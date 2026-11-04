package io.taskbeacon.core.selection;

import io.taskbeacon.core.model.TaskKey;

import java.time.Duration;
import java.time.Instant;

public record SelectionContext(Instant now, Duration completedDisplayTtl, TaskKey pinnedTask) {
    public SelectionContext {
        if (now == null) throw new IllegalArgumentException("now is required");
        if (completedDisplayTtl == null || completedDisplayTtl.isNegative()) {
            throw new IllegalArgumentException("completedDisplayTtl must not be negative");
        }
    }
}
