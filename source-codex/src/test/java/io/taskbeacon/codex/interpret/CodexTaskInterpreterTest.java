package io.taskbeacon.codex.interpret;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.taskbeacon.codex.signal.CodexRawSignal;
import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import io.taskbeacon.core.registry.DefaultTaskRegistry;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CodexTaskInterpreterTest {
    private final Instant now = Instant.parse("2026-09-10T00:00:00Z");

    @Test void hundredsOfItemsInOneSessionRemainOneActiveRun() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));
        interpreter.interpret(signal("session_discovered", "session-1", null, null, 0))
                .forEach(registry::accept);
        interpreter.interpret(signal("turn_started", "session-1", "turn-1", null, 1))
                .forEach(registry::accept);
        for (int index = 0; index < 302; index++) {
            interpreter.interpret(signal("reasoning", "session-1", "turn-1",
                    "item-" + index, index + 2)).forEach(registry::accept);
        }
        assertEquals(1, registry.activeTasks().size());
        assertEquals("session:session-1:run:1", registry.activeTasks().getFirst().key().taskId());

        interpreter.interpret(signal("turn_completed", "session-1", "turn-1", null, 400))
                .forEach(registry::accept);
        assertEquals(0, registry.activeTasks().size());

        var next = interpreter.interpret(signal("turn_started", "session-1", "turn-2", null, 401));
        assertEquals("session:session-1:run:2", next.getFirst().taskKey().taskId());
    }

    @Test void inheritsWorkspaceFromSessionMetaAndExtractsActivityDetails() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();

        // 1. session_meta provides workspace
        var metaPayload = JsonNodeFactory.instance.objectNode();
        CodexRawSignal meta = new CodexRawSignal("session-log", "session_discovered", now, "s1", null,
                null, null, "D:\\projects\\awesome-app", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, metaPayload);
        var metaEvents = interpreter.interpret(meta);
        assertEquals("awesome-app", metaEvents.getFirst().title());

        // 2. turn_started without workspace should inherit "awesome-app"
        var startPayload = JsonNodeFactory.instance.objectNode();
        CodexRawSignal start = new CodexRawSignal("session-log", "turn_started", now.plusSeconds(1), "s1", "t1",
                null, null, "", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, startPayload);
        var startEvents = interpreter.interpret(start);
        assertEquals("awesome-app", startEvents.getFirst().title());
        assertEquals("D:\\projects\\awesome-app", startEvents.getFirst().data().get("workspace"));

        // 3. command execution should set activityLabel
        var cmdPayload = JsonNodeFactory.instance.objectNode();
        var innerCmd = cmdPayload.putObject("payload");
        innerCmd.put("command", "git status --short");
        CodexRawSignal cmd = new CodexRawSignal("session-log", "command_started", now.plusSeconds(2), "s1", "t1",
                null, null, null, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, cmdPayload);
        var cmdEvents = interpreter.interpret(cmd);
        assertEquals("git status --short", cmdEvents.getFirst().data().get("activityLabel"));

        // 4. tool call should set activityLabel
        var toolPayload = JsonNodeFactory.instance.objectNode();
        toolPayload.putObject("payload").put("name", "read_file");
        CodexRawSignal tool = new CodexRawSignal("session-log", "tool_started", now.plusSeconds(3), "s1", "t1",
                null, null, null, ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, toolPayload);
        var toolEvents = interpreter.interpret(tool);
        assertEquals("read_file", toolEvents.getFirst().data().get("activityLabel"));
    }

    @Test void activatesRunMidFlightWhenCommandOrPatchArrivesWithoutPriorTurnStart() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));

        var cmdPayload = JsonNodeFactory.instance.objectNode();
        var innerCmd = cmdPayload.putObject("payload");
        innerCmd.put("name", "exec");
        innerCmd.put("input", "const r = await tools.exec_command({\n  cmd: \"node .tmp_instrument_formal\\\\inspect_ledger.mjs 'data.xlsx'\",\n  workdir: \"D:\\\\fii\"\n});");
        CodexRawSignal cmd = new CodexRawSignal("session-log", "command_started", now, "s2", null,
                null, null, "D:\\OneDrive\\桌面\\fii", ObservationMode.SESSION_LOG, EvidenceQuality.STRONG, cmdPayload);

        var events = interpreter.interpret(cmd);
        assertEquals(2, events.size());
        assertEquals(EventType.TASK_STARTED, events.get(0).type());
        assertEquals(EventType.ACTIVITY_CHANGED, events.get(1).type());

        events.forEach(registry::accept);
        assertEquals(1, registry.activeTasks().size());
        TaskState active = registry.activeTasks().getFirst();
        assertEquals(Lifecycle.RUNNING, active.lifecycle());
        assertEquals(Activity.EXECUTING_COMMAND, active.activity());
        assertTrue(active.activityLabel().contains("node"));
        assertEquals("fii", active.taskTitle());
    }

    @Test void newerTurnSupersedesUnclosedRunAndQuietRemovesItFromActiveTasks() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(200), ZoneOffset.UTC), Duration.ofHours(1));

        interpreter.interpret(signal("turn_started", "s3", "turn-old", null, 0)).forEach(registry::accept);
        var nextTurn = interpreter.interpret(signal("turn_started", "s3", "turn-new", null, 100));
        assertEquals(2, nextTurn.size());
        assertEquals(EventType.CONDITION_CHANGED, nextTurn.get(0).type());
        assertEquals(Condition.QUIET, nextTurn.get(0).data().get("condition"));
        assertEquals("session:s3:run:2", nextTurn.get(1).taskKey().taskId());
        nextTurn.forEach(registry::accept);

        assertEquals(1, registry.activeTasks().size());
        assertEquals(now.plusSeconds(100), registry.activeTasks().getFirst().startedAt());

        interpreter.interpret(signal("session_quiet", "s3", "turn-new", null, 120))
                .forEach(registry::accept);
        assertTrue(registry.activeTasks().isEmpty());

        var resumed = interpreter.interpret(signal("turn_started", "s3", "turn-third", null, 150));
        assertEquals("session:s3:run:3", resumed.getLast().taskKey().taskId());
    }

    @Test void messageImmediatelyBeforeTurnStartBelongsToThatRunAndDoesNotLeaveSessionRunning() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC), Duration.ofHours(1));

        interpreter.interpret(signal("message", "s4", "turn-1", null, 0)).forEach(registry::accept);
        interpreter.interpret(signal("turn_started", "s4", "turn-1", null, 1)).forEach(registry::accept);

        assertEquals(1, registry.activeTasks().size());
        assertEquals("session:s4:run:1", registry.activeTasks().getFirst().key().taskId());

        interpreter.interpret(signal("turn_completed", "s4", "turn-1", null, 2)).forEach(registry::accept);
        interpreter.interpret(signal("tool_completed", "s4", "turn-1", null, 3)).forEach(registry::accept);

        assertTrue(registry.activeTasks().isEmpty());
        assertTrue(registry.recentTasks().stream()
                .noneMatch(state -> state.key().taskId().equals("session:s4")
                        && state.lifecycle() == Lifecycle.RUNNING));
    }

    @Test void delayedCompletionForOlderTurnDoesNotCompleteOrResetCurrentTurn() {
        CodexTaskInterpreter interpreter = new CodexTaskInterpreter();
        DefaultTaskRegistry registry = new DefaultTaskRegistry(new DefaultTaskStateReducer(),
                Clock.fixed(now.plusSeconds(30), ZoneOffset.UTC), Duration.ofHours(1));

        interpreter.interpret(signal("turn_started", "s5", "turn-old", null, 0)).forEach(registry::accept);
        interpreter.interpret(signal("turn_started", "s5", "turn-current", null, 10)).forEach(registry::accept);
        interpreter.interpret(signal("turn_completed", "s5", "turn-old", null, 13)).forEach(registry::accept);
        interpreter.interpret(signal("reasoning", "s5", "turn-current", null, 15)).forEach(registry::accept);

        assertEquals(1, registry.activeTasks().size());
        TaskState current = registry.activeTasks().getFirst();
        assertEquals("session:s5:run:2", current.key().taskId());
        assertEquals(now.plusSeconds(10), current.startedAt());
        assertEquals(Lifecycle.RUNNING, current.lifecycle());

        interpreter.interpret(signal("session_quiet", "s5", null, null, 20)).forEach(registry::accept);
        assertTrue(registry.activeTasks().isEmpty());
    }

    private CodexRawSignal signal(String type, String session, String turn, String itemId, long seconds) {
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("id", itemId == null ? "ignored" : itemId);
        return new CodexRawSignal("session-log", type, now.plusSeconds(seconds), session, turn,
                null, null, "/work/demo", ObservationMode.SESSION_LOG,
                EvidenceQuality.STRONG, payload);
    }
}
