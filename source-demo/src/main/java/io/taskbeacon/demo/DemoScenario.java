package io.taskbeacon.demo;

import io.taskbeacon.core.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DemoScenario {
    private DemoScenario() { }
    public record ScheduledEvent(long delayMillis, TaskEvent event) { }

    public static List<ScheduledEvent> events(Instant base) {
        TaskKey root = new TaskKey("demo", "root-1");
        TaskKey childA = new TaskKey("demo", "child-a");
        TaskKey childB = new TaskKey("demo", "child-b");
        TaskRelation rootRelation = TaskRelation.root(TaskScope.RUN, root);
        TaskRelation relationA = new TaskRelation(TaskScope.SUBTASK, root, root);
        TaskRelation relationB = new TaskRelation(TaskScope.SUBTASK, root, root);
        List<ScheduledEvent> events = new ArrayList<>();
        events.add(at(0, event(root, rootRelation, EventType.TASK_DISCOVERED, base, "Demo build", "Detected",
                ProgressImpact.OBSERVATION_ONLY, Map.of("sourceDisplayName", "Demo", "workspace", "taskbeacon"))));
        events.add(at(350, event(root, rootRelation, EventType.TASK_STARTED, base, "Demo build", "Starting",
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.THINKING))));
        events.add(at(900, event(childA, relationA, EventType.TASK_STARTED, base, "Analyze", "Thinking",
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.THINKING, "sourceDisplayName", "Demo"))));
        events.add(at(1250, event(root, rootRelation, EventType.ACTIVITY_CHANGED, base, "Demo build", "Running tests",
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.TESTING))));
        events.add(at(1700, event(childB, relationB, EventType.TASK_STARTED, base, "Edit files", "Editing files",
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.EDITING_FILES, "sourceDisplayName", "Demo"))));
        events.add(at(2200, event(root, rootRelation, EventType.ATTENTION_REQUIRED, base, "Demo build", "Approval required",
                ProgressImpact.OBSERVATION_ONLY, Map.of("attention", Attention.APPROVAL_REQUIRED))));
        events.add(at(3300, event(root, rootRelation, EventType.ATTENTION_CLEARED, base, "Demo build", "Continuing",
                ProgressImpact.MEANINGFUL, Map.of())));
        events.add(at(3800, event(root, rootRelation, EventType.CONDITION_CHANGED, base, "Demo build", "Possibly stalled",
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.STALLED))));
        events.add(at(4800, event(root, rootRelation, EventType.ACTIVITY_CHANGED, base, "Demo build", "Editing files",
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.EDITING_FILES))));
        events.add(at(5400, event(childA, relationA, EventType.TASK_SUCCEEDED, base, "Analyze", "Completed",
                ProgressImpact.MEANINGFUL, Map.of())));
        events.add(at(5700, event(childB, relationB, EventType.TASK_SUCCEEDED, base, "Edit files", "Completed",
                ProgressImpact.MEANINGFUL, Map.of())));
        events.add(at(6400, event(root, rootRelation, EventType.TASK_SUCCEEDED, base, "Demo build", "Completed",
                ProgressImpact.MEANINGFUL, Map.of())));
        return List.copyOf(events);
    }

    private static ScheduledEvent at(long delay, TaskEvent event) {
        TaskEvent timed = new TaskEvent(event.schemaVersion(), event.eventId(), event.taskKey(), event.relation(),
                event.type(), event.occurredAt().plusMillis(delay), event.observationMode(), event.evidenceQuality(),
                event.progressImpact(), event.title(), event.message(), event.data());
        return new ScheduledEvent(delay, timed);
    }

    private static TaskEvent event(TaskKey key, TaskRelation relation, EventType type, Instant base,
                                   String title, String message, ProgressImpact impact, Map<String, Object> data) {
        return TaskEvent.of(key, relation, type, base,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, impact, title, message, data);
    }
}
