package io.taskbeacon.claude.interpret;

import io.taskbeacon.claude.signal.ClaudeRawSignal;
import io.taskbeacon.core.model.*;

import java.nio.file.Path;
import java.util.*;

public final class ClaudeTaskInterpreter {
    private final Map<String, RunCursor> runs = new HashMap<>();
    private final Set<String> activeAgents = new HashSet<>();
    private final Map<String, String> sessionWorkspaces = new HashMap<>();
    private record RunCursor(int generation, boolean active) { }

    public synchronized List<TaskEvent> interpret(ClaudeRawSignal signal) {
        if (signal.sessionId() == null || signal.sessionId().isBlank()) return List.of();
        if (signal.workspace() != null && !signal.workspace().isBlank()) {
            sessionWorkspaces.put(signal.sessionId(), signal.workspace());
        }
        String workspace = signal.workspace();
        if (workspace == null || workspace.isBlank()) {
            workspace = sessionWorkspaces.get(signal.sessionId());
        }
        RunCursor cursor = runs.getOrDefault(signal.sessionId(), new RunCursor(0, false));
        boolean starts = signal.signalType().equals("user_prompt");
        boolean terminal = signal.signalType().equals("turn_completed")
                || signal.signalType().equals("turn_cancelled");
        boolean subagent = signal.agentId() != null && !signal.agentId().isBlank();
        String agentRun = subagent ? signal.sessionId() + ":" + signal.agentId() : "";
        boolean startsSubagent = subagent && !terminal
                && !signal.signalType().equals("session_quiet") && activeAgents.add(agentRun);
        if (starts && signal.agentId() == null && !cursor.active()) {
            cursor = new RunCursor(cursor.generation() + 1, true);
            runs.put(signal.sessionId(), cursor);
        }
        TaskKey session = new TaskKey("claude", "session:" + signal.sessionId());
        TaskKey key = subagent
                ? new TaskKey("claude", "session:" + signal.sessionId() + ":subagent:" + signal.agentId())
                : cursor.active() ? new TaskKey("claude", "session:" + signal.sessionId() + ":run:" + cursor.generation())
                : session;
        TaskRelation relation = key.equals(session) ? TaskRelation.root(TaskScope.SESSION, session)
                : subagent
                ? new TaskRelation(TaskScope.SUBTASK, session, session)
                : new TaskRelation(TaskScope.RUN, session, session);
        Map<String, Object> data = new HashMap<>();
        data.put("sourceDisplayName", "Claude Code");
        if (workspace != null && !workspace.isBlank()) data.put("workspace", workspace);
        EventType eventType;
        ProgressImpact impact = ProgressImpact.MEANINGFUL;
        String type = signal.signalType();
        String message = "";
        if (type.equals("user_prompt") || startsSubagent) {
            eventType = EventType.TASK_STARTED;
            data.put("activity", type.equals("tool_started") ? classifyTool(signal.toolName()) : Activity.THINKING);
        } else if (type.equals("turn_completed")) {
            eventType = EventType.TASK_SUCCEEDED;
        } else if (type.equals("turn_cancelled")) {
            eventType = EventType.TASK_CANCELLED;
        } else if (type.equals("session_quiet")) {
            eventType = EventType.CONDITION_CHANGED; data.put("condition", Condition.QUIET);
            impact = ProgressImpact.OBSERVATION_ONLY;
        } else if (type.equals("reasoning") || type.equals("assistant_message") || type.equals("tool_completed")) {
            eventType = EventType.ACTIVITY_CHANGED; data.put("activity", Activity.THINKING);
        } else if (type.equals("tool_started")) {
            eventType = EventType.ACTIVITY_CHANGED;
            data.put("activity", classifyTool(signal.toolName()));
            if (signal.toolName() != null && !signal.toolName().isBlank()) {
                data.put("activityLabel", signal.toolName());
                message = signal.toolName();
            }
        } else {
            eventType = EventType.TASK_HEARTBEAT; impact = ProgressImpact.OBSERVATION_ONLY;
        }
        TaskEvent event = TaskEvent.of(key, relation, eventType, signal.observedAt(), signal.observationMode(),
                signal.evidenceQuality(), impact, projectName(workspace), message, data);
        if (terminal && signal.agentId() == null && cursor.active()) {
            runs.put(signal.sessionId(), new RunCursor(cursor.generation(), false));
        }
        if (terminal && subagent) activeAgents.remove(agentRun);
        return List.of(event);
    }

    private static Activity classifyTool(String name) {
        String tool = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (tool.equals("bash") || tool.contains("shell")) return Activity.EXECUTING_COMMAND;
        if (tool.equals("edit") || tool.equals("write") || tool.contains("notebookedit")) return Activity.EDITING_FILES;
        if (tool.equals("read")) return Activity.READING_FILES;
        if (tool.equals("glob") || tool.equals("grep") || tool.contains("search") || tool.contains("webfetch")) return Activity.SEARCHING;
        return Activity.CALLING_TOOL;
    }

    private static String projectName(String workspace) {
        if (workspace == null || workspace.isBlank()) return "";
        try {
            Path path = Path.of(workspace);
            Path file = path.getFileName();
            return file == null ? "" : file.toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
