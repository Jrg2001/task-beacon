package io.taskbeacon.claude.provider.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.api.*;
import io.taskbeacon.claude.ClaudeSourceConfig;
import io.taskbeacon.claude.parse.ClaudeTranscriptParser;
import io.taskbeacon.claude.signal.ClaudeRawSignal;
import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ClaudeTranscriptProvider implements AutoCloseable {
    private final TranscriptFileDiscovery discovery;
    private final AppendOnlyJsonlReader reader;
    private final ClaudeTranscriptParser parser;
    private final Clock clock;
    private final long intervalMillis;
    private final Duration quietThreshold;
    private final ScheduledExecutorService executor;
    private final Map<Path, String> sessionIds = new ConcurrentHashMap<>();
    private final Map<Path, String> agentIds = new ConcurrentHashMap<>();
    private final Map<Path, String> workspaces = new ConcurrentHashMap<>();
    private final Map<Path, Instant> lastSemanticAt = new ConcurrentHashMap<>();
    private final Set<Path> terminal = ConcurrentHashMap.newKeySet();
    private final Set<Path> quietEmitted = ConcurrentHashMap.newKeySet();
    private final Set<Path> initialized = ConcurrentHashMap.newKeySet();
    private final Map<Path, java.util.List<ClaudeRawSignal>> bootstrapSignals = new ConcurrentHashMap<>();
    private volatile ProviderHealth health;

    public ClaudeTranscriptProvider(ClaudeSourceConfig config, Clock clock, ObjectMapper mapper) {
        this.clock = Objects.requireNonNull(clock);
        this.discovery = new TranscriptFileDiscovery(config.projectsRoot(), config.staleSessionThreshold(), clock);
        this.reader = new AppendOnlyJsonlReader(config.maxReadBytes(), config.initialTailBytes(), clock);
        this.parser = new ClaudeTranscriptParser(mapper);
        this.intervalMillis = config.rescanInterval().toMillis();
        this.quietThreshold = config.quietThreshold();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "taskbeacon-claude-transcript");
            thread.setDaemon(true);
            return thread;
        });
        this.health = new ProviderHealth(HealthLevel.DISCONNECTED, "Not started", clock.instant());
    }

    public void start(Consumer<ClaudeRawSignal> sink) {
        executor.scheduleWithFixedDelay(() -> scanOnce(sink), 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    void scanOnce(Consumer<ClaudeRawSignal> sink) {
        try {
            for (Path path : discovery.discover()) {
                String hint = sessionIds.getOrDefault(path, sessionHint(path));
                java.util.List<ClaudeRawSignal> bootstrap = initialized.contains(path) ? null
                        : bootstrapSignals.computeIfAbsent(path, ignored -> new java.util.ArrayList<>());
                java.util.List<String> lines = reader.readAvailable(path);
                if (!lines.isEmpty() && initialized.contains(path)) {
                    // Any appended line (api-request, attachment, ...) proves the session is alive.
                    lastSemanticAt.put(path, clock.instant());
                    quietEmitted.remove(path);
                }
                for (String line : lines) {
                    for (ClaudeRawSignal signal : parser.parse(line, hint, clock.instant())) {
                        observe(path, signal);
                        if (bootstrap == null) sink.accept(signal); else bootstrap.add(signal);
                    }
                    hint = sessionIds.getOrDefault(path, hint);
                }
                FileCursor cursor = reader.cursor(path);
                if (bootstrap != null && cursor != null && cursor.offset() >= Files.size(path)) {
                    initialized.add(path);
                    bootstrapSignals.remove(path);
                    bootstrap.forEach(sink);
                }
                if (!initialized.contains(path)) continue;
                emitQuietIfNeeded(path, sink);
            }
            health = new ProviderHealth(HealthLevel.HEALTHY, "Watching Claude Code transcripts", clock.instant());
        } catch (IOException | RuntimeException error) {
            health = new ProviderHealth(HealthLevel.DEGRADED, "Transcript scan failed", clock.instant());
        }
    }

    private void observe(Path path, ClaudeRawSignal signal) {
        if (signal.sessionId() != null && !signal.sessionId().isBlank()) sessionIds.put(path, signal.sessionId());
        if (signal.agentId() != null && !signal.agentId().isBlank()) agentIds.put(path, signal.agentId());
        if (signal.workspace() != null && !signal.workspace().isBlank()) workspaces.put(path, signal.workspace());
        lastSemanticAt.put(path, signal.observedAt());
        quietEmitted.remove(path);
        if (signal.signalType().equals("user_prompt")) terminal.remove(path);
        if (signal.signalType().equals("turn_completed")
                || signal.signalType().equals("turn_cancelled")) terminal.add(path);
    }

    private void emitQuietIfNeeded(Path path, Consumer<ClaudeRawSignal> sink) throws IOException {
        FileCursor cursor = reader.cursor(path);
        Instant last = lastSemanticAt.get(path);
        String session = sessionIds.get(path);
        if (cursor == null || cursor.offset() < Files.size(path) || last == null || session == null
                || terminal.contains(path) || quietEmitted.contains(path)) return;
        Instant now = clock.instant();
        if (Duration.between(last, now).compareTo(quietThreshold) < 0) return;
        quietEmitted.add(path);
        sink.accept(new ClaudeRawSignal("session_quiet", now, session, agentIds.get(path),
                workspaces.getOrDefault(path, ""), "", ObservationMode.FILE_WATCH, EvidenceQuality.HEURISTIC));
    }

    private static String sessionHint(Path path) {
        String name = path.getFileName().toString();
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }

    public ProviderHealth health() { return health; }

    @Override public void close() {
        executor.shutdownNow();
        health = new ProviderHealth(HealthLevel.DISCONNECTED, "Stopped", clock.instant());
    }
}
