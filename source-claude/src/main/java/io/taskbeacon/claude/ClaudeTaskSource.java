package io.taskbeacon.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.api.*;
import io.taskbeacon.claude.interpret.ClaudeTaskInterpreter;
import io.taskbeacon.claude.provider.transcript.ClaudeTranscriptProvider;

import java.time.Clock;
import java.util.*;

public final class ClaudeTaskSource implements TaskSource {
    private final ClaudeTranscriptProvider provider;
    private final ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
    private final Clock clock;
    private volatile SourceHealth health;

    public ClaudeTaskSource(ClaudeSourceConfig config, Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.provider = new ClaudeTranscriptProvider(config, clock, new ObjectMapper());
        this.health = new SourceHealth(HealthLevel.DISCONNECTED, "Not started", clock.instant(), Map.of());
    }

    @Override public SourceDescriptor descriptor() {
        return new SourceDescriptor("claude", "Claude Code", "0.1", EnumSet.of(
                SourceCapability.REALTIME_EVENTS, SourceCapability.MULTI_TASK,
                SourceCapability.TASK_HIERARCHY, SourceCapability.COMPLETION_EVENTS,
                SourceCapability.STALL_EVIDENCE, SourceCapability.READ_ONLY));
    }

    @Override public void start(TaskEventSink sink) {
        provider.start(signal -> interpreter.interpret(signal).forEach(event -> {
            try { sink.publish(event); } catch (RuntimeException ignored) { }
        }));
        refreshHealth();
    }

    private void refreshHealth() {
        ProviderHealth providerHealth = provider.health();
        health = new SourceHealth(providerHealth.level(),
                providerHealth.level() == HealthLevel.HEALTHY ? "Monitoring Claude Code" : "Claude monitoring is limited",
                clock.instant(), Map.of("transcript-log", providerHealth));
    }

    @Override public SourceHealth health() {
        refreshHealth();
        return health;
    }

    @Override public void close() {
        provider.close();
        refreshHealth();
    }
}
