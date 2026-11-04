package io.taskbeacon.core.reducer;

import io.taskbeacon.core.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaskStateReducerTest {
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();
    private final TaskKey key = new TaskKey("test", "one");
    private final TaskRelation relation = TaskRelation.root(TaskScope.RUN, key);
    private final Instant start = Instant.parse("2026-09-10T00:00:00Z");

    @Test void tracksLifecycleAttentionConditionAndMeaningfulTimeIndependently() {
        TaskState state = reducer.reduce(null, event(EventType.TASK_STARTED, start,
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.THINKING)));
        assertEquals(Lifecycle.RUNNING, state.lifecycle());
        assertEquals(start, state.lastMeaningfulProgressAt());

        state = reducer.reduce(state, event(EventType.ATTENTION_REQUIRED, start.plusSeconds(2),
                ProgressImpact.OBSERVATION_ONLY, Map.of("attention", Attention.APPROVAL_REQUIRED)));
        assertEquals(Lifecycle.RUNNING, state.lifecycle());
        assertEquals(Attention.APPROVAL_REQUIRED, state.attention());
        assertEquals(start, state.lastMeaningfulProgressAt());
        assertEquals(start.plusSeconds(2), state.lastObservedAt());

        state = reducer.reduce(state, event(EventType.CONDITION_CHANGED, start.plusSeconds(3),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.STALLED)));
        assertEquals(Condition.STALLED, state.condition());
        assertEquals(Lifecycle.RUNNING, state.lifecycle());
    }

    @Test void terminalStateCannotBeOverwrittenAndMeaningfulProgressRecoversStall() {
        TaskState state = reducer.reduce(null, event(EventType.TASK_STARTED, start,
                ProgressImpact.MEANINGFUL, Map.of()));
        state = reducer.reduce(state, event(EventType.CONDITION_CHANGED, start.plusSeconds(1),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.STALLED)));
        state = reducer.reduce(state, event(EventType.ACTIVITY_CHANGED, start.plusSeconds(2),
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.EDITING_FILES)));
        assertEquals(Condition.NORMAL, state.condition());
        state = reducer.reduce(state, event(EventType.TASK_SUCCEEDED, start.plusSeconds(3),
                ProgressImpact.MEANINGFUL, Map.of()));
        state = reducer.reduce(state, event(EventType.ACTIVITY_CHANGED, start.plusSeconds(4),
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.TESTING)));
        assertEquals(Lifecycle.SUCCEEDED, state.lifecycle());
        assertEquals(Activity.IDLE, state.activity());
        assertEquals(start.plusSeconds(3), state.finishedAt());
    }

    @Test void meaningfulProgressRecoversQuietCondition() {
        TaskState state = reducer.reduce(null, event(EventType.TASK_STARTED, start,
                ProgressImpact.MEANINGFUL, Map.of()));
        state = reducer.reduce(state, event(EventType.CONDITION_CHANGED, start.plusSeconds(15),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.QUIET)));
        state = reducer.reduce(state, event(EventType.ACTIVITY_CHANGED, start.plusSeconds(16),
                ProgressImpact.MEANINGFUL, Map.of("activity", Activity.CALLING_TOOL)));
        assertEquals(Condition.NORMAL, state.condition());
        assertEquals(start.plusSeconds(16), state.lastMeaningfulProgressAt());
    }

    @Test void validatesProgress() {
        assertThrows(IllegalArgumentException.class, () -> TaskProgress.determinate(11, 10, "items"));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskProgress(ProgressMode.INDETERMINATE, 1L, null, ""));
        assertEquals(25L, TaskProgress.determinate(25, 100, "%").current());
    }

    private TaskEvent event(EventType type, Instant at, ProgressImpact impact, Map<String, Object> data) {
        return TaskEvent.of(key, relation, type, at, ObservationMode.EXTERNAL,
                EvidenceQuality.STRONG, impact, "Task", "", data);
    }
}
