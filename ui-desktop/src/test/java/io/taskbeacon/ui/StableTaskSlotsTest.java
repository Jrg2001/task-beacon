package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StableTaskSlotsTest {
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();
    private final Instant now = Instant.parse("2026-09-15T00:00:00Z");

    @Test void updatesDoNotSwapSlotsAndSecondaryMovesUpOnlyAfterPrimaryEnds() {
        StableTaskSlots slots = new StableTaskSlots();
        TaskState codex = running("codex", "run-1", now);
        TaskState claude = running("claude", "run-1", now.plusSeconds(1));

        var initial = slots.select(List.of(claude, codex));
        assertEquals(claude.key(), initial.primary().key());
        assertEquals(codex.key(), initial.secondary().key());

        TaskState updatedCodex = reducer.reduce(codex, event(codex.key(), EventType.ACTIVITY_CHANGED,
                now.plusSeconds(10), Map.of("activity", Activity.CALLING_TOOL)));
        var afterUpdate = slots.select(List.of(updatedCodex, claude));
        assertEquals(claude.key(), afterUpdate.primary().key());
        assertEquals(codex.key(), afterUpdate.secondary().key());

        var afterPrimaryEnds = slots.select(List.of(updatedCodex));
        assertEquals(codex.key(), afterPrimaryEnds.primary().key());
        assertNull(afterPrimaryEnds.secondary());
    }

    @Test void newTaskFillsEmptySecondaryWithoutMovingPrimary() {
        StableTaskSlots slots = new StableTaskSlots();
        TaskState codex = running("codex", "run-1", now);
        assertEquals(codex.key(), slots.select(List.of(codex)).primary().key());

        TaskState claude = running("claude", "run-2", now.plusSeconds(5));
        var selection = slots.select(List.of(claude, codex));
        assertEquals(codex.key(), selection.primary().key());
        assertEquals(claude.key(), selection.secondary().key());
    }

    private TaskState running(String source, String id, Instant at) {
        TaskKey key = new TaskKey(source, id);
        return reducer.reduce(null, event(key, EventType.TASK_STARTED, at,
                Map.of("activity", Activity.THINKING)));
    }

    private TaskEvent event(TaskKey key, EventType type, Instant at, Map<String, Object> data) {
        return TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key), type, at,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                "Task", "", data);
    }
}
