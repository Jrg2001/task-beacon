package io.taskbeacon.core.model;

public record TaskKey(String sourceId, String taskId) {
    public TaskKey {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId must not be blank");
    }
}
