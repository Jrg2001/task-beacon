package io.taskbeacon.core.stall;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultStallDetectorTest {
    private final Instant start = Instant.parse("2026-09-10T00:00:00Z");

    @Test void usesMeaningfulProgressThresholdAndSkipsApprovalAndTerminalTasks() {
        DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();
        DefaultStallDetector detector = new DefaultStallDetector(Duration.ofMinutes(5));
        TaskState state = reducer.reduce(null, event(EventType.TASK_STARTED, start,
                ProgressImpact.MEANINGFUL, Map.of()));
        assertTrue(detector.evaluate(state, start.plusSeconds(299)).isEmpty());
        assertEquals(Condition.STALLED, detector.evaluate(state, start.plusSeconds(301)).orElseThrow()
                .data().get("condition"));

        TaskState approval = reducer.reduce(state, event(EventType.ATTENTION_REQUIRED, start.plusSeconds(10),
                ProgressImpact.OBSERVATION_ONLY, Map.of("attention", Attention.APPROVAL_REQUIRED)));
        assertTrue(detector.evaluate(approval, start.plusSeconds(400)).isEmpty());
        TaskState terminal = reducer.reduce(state, event(EventType.TASK_SUCCEEDED, start.plusSeconds(20),
                ProgressImpact.MEANINGFUL, Map.of()));
        assertTrue(detector.evaluate(terminal, start.plusSeconds(400)).isEmpty());

        TaskState quiet = reducer.reduce(state, event(EventType.CONDITION_CHANGED, start.plusSeconds(20),
                ProgressImpact.OBSERVATION_ONLY, Map.of("condition", Condition.QUIET)));
        assertTrue(detector.evaluate(quiet, start.plusSeconds(400)).isEmpty());
    }

    private TaskEvent event(EventType type, Instant at, ProgressImpact impact, Map<String, Object> data) {
        TaskKey key = new TaskKey("test", "one");
        return TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key), type, at,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, impact, "Task", "", data);
    }
}
