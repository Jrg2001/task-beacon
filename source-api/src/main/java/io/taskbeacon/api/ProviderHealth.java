package io.taskbeacon.api;

import java.time.Instant;
import java.util.Objects;

public record ProviderHealth(HealthLevel level, String message, Instant updatedAt) {
    public ProviderHealth {
        Objects.requireNonNull(level, "level");
        message = message == null ? "" : message;
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
