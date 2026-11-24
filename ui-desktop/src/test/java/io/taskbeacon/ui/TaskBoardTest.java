package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskBoardTest {
    private final Instant start = Instant.parse("2026-11-24T00:00:00Z");
    private final DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();
    private final TaskKey session = new TaskKey("claude", "session:s1");

    @Test void subagentIsShownUnderTheRunOfItsSession() {
        TaskState run = task(new TaskKey("claude", "session:s1:run:2"), TaskScope.RUN);
        TaskState agent = task(new TaskKey("claude", "session:s1:subagent:a7"), TaskScope.SUBTASK);
        TaskState other = task(new TaskKey("codex", "x"), TaskScope.RUN);

        List<TaskBoard.Group> groups = TaskBoard.group(List.of(agent, other, run));
        assertEquals(2, groups.size(), "the subagent is not a separate task");
        assertEquals(other.key(), groups.get(0).task().key());
        assertEquals(run.key(), groups.get(1).task().key());
        assertEquals(List.of(agent), groups.get(1).subtasks());
    }

    @Test void subagentWithoutVisibleParentStillShows() {
        TaskState agent = task(new TaskKey("claude", "session:s1:subagent:a7"), TaskScope.SUBTASK);
        List<TaskBoard.Group> groups = TaskBoard.group(List.of(agent));
        assertEquals(1, groups.size());
        assertEquals(agent.key(), groups.getFirst().task().key());
    }

    @Test void sourceKeyRecognisesBothTools() {
        assertEquals("claude", TaskBoard.sourceKey(task(new TaskKey("claude", "a"), TaskScope.RUN)));
        assertEquals("codex", TaskBoard.sourceKey(task(new TaskKey("codex", "b"), TaskScope.RUN)));
        assertEquals("demo", TaskBoard.sourceKey(task(new TaskKey("demo", "c"), TaskScope.RUN)));
    }

    private TaskState task(TaskKey key, TaskScope scope) {
        TaskRelation relation = key.sourceId().equals("claude")
                ? new TaskRelation(scope, session, session) : TaskRelation.root(scope, key);
        return reducer.reduce(null, TaskEvent.of(key, relation, EventType.TASK_STARTED, start,
                ObservationMode.EXTERNAL, EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL, "t", "",
                Map.of("activity", Activity.THINKING)));
    }
}
