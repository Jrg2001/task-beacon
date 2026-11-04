package io.taskbeacon.core.registry;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaskRegistryTest {
    private final Instant now = Instant.parse("2026-09-10T00:00:00Z");
    private final DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
            Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(30));

    @Test void childCompletionDoesNotCompleteRootAndChildFailureOnlyDegradesIt() {
        TaskKey root = new TaskKey("demo", "root");
        TaskKey child = new TaskKey("demo", "child");
        registry.accept(event(root, TaskRelation.root(TaskScope.RUN, root), EventType.TASK_STARTED));
        TaskRelation childRelation = new TaskRelation(TaskScope.SUBTASK, root, root);
        registry.accept(event(child, childRelation, EventType.TASK_STARTED));
        registry.accept(event(child, childRelation, EventType.TASK_SUCCEEDED));
        assertEquals(Lifecycle.RUNNING, registry.get(root).orElseThrow().lifecycle());
        assertEquals(1, registry.childrenOf(root).size());

        TaskKey failedChild = new TaskKey("demo", "failed-child");
        TaskRelation failedRelation = new TaskRelation(TaskScope.SUBTASK, root, root);
        registry.accept(event(failedChild, failedRelation, EventType.TASK_STARTED));
        registry.accept(event(failedChild, failedRelation, EventType.TASK_FAILED));
        TaskState rootState = registry.get(root).orElseThrow();
        assertEquals(Lifecycle.RUNNING, rootState.lifecycle());
        assertEquals(Attention.WARNING, rootState.attention());
        assertEquals(Condition.DEGRADED, rootState.condition());
    }

    @Test void quietRunningTaskIsNotReportedAsActive() {
        TaskKey key = new TaskKey("test", "quiet");
        TaskRelation relation = TaskRelation.root(TaskScope.RUN, key);
        registry.accept(event(key, relation, EventType.TASK_STARTED));
        registry.accept(TaskEvent.of(key, relation, EventType.CONDITION_CHANGED, now,
                ObservationMode.FILE_WATCH, EvidenceQuality.HEURISTIC,
                ProgressImpact.OBSERVATION_ONLY, "Task", "", Map.of("condition", Condition.QUIET)));

        assertTrue(registry.activeTasks().isEmpty());
        assertEquals(Condition.QUIET, registry.get(key).orElseThrow().condition());
    }

    private TaskEvent event(TaskKey key, TaskRelation relation, EventType type) {
        return TaskEvent.of(key, relation, type, now, ObservationMode.EXTERNAL,
                EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL, "Task", "", Map.of());
    }
}
