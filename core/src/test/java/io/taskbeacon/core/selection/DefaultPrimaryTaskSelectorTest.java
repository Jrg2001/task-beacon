package io.taskbeacon.core.selection;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPrimaryTaskSelectorTest {
    private final Instant now = Instant.parse("2026-09-10T00:00:10Z");
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();

    @Test void attentionBeatsStallAndRunningAndExpiredCompletionIsHidden() {
        TaskState running = state("running", EventType.TASK_STARTED, now.minusSeconds(2), Map.of());
        TaskState stalled = reducer.reduce(state("stalled", EventType.TASK_STARTED, now.minusSeconds(5), Map.of()),
                event("stalled", EventType.CONDITION_CHANGED, now.minusSeconds(1),
                        Map.of("condition", Condition.STALLED)));
        TaskState approval = reducer.reduce(state("approval", EventType.TASK_STARTED, now.minusSeconds(8), Map.of()),
                event("approval", EventType.ATTENTION_REQUIRED, now.minusSeconds(3),
                        Map.of("attention", Attention.APPROVAL_REQUIRED)));
        TaskState selected = new DefaultPrimaryTaskSelector().select(List.of(running, stalled, approval),
                new SelectionContext(now, Duration.ofSeconds(8), null)).orElseThrow();
        assertEquals("approval", selected.key().taskId());
    }

    @Test void quietRunningTaskDoesNotOccupyPrimaryCard() {
        TaskState running = state("quiet", EventType.TASK_STARTED, now.minusSeconds(20), Map.of());
        TaskState quiet = reducer.reduce(running, event("quiet", EventType.CONDITION_CHANGED,
                now.minusSeconds(5), Map.of("condition", Condition.QUIET)));
        assertTrue(new DefaultPrimaryTaskSelector().select(List.of(quiet),
                new SelectionContext(now, Duration.ofSeconds(8), null)).isEmpty());
    }

    private TaskState state(String id, EventType type, Instant at, Map<String, Object> data) {
        return reducer.reduce(null, event(id, type, at, data));
    }

    private TaskEvent event(String id, EventType type, Instant at, Map<String, Object> data) {
        TaskKey key = new TaskKey("test", id);
        return TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key), type, at,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Task", "", data);
    }
}
