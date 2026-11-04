package io.taskbeacon.demo;

import io.taskbeacon.api.*;
import io.taskbeacon.core.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DemoTaskSource implements TaskSource {
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile SourceHealth health;

    public DemoTaskSource(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "taskbeacon-demo-source");
            thread.setDaemon(true);
            return thread;
        });
        this.health = SourceHealth.healthy(clock.instant());
    }

    @Override public SourceDescriptor descriptor() {
        return new SourceDescriptor("demo", "Demo", "1.0", EnumSet.of(
                SourceCapability.REALTIME_EVENTS, SourceCapability.MULTI_TASK,
                SourceCapability.TASK_HIERARCHY, SourceCapability.ATTENTION_EVENTS,
                SourceCapability.COMPLETION_EVENTS, SourceCapability.STALL_EVIDENCE,
                SourceCapability.READ_ONLY));
    }

    @Override public void start(TaskEventSink sink) {
        if (!started.compareAndSet(false, true)) return;
        Instant base = clock.instant();
        for (DemoScenario.ScheduledEvent item : DemoScenario.events(base)) {
            executor.schedule(() -> {
                try { sink.publish(item.event()); }
                catch (RuntimeException error) {
                    health = new SourceHealth(HealthLevel.DEGRADED, "Event sink rejected demo event",
                            clock.instant(), Map.of());
                }
            }, item.delayMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override public SourceHealth health() { return health; }

    @Override public void close() {
        executor.shutdownNow();
        health = new SourceHealth(HealthLevel.DISCONNECTED, "Stopped", clock.instant(), Map.of());
    }
}
