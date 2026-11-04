package io.taskbeacon.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TaskState(
        TaskKey key, TaskRelation relation, String sourceDisplayName, String taskTitle, String workspace,
        Lifecycle lifecycle, Activity activity, String activityLabel, Attention attention, Condition condition,
        String message, TaskProgress progress, Instant discoveredAt, Instant startedAt,
        Instant lifecycleSince, Instant activitySince, Instant attentionSince, Instant conditionSince,
        Instant lastObservedAt, Instant lastMeaningfulProgressAt, Instant updatedAt, Instant finishedAt,
        EvidenceQuality currentEvidence, long eventCount, Map<String, Object> attributes) {
    public TaskState {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(relation, "relation");
        sourceDisplayName = sourceDisplayName == null ? key.sourceId() : sourceDisplayName;
        taskTitle = taskTitle == null ? "" : taskTitle;
        workspace = workspace == null ? "" : workspace;
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(activity, "activity");
        activityLabel = activityLabel == null ? "" : activityLabel;
        Objects.requireNonNull(attention, "attention");
        Objects.requireNonNull(condition, "condition");
        message = message == null ? "" : message;
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(discoveredAt, "discoveredAt");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        currentEvidence = currentEvidence == null ? EvidenceQuality.UNKNOWN : currentEvidence;
        if (eventCount < 1) throw new IllegalArgumentException("eventCount must be positive");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
