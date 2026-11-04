package io.taskbeacon.codex.provider.sessionlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.taskbeacon.api.*;
import io.taskbeacon.codex.CodexSourceConfig;
import io.taskbeacon.codex.provider.CodexSignalProvider;
import io.taskbeacon.codex.signal.CodexRawSignal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SessionLogProvider implements CodexSignalProvider {
    private final SessionFileDiscovery discovery;
    private final AppendOnlyJsonlReader reader;
    private final SessionLogParser parser;
    private final Clock clock;
    private final long intervalMillis;
    private final Duration quietThreshold;
    private final ScheduledExecutorService executor;
    private volatile ProviderHealth health;
    private final Map<Path, String> sessionIds = new ConcurrentHashMap<>();
    private final Map<Path, Instant> lastSemanticAt = new ConcurrentHashMap<>();
    private final Map<Path, String> workspaces = new ConcurrentHashMap<>();
    private final Set<Path> terminal = ConcurrentHashMap.newKeySet();
    private final Set<Path> openTurn = ConcurrentHashMap.newKeySet();
    private final Set<Path> quietEmitted = ConcurrentHashMap.newKeySet();
    private final Set<Path> initialized = ConcurrentHashMap.newKeySet();
    private final Map<Path, java.util.List<CodexRawSignal>> bootstrapSignals = new ConcurrentHashMap<>();

    public SessionLogProvider(CodexSourceConfig config, Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        this.discovery = new SessionFileDiscovery(config.sessionsRoot(), config.staleSessionThreshold(), clock);
        this.reader = new AppendOnlyJsonlReader(config.maxReadBytes(), config.initialTailBytes(), clock);
        this.parser = new SessionLogParser(mapper);
        this.intervalMillis = config.rescanInterval().toMillis();
        this.quietThreshold = config.quietThreshold();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "taskbeacon-codex-sessionlog");
            thread.setDaemon(true);
            return thread;
        });
        this.health = new ProviderHealth(HealthLevel.DISCONNECTED, "Not started", clock.instant());
    }

    @Override public String id() { return "session-log"; }
    @Override public ProviderHealth health() { return health; }

    @Override public void start(Consumer<CodexRawSignal> sink) {
        executor.scheduleWithFixedDelay(() -> scanOnce(sink), 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private static final java.util.regex.Pattern TRAILING_UUID =
            java.util.regex.Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    void scanOnce(Consumer<CodexRawSignal> sink) {
        try {
            for (Path path : discovery.discover()) {
                ensureSessionMetaKnown(path);
                String hint = sessionIds.getOrDefault(path, sessionHint(path));
                java.util.List<CodexRawSignal> bootstrap = initialized.contains(path) ? null
                        : bootstrapSignals.computeIfAbsent(path, ignored -> new java.util.ArrayList<>());
                for (String line : reader.readAvailable(path)) {
                    parser.parse(line, hint, clock.instant()).ifPresent(signal -> {
                        observe(path, signal, bootstrap == null);
                        String ws = signal.workspace();
                        if (ws == null || ws.isBlank()) {
                            ws = workspaces.get(path);
                            if (ws != null && !ws.isBlank()) {
                                signal = signal.withWorkspace(ws);
                            }
                        }
                        if (bootstrap == null) sink.accept(signal); else bootstrap.add(signal);
                    });
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
            health = new ProviderHealth(HealthLevel.HEALTHY, "Watching Codex session logs", clock.instant());
        } catch (IOException | RuntimeException error) {
            health = new ProviderHealth(HealthLevel.DEGRADED, "Session log scan failed", clock.instant());
        }
    }

    private void ensureSessionMetaKnown(Path path) {
        if (sessionIds.containsKey(path) && workspaces.containsKey(path)) return;
        try (var br = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            String firstLine = br.readLine();
            if (firstLine != null && !firstLine.isBlank()) {
                parser.parse(firstLine, sessionHint(path), clock.instant()).ifPresent(metaSignal -> {
                    observe(path, metaSignal, false);
                    if (metaSignal.workspace() != null && !metaSignal.workspace().isBlank()) {
                        workspaces.put(path, metaSignal.workspace());
                    }
                });
            }
        } catch (Exception ignored) { }
    }

    private void observe(Path path, CodexRawSignal signal, boolean liveAppend) {
        if (signal.sessionId() != null && !signal.sessionId().isBlank()) {
            sessionIds.put(path, signal.sessionId());
        }
        if (signal.workspace() != null && !signal.workspace().isBlank()) workspaces.put(path, signal.workspace());
        String type = signal.signalType();
        if (!type.equals("heartbeat") && !type.equals("session_discovered")) {
            // Historical timestamps are useful during bootstrap, but live records can arrive late
            // or carry a skewed producer clock. Quietness starts when TaskBeacon observes the append.
            lastSemanticAt.put(path, liveAppend ? clock.instant() : signal.observedAt());
            quietEmitted.remove(path);
        }
        if (type.equals("turn_started") || type.equals("command_started") || type.equals("tool_started")
                || type.equals("file_changed") || type.equals("reasoning") || type.equals("message")) {
            terminal.remove(path);
        }
        if (type.equals("turn_started")) openTurn.add(path);
        if (type.equals("turn_completed") || type.equals("turn_cancelled") || type.equals("turn_failed")) {
            terminal.add(path);
            openTurn.remove(path);
        }
    }

    private void emitQuietIfNeeded(Path path, Consumer<CodexRawSignal> sink) throws IOException {
        FileCursor cursor = reader.cursor(path);
        Instant last = lastSemanticAt.get(path);
        String session = sessionIds.get(path);
        if (cursor == null || cursor.offset() < Files.size(path) || last == null || session == null
                || terminal.contains(path) || openTurn.contains(path) || quietEmitted.contains(path)) return;
        Instant now = clock.instant();
        if (Duration.between(last, now).compareTo(quietThreshold) < 0) return;
        quietEmitted.add(path);
        sink.accept(new CodexRawSignal("session-log", "session_quiet", now, session,
                null, null, null, workspaces.getOrDefault(path, ""),
                io.taskbeacon.core.model.ObservationMode.FILE_WATCH,
                io.taskbeacon.core.model.EvidenceQuality.HEURISTIC,
                JsonNodeFactory.instance.objectNode()));
    }

    private static String sessionHint(Path path) {
        String name = path.getFileName().toString();
        var matcher = TRAILING_UUID.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }

    @Override public void close() {
        executor.shutdownNow();
        health = new ProviderHealth(HealthLevel.DISCONNECTED, "Stopped", clock.instant());
    }
}
