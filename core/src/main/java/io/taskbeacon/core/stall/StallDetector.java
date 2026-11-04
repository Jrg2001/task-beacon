package io.taskbeacon.core.stall;

import io.taskbeacon.core.model.TaskEvent;
import io.taskbeacon.core.model.TaskState;

import java.time.Instant;
import java.util.Optional;

public interface StallDetector {
    Optional<TaskEvent> evaluate(TaskState state, Instant now);
}
