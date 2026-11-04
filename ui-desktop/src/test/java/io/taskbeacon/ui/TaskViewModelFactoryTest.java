package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskViewModelFactoryTest {
    private final Instant start = Instant.parse("2026-09-10T00:00:00Z");
    private final Clock clock = Clock.fixed(start.plusSeconds(94), ZoneOffset.UTC);
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();

    @Test void formatsElapsedAttentionAndConditionWithoutJavaFxWindow() {
        TaskState running = state(EventType.TASK_STARTED, Map.of("activity", Activity.TESTING));
        TaskCardViewModel normal = new TaskViewModelFactory(clock).create(running, 2);
        assertEquals("Running tests", normal.status());
        assertEquals("01:34", normal.elapsed());
        assertEquals(2, normal.activeTaskCount());

        TaskState approval = reducer.reduce(running, event(EventType.ATTENTION_REQUIRED, start.plusSeconds(1),
                ProgressImpact.OBSERVATION_ONLY, Map.of("attention", Attention.APPROVAL_REQUIRED)));
        assertEquals("Approval required", new TaskViewModelFactory(clock).create(approval, 1).status());

        TaskState stalled = reducer.reduce(running, event(EventType.CONDITION_CHANGED, start.plusSeconds(2),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.STALLED)));
        assertEquals("Possibly stalled", new TaskViewModelFactory(clock).create(stalled, 1).status());

        TaskState quiet = reducer.reduce(running, event(EventType.CONDITION_CHANGED, start.plusSeconds(15),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.QUIET)));
        TaskCardViewModel quietModel = new TaskViewModelFactory(clock).create(quiet, 1);
        assertEquals("No recent activity", quietModel.status());
        assertEquals("01:34", quietModel.elapsed());
    }

    @Test void eachRunningTaskKeepsItsOwnCumulativeTimerAcrossActivityChanges() {
        TaskState first = state(EventType.TASK_STARTED, Map.of("activity", Activity.THINKING));
        first = reducer.reduce(first, event(EventType.ACTIVITY_CHANGED, start.plusSeconds(40),
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.CALLING_TOOL)));
        TaskCardViewModel firstModel = new TaskViewModelFactory(clock).create(first, 2);
        assertEquals("Using tool", firstModel.status());
        assertEquals("01:34", firstModel.elapsed());

        TaskKey secondKey = new TaskKey("demo", "two");
        TaskEvent secondStart = TaskEvent.of(secondKey, TaskRelation.root(TaskScope.RUN, secondKey),
                EventType.TASK_STARTED, start.plusSeconds(70), ObservationMode.EXTERNAL,
                EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL, "Review", "",
                Map.of("activity", Activity.THINKING));
        TaskCardViewModel secondModel = new TaskViewModelFactory(clock)
                .create(reducer.reduce(null, secondStart), 2);
        assertTrue(secondModel.status().startsWith("Thinking"));
        assertEquals("00:24", secondModel.elapsed());
    }

    @Test void formatsActivityWithDetails() {
        TaskViewModelFactory factory = new TaskViewModelFactory(clock);

        TaskState cmd = state(EventType.TASK_STARTED, Map.of("activity", Activity.EXECUTING_COMMAND, "activityLabel", "git status"));
        assertEquals("Running: git status", factory.create(cmd, 1).status());

        TaskState tool = state(EventType.TASK_STARTED, Map.of("activity", Activity.CALLING_TOOL, "activityLabel", "read_file"));
        assertEquals("Tool: read_file", factory.create(tool, 1).status());

        TaskState edit = state(EventType.TASK_STARTED, Map.of("activity", Activity.EDITING_FILES, "activityLabel", "C:\\Users\\dev\\App.java"));
        assertEquals("Editing: App.java", factory.create(edit, 1).status());

        TaskState test = state(EventType.TASK_STARTED, Map.of("activity", Activity.TESTING, "activityLabel", "./gradlew test"));
        assertEquals("Testing: ./gradlew test", factory.create(test, 1).status());
    }

    @Test void prefersWorkspaceFolderNameOverDefaultTitle() {
        TaskViewModelFactory factory = new TaskViewModelFactory(clock);

        TaskEvent eventWithFallback = TaskEvent.of(new TaskKey("codex", "s1"), TaskRelation.root(TaskScope.RUN, new TaskKey("codex", "s1")),
                EventType.TASK_STARTED, start, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Codex task", "", Map.of("workspace", "D:\\workspace\\project-x", "sourceDisplayName", "Codex"));
        TaskState stateWithFallback = reducer.reduce(null, eventWithFallback);
        assertEquals("project-x", factory.create(stateWithFallback, 1).title());

        TaskEvent explicitEvent = TaskEvent.of(new TaskKey("codex", "s2"), TaskRelation.root(TaskScope.RUN, new TaskKey("codex", "s2")),
                EventType.TASK_STARTED, start, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Custom Feature", "", Map.of("workspace", "D:\\workspace\\project-x", "sourceDisplayName", "Codex"));
        TaskState stateExplicit = reducer.reduce(null, explicitEvent);
        assertEquals("Custom Feature", factory.create(stateExplicit, 1).title());

        TaskEvent duplicateEvent = TaskEvent.of(new TaskKey("codex", "s3"), TaskRelation.root(TaskScope.RUN, new TaskKey("codex", "s3")),
                EventType.TASK_STARTED, start, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Codex", "", Map.of("workspace", "D:\\workspace\\project-x", "sourceDisplayName", "Codex"));
        TaskState stateDuplicate = reducer.reduce(null, duplicateEvent);
        assertEquals("project-x", factory.create(stateDuplicate, 1).title());

        TaskEvent noWorkspaceEvent = TaskEvent.of(new TaskKey("codex", "s4"), TaskRelation.root(TaskScope.RUN, new TaskKey("codex", "s4")),
                EventType.TASK_STARTED, start, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Codex", "", Map.of("sourceDisplayName", "Codex"));
        TaskState stateNoWorkspace = reducer.reduce(null, noWorkspaceEvent);
        assertEquals("Active task", factory.create(stateNoWorkspace, 1).title());
    }

    private TaskState state(EventType type, Map<String, Object> data) {
        return reducer.reduce(null, event(type, start, ProgressImpact.MEANINGFUL, data));
    }

    private TaskEvent event(EventType type, Instant at, ProgressImpact impact, Map<String, Object> data) {
        TaskKey key = new TaskKey("demo", "one");
        return TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key), type, at,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, impact, "Build", "", data);
    }
}
