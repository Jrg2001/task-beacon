package io.taskbeacon.core.model;

import java.util.Objects;

public record TaskRelation(TaskScope scope, TaskKey rootTaskKey, TaskKey parentTaskKey) {
    public TaskRelation {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(rootTaskKey, "rootTaskKey");
    }

    public static TaskRelation root(TaskScope scope, TaskKey key) {
        return new TaskRelation(scope, key, null);
    }
}
