package io.taskbeacon.core.registry;

import io.taskbeacon.core.model.TaskState;

@FunctionalInterface
public interface TaskStateListener {
    void onStateChanged(TaskState state);
}
