package io.taskbeacon.claude.interpret;

import io.taskbeacon.claude.signal.ClaudeRawSignal;
import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import io.taskbeacon.core.registry.DefaultTaskRegistry;
import io.taskbeacon.core.stall.DefaultStallDetector;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeTaskInterpreterTest {
    private final Instant now = Instant.parse("2026-09-10T00:00:00Z");

    @Test void transcriptRecordsRemainOneRunUntilTurnDuration() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));
        interpreter.interpret(signal("user_prompt", "", 0)).forEach(registry::accept);
        for (int index = 0; index < 302; index++) {
            interpreter.interpret(signal("reasoning", "", index + 1)).forEach(registry::accept);
        }
        assertEquals(1, registry.activeTasks().size());
        assertEquals("session:s1:run:1", registry.activeTasks().getFirst().key().taskId());

        interpreter.interpret(signal("turn_completed", "", 400)).forEach(registry::accept);
        assertTrue(registry.activeTasks().isEmpty());
        assertEquals("session:s1:run:2", interpreter.interpret(signal("user_prompt", "", 401))
                .getFirst().taskKey().taskId());
    }

    @Test void mapsClaudeToolsAndSubagents() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        interpreter.interpret(signal("user_prompt", "", 0));
        assertEquals(Activity.EXECUTING_COMMAND, activity(interpreter.interpret(tool("Bash"))));
        assertEquals(Activity.EDITING_FILES, activity(interpreter.interpret(tool("Edit"))));
        assertEquals(Activity.READING_FILES, activity(interpreter.interpret(tool("Read"))));
        assertEquals(Activity.SEARCHING, activity(interpreter.interpret(tool("Grep"))));
        assertEquals(Activity.CALLING_TOOL, activity(interpreter.interpret(tool("Task"))));

        var child = interpreter.interpret(new ClaudeRawSignal("tool_started", now, "s1", "agent-7",
                "C:/work/demo", "Read", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG)).getFirst();
        assertEquals("session:s1:subagent:agent-7", child.taskKey().taskId());
        assertEquals(EventType.TASK_STARTED, child.type());
        assertEquals(TaskScope.SUBTASK, child.relation().scope());
        assertEquals("session:s1", child.relation().parentTaskKey().taskId());
    }

    @Test void interruptedTurnCancelsCurrentRunAndClearsStalledCondition() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC), Duration.ofHours(1));
        interpreter.interpret(signal("user_prompt", "", 0)).forEach(registry::accept);
        TaskKey run = new TaskKey("claude", "session:s1:run:1");
        TaskEvent stalled = TaskEvent.of(run, new TaskRelation(TaskScope.RUN,
                        new TaskKey("claude", "session:s1"), new TaskKey("claude", "session:s1")),
                EventType.CONDITION_CHANGED, now.plusSeconds(5), ObservationMode.POLLING,
                EvidenceQuality.HEURISTIC, ProgressImpact.OBSERVATION_ONLY, "demo", "",
                java.util.Map.of("condition", Condition.STALLED));
        registry.accept(stalled);

        var cancelled = interpreter.interpret(signal("turn_cancelled", "", 6)).getFirst();
        assertEquals(EventType.TASK_CANCELLED, cancelled.type());
        assertEquals(run, cancelled.taskKey());
        registry.accept(cancelled);
        assertEquals(Lifecycle.CANCELLED, registry.get(run).orElseThrow().lifecycle());
        assertEquals(Condition.NORMAL, registry.get(run).orElseThrow().condition());
        assertTrue(registry.activeTasks().isEmpty());
        assertEquals("session:s1:run:2", interpreter.interpret(signal("user_prompt", "", 7))
                .getFirst().taskKey().taskId());
    }

    @Test void runRecoversFromQuietWithoutSwitchingToRootTask() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC), Duration.ofHours(1));
        interpreter.interpret(signal("user_prompt", "", 0)).forEach(registry::accept);
        ClaudeRawSignal quiet = new ClaudeRawSignal("session_quiet", now.plusSeconds(30), "s1", null,
                "C:/work/demo", "", ObservationMode.FILE_WATCH, EvidenceQuality.HEURISTIC);
        interpreter.interpret(quiet).forEach(registry::accept);
        TaskKey runKey = new TaskKey("claude", "session:s1:run:1");
        assertEquals(Condition.QUIET, registry.get(runKey).orElseThrow().condition());

        interpreter.interpret(signal("tool_completed", "", 40)).forEach(registry::accept);
        assertEquals(Condition.NORMAL, registry.get(runKey).orElseThrow().condition());
        assertTrue(registry.get(new TaskKey("claude", "session:s1")).isEmpty());
        assertEquals(1, registry.activeTasks().size());
    }

    @Test void quietSubagentDoesNotMakeParentQuietOrStalled() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC), Duration.ofHours(1));
        interpreter.interpret(signal("user_prompt", "", 0)).forEach(registry::accept);
        ClaudeRawSignal childStart = new ClaudeRawSignal("tool_started", now.plusSeconds(1), "s1", "agent-7",
                "C:/work/demo", "Read", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG);
        interpreter.interpret(childStart).forEach(registry::accept);

        ClaudeRawSignal childQuiet = new ClaudeRawSignal("session_quiet", now.plusSeconds(30), "s1", "agent-7",
                "C:/work/demo", "", ObservationMode.FILE_WATCH, EvidenceQuality.HEURISTIC);
        interpreter.interpret(childQuiet).forEach(registry::accept);

        TaskKey parentKey = new TaskKey("claude", "session:s1:run:1");
        TaskKey childKey = new TaskKey("claude", "session:s1:subagent:agent-7");
        assertEquals(Condition.NORMAL, registry.get(parentKey).orElseThrow().condition());
        assertEquals(Condition.QUIET, registry.get(childKey).orElseThrow().condition());
        assertTrue(new DefaultStallDetector(Duration.ofMinutes(5))
                .evaluate(registry.get(childKey).orElseThrow(), now.plusSeconds(600)).isEmpty());
        assertEquals(1, registry.activeTasks().size());
        assertEquals(parentKey, registry.activeTasks().getFirst().key());

        ClaudeRawSignal childResumes = new ClaudeRawSignal("reasoning", now.plusSeconds(31), "s1", "agent-7",
                "C:/work/demo", "", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG);
        interpreter.interpret(childResumes).forEach(registry::accept);
        assertEquals(Condition.NORMAL, registry.get(childKey).orElseThrow().condition());
    }

    @Test void longRunningToolIsNotReportedStalledUntilToolThreshold() {
        ClaudeTaskInterpreter interpreter = new ClaudeTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));
        DefaultStallDetector detector = new DefaultStallDetector(Duration.ofMinutes(5), Duration.ofMinutes(20));
        interpreter.interpret(signal("user_prompt", "", 0)).forEach(registry::accept);
        // Two parallel tool calls; only one answered, so a tool is still running.
        interpreter.interpret(signal("tool_started", "Bash", 1)).forEach(registry::accept);
        interpreter.interpret(signal("tool_started", "Bash", 1)).forEach(registry::accept);
        interpreter.interpret(signal("tool_completed", "", 2)).forEach(registry::accept);
        TaskState running = registry.activeTasks().getFirst();
        assertTrue(detector.evaluate(running, now.plus(Duration.ofMinutes(10))).isEmpty());
        assertTrue(detector.evaluate(running, now.plus(Duration.ofMinutes(21))).isPresent());

        interpreter.interpret(signal("tool_completed", "", 3)).forEach(registry::accept);
        TaskState idleBetweenTools = registry.activeTasks().getFirst();
        assertTrue(detector.evaluate(idleBetweenTools, now.plus(Duration.ofMinutes(6))).isPresent());
    }

    private static Activity activity(java.util.List<TaskEvent> events) {
        return (Activity) events.getFirst().data().get("activity");
    }

    private ClaudeRawSignal tool(String name) {
        return new ClaudeRawSignal("tool_started", now.plusSeconds(1), "s1", null,
                "C:/work/demo", name, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG);
    }

    private ClaudeRawSignal signal(String type, String tool, long seconds) {
        return new ClaudeRawSignal(type, now.plusSeconds(seconds), "s1", null,
                "C:/work/demo", tool, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG);
    }
}
