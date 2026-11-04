package io.taskbeacon.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Map;

public record SourceHealth(HealthLevel level, String message, Instant updatedAt,
                           Map<String, ProviderHealth> providers) {
    public SourceHealth {
        Objects.requireNonNull(level, "level");
        message = message == null ? "" : message;
        Objects.requireNonNull(updatedAt, "updatedAt");
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public static SourceHealth healthy(Instant now) {
        return new SourceHealth(HealthLevel.HEALTHY, "Ready", now, Map.of());
    }
}
