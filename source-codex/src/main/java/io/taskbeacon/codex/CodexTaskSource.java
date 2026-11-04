package io.taskbeacon.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.api.*;
import io.taskbeacon.codex.interpret.CodexTaskInterpreter;
import io.taskbeacon.codex.provider.CodexSignalProvider;
import io.taskbeacon.codex.provider.sessionlog.SessionLogProvider;
import io.taskbeacon.codex.signal.CodexRawSignal;
import io.taskbeacon.codex.signal.CodexSignalFusion;
import io.taskbeacon.core.model.TaskEvent;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class CodexTaskSource implements TaskSource {
    private final List<CodexSignalProvider> providers;
    private final CodexSignalFusion fusion;
    private final CodexTaskInterpreter interpreter;
    private final Clock clock;
    private final Queue<CodexRawSignal> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService fusionExecutor;
    private volatile SourceHealth health;

    public CodexTaskSource(CodexSourceConfig config, Clock clock) {
        this(List.of(new SessionLogProvider(config, clock, new ObjectMapper())), clock);
    }

    CodexTaskSource(List<CodexSignalProvider> providers, Clock clock) {
        this.providers = List.copyOf(providers);
        this.clock = Objects.requireNonNull(clock);
        this.fusion = new CodexSignalFusion(Duration.ofMillis(150));
        this.interpreter = new CodexTaskInterpreter();
        this.fusionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "taskbeacon-codex-fusion");
            thread.setDaemon(true);
            return thread;
        });
        this.health = new SourceHealth(HealthLevel.DISCONNECTED, "Not started", clock.instant(), Map.of());
    }

    @Override public SourceDescriptor descriptor() {
        return new SourceDescriptor("codex", "Codex", "0.2", EnumSet.of(
                SourceCapability.REALTIME_EVENTS, SourceCapability.MULTI_TASK,
                SourceCapability.TASK_HIERARCHY, SourceCapability.ATTENTION_EVENTS,
                SourceCapability.COMPLETION_EVENTS, SourceCapability.STALL_EVIDENCE,
                SourceCapability.READ_ONLY));
    }

    @Override public void start(TaskEventSink sink) {
        for (CodexSignalProvider provider : providers) {
            try { provider.start(pending::add); }
            catch (RuntimeException ignored) { }
        }
        fusionExecutor.scheduleWithFixedDelay(() -> flush(sink), 150, 150, TimeUnit.MILLISECONDS);
        refreshHealth();
    }

    private void flush(TaskEventSink sink) {
        List<CodexRawSignal> batch = new ArrayList<>();
        CodexRawSignal signal;
        while ((signal = pending.poll()) != null && batch.size() < 10_000) batch.add(signal);
        for (CodexRawSignal fused : fusion.fuse(batch)) {
            for (TaskEvent event : interpreter.interpret(fused)) {
                try { sink.publish(event); } catch (RuntimeException ignored) { }
            }
        }
        refreshHealth();
    }

    private void refreshHealth() {
        Map<String, ProviderHealth> providerHealth = new LinkedHashMap<>();
        boolean healthy = false;
        for (CodexSignalProvider provider : providers) {
            ProviderHealth value = provider.health();
            providerHealth.put(provider.id(), value);
            healthy |= value.level() == HealthLevel.HEALTHY;
        }
        HealthLevel level = healthy ? HealthLevel.HEALTHY
                : providerHealth.values().stream().anyMatch(value -> value.level() == HealthLevel.DEGRADED)
                ? HealthLevel.DEGRADED : HealthLevel.DISCONNECTED;
        health = new SourceHealth(level, healthy ? "Monitoring Codex" : "Codex monitoring is limited",
                clock.instant(), providerHealth);
    }

    @Override public SourceHealth health() { return health; }

    @Override public void close() {
        fusionExecutor.shutdownNow();
        for (CodexSignalProvider provider : providers) {
            try { provider.close(); } catch (RuntimeException ignored) { }
        }
        health = new SourceHealth(HealthLevel.DISCONNECTED, "Stopped", clock.instant(), health.providers());
    }
}
