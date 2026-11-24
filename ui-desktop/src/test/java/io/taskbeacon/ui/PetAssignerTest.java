package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PetAssignerTest {
    private final Instant start = Instant.parse("2026-11-24T00:00:00Z");
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();

    @Test void idleCodexPetIsLentToSecondClaudeTaskAfterDelay() {
        PetAssigner assigner = new PetAssigner(Duration.ofSeconds(3));
        TaskState a = running("claude", "a");
        TaskState b = running("claude", "b");
        Map<String, List<TaskState>> tasks = Map.of(PetAssigner.CLAUDE, List.of(a, b));

        PetAssigner.Result early = assigner.assign(tasks, start);
        assertNull(early.pets().get(PetAssigner.CODEX).task(), "not lent before the delay");

        PetAssigner.Result lent = assigner.assign(tasks, start.plusSeconds(3));
        assertEquals(a.key(), lent.pets().get(PetAssigner.CLAUDE).task().key());
        PetAssigner.PetView codexPet = lent.pets().get(PetAssigner.CODEX);
        assertTrue(codexPet.borrowed());
        assertEquals(PetAssigner.CLAUDE, codexPet.taskSource());
        assertEquals(b.key(), codexPet.task().key());
    }

    @Test void ownerReclaimsItsPetImmediately() {
        PetAssigner assigner = new PetAssigner(Duration.ZERO);
        TaskState a = running("claude", "a");
        TaskState b = running("claude", "b");
        assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(a, b)), start);

        TaskState codexTask = running("codex", "c");
        PetAssigner.Result result = assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(a, b),
                PetAssigner.CODEX, List.of(codexTask)), start.plusSeconds(1));
        PetAssigner.PetView codexPet = result.pets().get(PetAssigner.CODEX);
        assertFalse(codexPet.borrowed());
        assertEquals(codexTask.key(), codexPet.task().key());
    }

    @Test void equalTasksKeepTheirPetsWhenRankingOrderFlips() {
        PetAssigner assigner = new PetAssigner(Duration.ZERO);
        TaskState a = running("claude", "a");
        TaskState b = running("claude", "b");
        assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(a, b)), start);

        // b made progress more recently, so the selector now ranks it first.
        PetAssigner.Result flipped = assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(b, a)), start.plusSeconds(1));
        assertEquals(a.key(), flipped.pets().get(PetAssigner.CLAUDE).task().key());
        assertEquals(b.key(), flipped.pets().get(PetAssigner.CODEX).task().key());
    }

    @Test void attentionTaskMovesToTheOwnersPet() {
        PetAssigner assigner = new PetAssigner(Duration.ZERO);
        TaskState a = running("claude", "a");
        TaskState b = running("claude", "b");
        assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(a, b)), start);

        TaskState stalledB = reducer.reduce(b, event("claude", "b", EventType.CONDITION_CHANGED,
                Map.of("condition", Condition.STALLED)));
        PetAssigner.Result result = assigner.assign(Map.of(PetAssigner.CLAUDE, List.of(stalledB, a)),
                start.plusSeconds(1));
        assertEquals(b.key(), result.pets().get(PetAssigner.CLAUDE).task().key());
        assertEquals(a.key(), result.pets().get(PetAssigner.CODEX).task().key());
    }

    @Test void singleTaskIsNeverSplitAcrossPets() {
        PetAssigner assigner = new PetAssigner(Duration.ZERO);
        PetAssigner.Result result = assigner.assign(Map.of(PetAssigner.CODEX, List.of(running("codex", "x"))), start);
        assertNull(result.pets().get(PetAssigner.CLAUDE).task());
        assertFalse(result.pets().get(PetAssigner.CLAUDE).borrowed());
    }

    private TaskState running(String source, String id) {
        return reducer.reduce(null, event(source, id, EventType.TASK_STARTED, Map.of("activity", Activity.THINKING)));
    }

    private TaskEvent event(String source, String id, EventType type, Map<String, Object> data) {
        TaskKey key = new TaskKey(source, id);
        return TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key), type, start, ObservationMode.EXTERNAL,
                EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL, id, "", data);
    }
}
