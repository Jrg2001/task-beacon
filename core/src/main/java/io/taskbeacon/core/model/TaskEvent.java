package io.taskbeacon.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TaskEvent(int schemaVersion, UUID eventId, TaskKey taskKey, TaskRelation relation,
                        EventType type, Instant occurredAt, ObservationMode observationMode,
                        EvidenceQuality evidenceQuality, ProgressImpact progressImpact,
                        String title, String message, Map<String, Object> data) {
    public TaskEvent {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(taskKey, "taskKey");
        relation = relation == null ? TaskRelation.root(TaskScope.UNKNOWN, taskKey) : relation;
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        observationMode = observationMode == null ? ObservationMode.UNKNOWN : observationMode;
        evidenceQuality = evidenceQuality == null ? EvidenceQuality.UNKNOWN : evidenceQuality;
        progressImpact = progressImpact == null ? ProgressImpact.UNKNOWN : progressImpact;
        title = title == null ? "" : title;
        message = message == null ? "" : message;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static TaskEvent of(TaskKey key, TaskRelation relation, EventType type, Instant at,
                               ObservationMode mode, EvidenceQuality evidence, ProgressImpact impact,
                               String title, String message, Map<String, Object> data) {
        return new TaskEvent(1, UUID.randomUUID(), key, relation, type, at, mode, evidence,
                impact, title, message, data);
    }
}
