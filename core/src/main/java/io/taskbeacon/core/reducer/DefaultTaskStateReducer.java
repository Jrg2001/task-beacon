package io.taskbeacon.core.reducer;

import io.taskbeacon.core.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultTaskStateReducer implements TaskStateReducer {
    private static final Logger log = LoggerFactory.getLogger(DefaultTaskStateReducer.class);
    @Override
    public TaskState reduce(TaskState previous, TaskEvent event) {
        if (previous != null && !previous.key().equals(event.taskKey())) {
            throw new IllegalArgumentException("event key does not match previous state");
        }
        if (previous == null) return initial(event);

        Lifecycle lifecycle = previous.lifecycle();
        Activity activity = previous.activity();
        String activityLabel = previous.activityLabel();
        Attention attention = previous.attention();
        Condition condition = previous.condition();
        TaskProgress progress = previous.progress();
        Instant startedAt = previous.startedAt();
        Instant lifecycleSince = previous.lifecycleSince();
        Instant activitySince = previous.activitySince();
        Instant attentionSince = previous.attentionSince();
        Instant conditionSince = previous.conditionSince();
        Instant finishedAt = previous.finishedAt();
        boolean terminal = lifecycle.isTerminal();

        switch (event.type()) {
            case TASK_STARTED -> {
                if (!terminal) {
                    lifecycle = Lifecycle.RUNNING;
                    lifecycleSince = event.occurredAt();
                    startedAt = startedAt == null ? event.occurredAt() : startedAt;
                    activity = enumValue(event.data(), "activity", Activity.class, Activity.STARTING);
                    activityLabel = text(event.data(), "activityLabel", activityLabel);
                    activitySince = event.occurredAt();
                    log.info("RUNNING via TASK_STARTED: key={} ws={} startedAt={} prevLifecycle={}",
                            previous.key(), previous.workspace(), startedAt, previous.lifecycle());
                }
            }
            case ACTIVITY_CHANGED -> {
                if (!terminal) {
                    activity = enumValue(event.data(), "activity", Activity.class, Activity.OTHER);
                    if (activity != Activity.IDLE && lifecycle == Lifecycle.DISCOVERED) {
                        lifecycle = Lifecycle.RUNNING;
                        lifecycleSince = event.occurredAt();
                        startedAt = startedAt == null ? event.occurredAt() : startedAt;
                        log.info("RUNNING via ACTIVITY_CHANGED: key={} ws={} activity={} startedAt={}",
                                previous.key(), previous.workspace(), activity, startedAt);
                    }
                    activityLabel = text(event.data(), "activityLabel", event.message());
                    activitySince = event.occurredAt();
                }
            }
            case ATTENTION_REQUIRED -> {
                if (!terminal) {
                    attention = enumValue(event.data(), "attention", Attention.class, Attention.WARNING);
                    attentionSince = event.occurredAt();
                }
            }
            case ATTENTION_CLEARED -> {
                if (!terminal) {
                    attention = Attention.NONE;
                    attentionSince = event.occurredAt();
                }
            }
            case CONDITION_CHANGED -> {
                if (!terminal) {
                    condition = enumValue(event.data(), "condition", Condition.class, Condition.UNKNOWN);
                    conditionSince = event.occurredAt();
                }
            }
            case PROGRESS_UPDATED -> {
                if (!terminal && event.data().get("progress") instanceof TaskProgress value) progress = value;
            }
            case TASK_SUCCEEDED -> {
                if (!terminal) {
                    lifecycle = Lifecycle.SUCCEEDED; activity = Activity.IDLE; attention = Attention.NONE;
                    condition = Condition.NORMAL; lifecycleSince = event.occurredAt();
                    activitySince = event.occurredAt(); attentionSince = event.occurredAt();
                    conditionSince = event.occurredAt(); finishedAt = event.occurredAt();
                }
            }
            case TASK_FAILED -> {
                if (!terminal) {
                    lifecycle = Lifecycle.FAILED; activity = Activity.IDLE; attention = Attention.ERROR;
                    lifecycleSince = event.occurredAt(); activitySince = event.occurredAt();
                    attentionSince = event.occurredAt(); finishedAt = event.occurredAt();
                }
            }
            case TASK_CANCELLED -> {
                if (!terminal) {
                    lifecycle = Lifecycle.CANCELLED; activity = Activity.IDLE;
                    attention = Attention.NONE; condition = Condition.NORMAL;
                    lifecycleSince = event.occurredAt(); activitySince = event.occurredAt();
                    attentionSince = event.occurredAt(); conditionSince = event.occurredAt();
                    finishedAt = event.occurredAt();
                }
            }
            default -> { }
        }

        if (!lifecycle.isTerminal() && event.progressImpact() == ProgressImpact.MEANINGFUL
                && (condition == Condition.STALLED || condition == Condition.QUIET)
                && event.type() != EventType.CONDITION_CHANGED) {
            condition = Condition.NORMAL;
            conditionSince = event.occurredAt();
        }

        Map<String, Object> attributes = new HashMap<>(previous.attributes());
        if (event.data().get("attributes") instanceof Map<?, ?> additions) {
            additions.forEach((key, value) -> { if (key instanceof String name && value != null) attributes.put(name, value); });
        }
        Instant meaningfulAt = event.progressImpact() == ProgressImpact.MEANINGFUL
                ? event.occurredAt() : previous.lastMeaningfulProgressAt();
        return new TaskState(previous.key(), previous.relation(),
                text(event.data(), "sourceDisplayName", previous.sourceDisplayName()),
                event.title().isBlank() ? previous.taskTitle() : event.title(),
                text(event.data(), "workspace", previous.workspace()), lifecycle, activity, activityLabel,
                attention, condition, event.message().isBlank() ? previous.message() : event.message(), progress,
                previous.discoveredAt(), startedAt, lifecycleSince, activitySince, attentionSince, conditionSince,
                event.occurredAt(), meaningfulAt, event.occurredAt(), finishedAt,
                evidenceFor(previous, event), previous.eventCount() + 1, attributes);
    }

    private TaskState initial(TaskEvent event) {
        Activity activity = event.type() == EventType.TASK_STARTED
                ? enumValue(event.data(), "activity", Activity.class, Activity.STARTING)
                : event.type() == EventType.ACTIVITY_CHANGED
                ? enumValue(event.data(), "activity", Activity.class, Activity.OTHER)
                : Activity.IDLE;
        Lifecycle lifecycle = switch (event.type()) {
            case TASK_STARTED -> Lifecycle.RUNNING;
            case ACTIVITY_CHANGED -> activity != Activity.IDLE ? Lifecycle.RUNNING : Lifecycle.DISCOVERED;
            case TASK_SUCCEEDED -> Lifecycle.SUCCEEDED;
            case TASK_FAILED -> Lifecycle.FAILED;
            case TASK_CANCELLED -> Lifecycle.CANCELLED;
            default -> Lifecycle.DISCOVERED;
        };
        Attention attention = event.type() == EventType.TASK_FAILED ? Attention.ERROR
                : event.type() == EventType.ATTENTION_REQUIRED
                ? enumValue(event.data(), "attention", Attention.class, Attention.WARNING) : Attention.NONE;
        Condition condition = event.type() == EventType.CONDITION_CHANGED
                ? enumValue(event.data(), "condition", Condition.class, Condition.UNKNOWN) : Condition.NORMAL;
        Instant started = lifecycle == Lifecycle.RUNNING ? event.occurredAt() : null;
        Instant finished = lifecycle.isTerminal() ? event.occurredAt() : null;
        TaskProgress progress = event.type() == EventType.PROGRESS_UPDATED
                && event.data().get("progress") instanceof TaskProgress value
                ? value : TaskProgress.indeterminate();
        Map<String, Object> attributes = new HashMap<>();
        if (event.data().get("attributes") instanceof Map<?, ?> additions) {
            additions.forEach((key, value) -> { if (key instanceof String name && value != null) attributes.put(name, value); });
        }
        TaskState base = new TaskState(event.taskKey(), event.relation(),
                text(event.data(), "sourceDisplayName", event.taskKey().sourceId()), event.title(),
                text(event.data(), "workspace", ""), lifecycle, activity,
                text(event.data(), "activityLabel", ""), attention, condition, event.message(),
                progress, event.occurredAt(), started, event.occurredAt(), event.occurredAt(),
                event.occurredAt(), event.occurredAt(), event.occurredAt(),
                event.progressImpact() == ProgressImpact.MEANINGFUL ? event.occurredAt() : null,
                event.occurredAt(), finished, event.evidenceQuality(), 1, attributes);
        return base;
    }

    private static EvidenceQuality evidenceFor(TaskState previous, TaskEvent event) {
        if (event.type() == EventType.TASK_HEARTBEAT) return previous.currentEvidence();
        if (previous.lifecycle().isTerminal()
                && (event.type() == EventType.ACTIVITY_CHANGED || event.type() == EventType.TASK_HEARTBEAT)) {
            return previous.currentEvidence();
        }
        return event.evidenceQuality();
    }

    private static String text(Map<String, Object> data, String key, String fallback) {
        Object value = data.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> data, String key,
                                                    Class<E> type, E fallback) {
        Object value = data.get(key);
        if (type.isInstance(value)) return type.cast(value);
        if (value instanceof String text) {
            try { return Enum.valueOf(type, text); } catch (IllegalArgumentException ignored) { }
        }
        return fallback;
    }
}
