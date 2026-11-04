package io.taskbeacon.ui;

import io.taskbeacon.core.model.TaskKey;
import io.taskbeacon.core.model.TaskState;

import java.util.List;

/** Keeps active tasks in stable visual positions until a task leaves the active set. */
final class StableTaskSlots {
    record Selection(TaskState primary, TaskState secondary) { }

    private TaskKey primaryKey;
    private TaskKey secondaryKey;

    Selection select(List<TaskState> activeTasks) {
        TaskState primary = find(activeTasks, primaryKey);
        if (primary == null) {
            primary = find(activeTasks, secondaryKey);
            if (primary == null && !activeTasks.isEmpty()) primary = activeTasks.getFirst();
        }
        primaryKey = primary == null ? null : primary.key();

        TaskState secondary = find(activeTasks, secondaryKey);
        if (secondary != null && secondary.key().equals(primaryKey)) secondary = null;
        if (secondary == null) {
            secondary = activeTasks.stream()
                    .filter(candidate -> !candidate.key().equals(primaryKey))
                    .findFirst().orElse(null);
        }
        secondaryKey = secondary == null ? null : secondary.key();

        if (primary == null) {
            primaryKey = null;
            secondaryKey = null;
        }
        return new Selection(primary, secondary);
    }

    private static TaskState find(List<TaskState> tasks, TaskKey key) {
        if (key == null) return null;
        return tasks.stream().filter(task -> task.key().equals(key)).findFirst().orElse(null);
    }
}
