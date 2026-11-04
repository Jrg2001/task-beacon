package io.taskbeacon.core.registry;

import io.taskbeacon.core.model.TaskEvent;
import io.taskbeacon.core.model.TaskKey;
import io.taskbeacon.core.model.TaskState;

import java.util.List;
import java.util.Optional;

public interface TaskRegistry {
    void accept(TaskEvent event);
    Optional<TaskState> get(TaskKey key);
    List<TaskState> activeTasks();
    List<TaskState> recentTasks();
    List<TaskState> childrenOf(TaskKey parent);
    Subscription subscribe(TaskStateListener listener);
}
