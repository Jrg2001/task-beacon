package io.taskbeacon.codex.interpret;

import io.taskbeacon.codex.signal.CodexRawSignal;
import io.taskbeacon.core.model.*;

import java.nio.file.Path;
import java.util.*;

public final class CodexTaskInterpreter {
    private final CommandClassifier commands = new CommandClassifier();
    private final Map<String, SessionRuns> runs = new HashMap<>();
    private final Map<String, String> sessionWorkspaces = new HashMap<>();

    private static final class SessionRuns {
        private int nextGeneration;
        private Integer currentGeneration;
        private final Map<String, Integer> turnGenerations = new HashMap<>();
        private final LinkedHashSet<Integer> activeGenerations = new LinkedHashSet<>();

        int allocate(String turnId) {
            int generation = ++nextGeneration;
            if (turnId != null && !turnId.isBlank()) turnGenerations.put(turnId, generation);
            return generation;
        }

        Integer generationFor(String turnId) {
            return turnId == null || turnId.isBlank() ? null : turnGenerations.get(turnId);
        }
    }

    public synchronized List<TaskEvent> interpret(CodexRawSignal signal) {
        if (signal.sessionId() == null || signal.sessionId().isBlank()) return List.of();
        if (signal.workspace() != null && !signal.workspace().isBlank()) {
            sessionWorkspaces.put(signal.sessionId(), signal.workspace());
        }
        String workspace = signal.workspace();
        if (workspace == null || workspace.isBlank()) {
            workspace = sessionWorkspaces.get(signal.sessionId());
        }
        TaskKey sessionKey = new TaskKey("codex", "session:" + signal.sessionId());
        Map<String, Object> common = new HashMap<>();
        common.put("sourceDisplayName", "Codex");
        if (workspace != null && !workspace.isBlank()) common.put("workspace", workspace);
        common.put("attributes", Map.of("provider", signal.providerId()));
        String title = projectName(workspace);
        String type = signal.signalType().toLowerCase(Locale.ROOT);
        boolean sessionDiscovered = type.equals("session_discovered");
        boolean runStarted = (type.equals("turn_started") || type.equals("task_started")
                || type.equals("user_prompt") || (type.contains("prompt") && !type.contains("tool")))
                && !type.contains("command") && !type.contains("tool");
        boolean runTerminal = type.contains("turn_completed") || type.equals("turn_cancelled")
                || type.equals("stop") || type.equals("task_complete") || type.contains("turn_failed");
        boolean activitySignal = type.contains("command") || type.contains("file") || type.contains("patch")
                || type.contains("tool") || type.contains("item") || type.contains("reason")
                || type.contains("search") || type.equals("message");
        boolean hasActiveWork = activitySignal && !type.contains("completed") && !type.contains("output")
                && !runTerminal && !sessionDiscovered;
        SessionRuns sessionRuns = runs.computeIfAbsent(signal.sessionId(), ignored -> new SessionRuns());
        List<TaskEvent> preceding = new ArrayList<>();
        boolean rootSignal = signal.agentId() == null || signal.agentId().isBlank();
        boolean justActivated = false;
        Integer generation = null;

        if (type.equals("session_quiet") && rootSignal) {
            for (int activeGeneration : List.copyOf(sessionRuns.activeGenerations)) {
                preceding.add(quietEvent(signal, sessionKey, activeGeneration, title, common,
                        "Session has no recent activity"));
            }
            sessionRuns.activeGenerations.clear();
            sessionRuns.currentGeneration = null;
            return List.copyOf(preceding);
        }

        if (rootSignal && runStarted) {
            generation = sessionRuns.generationFor(signal.turnId());
            if (generation != null && !sessionRuns.activeGenerations.contains(generation)) {
                // A delayed duplicate start for a turn that already ended must not resurrect it.
                return List.of();
            }
            if (generation == null) {
                generation = sessionRuns.allocate(signal.turnId());
            }
            quietOtherRuns(signal, sessionKey, sessionRuns, generation, title, common, preceding);
            sessionRuns.activeGenerations.add(generation);
            sessionRuns.currentGeneration = generation;
        } else if (rootSignal && runTerminal) {
            generation = sessionRuns.generationFor(signal.turnId());
            if (generation == null && (signal.turnId() == null || signal.turnId().isBlank())) {
                generation = sessionRuns.currentGeneration;
            }
            // A completion for an unknown/older turn must never finish the current turn.
            if (generation == null || !sessionRuns.activeGenerations.contains(generation)) return List.of();
        } else if (rootSignal && activitySignal) {
            generation = sessionRuns.generationFor(signal.turnId());
            if (generation != null && !sessionRuns.activeGenerations.contains(generation)) {
                return List.of();
            }
            if (generation == null && !hasActiveWork) return List.of();
            if (generation == null) {
                if ((signal.turnId() == null || signal.turnId().isBlank())
                        && sessionRuns.currentGeneration != null
                        && sessionRuns.activeGenerations.contains(sessionRuns.currentGeneration)) {
                    generation = sessionRuns.currentGeneration;
                } else {
                    generation = sessionRuns.allocate(signal.turnId());
                    quietOtherRuns(signal, sessionKey, sessionRuns, generation, title, common, preceding);
                    sessionRuns.activeGenerations.add(generation);
                    sessionRuns.currentGeneration = generation;
                    justActivated = true;
                }
            }
        } else if (rootSignal && !sessionDiscovered) {
            generation = sessionRuns.currentGeneration;
        }
        TaskKey key = taskKey(signal, sessionKey, generation, sessionDiscovered);
        TaskRelation relation = relation(signal, key, sessionKey, sessionDiscovered);
        EventType eventType;
        ProgressImpact impact = ProgressImpact.MEANINGFUL;
        String message = "";

        if (sessionDiscovered) {
            eventType = EventType.TASK_DISCOVERED; impact = ProgressImpact.OBSERVATION_ONLY;
        } else if (runStarted) {
            eventType = EventType.TASK_STARTED; common.put("activity", Activity.THINKING);
        } else if (type.contains("permission") || type.contains("approval")) {
            eventType = EventType.ATTENTION_REQUIRED; common.put("attention", Attention.APPROVAL_REQUIRED);
            impact = ProgressImpact.OBSERVATION_ONLY;
        } else if (type.contains("interrupt") || type.contains("cancel")) {
            eventType = EventType.TASK_CANCELLED;
        } else if (type.contains("fail") || type.contains("error")) {
            eventType = EventType.TASK_FAILED;
        } else if (type.contains("turn_completed") || type.contains("subagent_completed")
                || type.equals("stop") || type.equals("task_complete")) {
            eventType = EventType.TASK_SUCCEEDED;
        } else if (type.contains("command")) {
            eventType = EventType.ACTIVITY_CHANGED;
            String command = safeCommand(signal);
            common.put("activity", commands.classify(command));
            if (!command.isBlank()) common.put("activityLabel", command);
            message = command;
        } else if (type.contains("file") || type.contains("patch")) {
            eventType = EventType.ACTIVITY_CHANGED;
            common.put("activity", Activity.EDITING_FILES);
            String target = extractTargetFile(signal);
            if (!target.isBlank()) common.put("activityLabel", target);
            message = target;
        } else if (type.contains("search")) {
            eventType = EventType.ACTIVITY_CHANGED; common.put("activity", Activity.SEARCHING);
        } else if (type.contains("tool") || type.contains("item")) {
            eventType = EventType.ACTIVITY_CHANGED;
            common.put("activity", Activity.CALLING_TOOL);
            String toolName = extractToolName(signal);
            if (!toolName.isBlank()) common.put("activityLabel", toolName);
            message = toolName;
        } else if (type.contains("reason") || type.equals("message")) {
            eventType = EventType.ACTIVITY_CHANGED; common.put("activity", Activity.THINKING);
        } else {
            eventType = EventType.TASK_HEARTBEAT; impact = ProgressImpact.OBSERVATION_ONLY;
        }
        TaskEvent event = TaskEvent.of(key, relation, eventType, signal.observedAt(), signal.observationMode(),
                signal.evidenceQuality(), impact, title, message, common);
        if (runTerminal && rootSignal && generation != null) {
            sessionRuns.activeGenerations.remove(generation);
            if (Objects.equals(sessionRuns.currentGeneration, generation)) {
                sessionRuns.currentGeneration = sessionRuns.activeGenerations.isEmpty() ? null
                        : sessionRuns.activeGenerations.getLast();
            }
        }
        if (justActivated) {
            Map<String, Object> startData = new HashMap<>(common);
            startData.put("activity", Activity.STARTING);
            TaskEvent startEvent = TaskEvent.of(key, relation, EventType.TASK_STARTED, signal.observedAt(),
                    signal.observationMode(), signal.evidenceQuality(), ProgressImpact.MEANINGFUL,
                    title, "Task active", startData);
            preceding.add(startEvent);
        }
        preceding.add(event);
        return List.copyOf(preceding);
    }

    private static void quietOtherRuns(CodexRawSignal signal, TaskKey sessionKey, SessionRuns sessionRuns,
                                       int keepGeneration, String title, Map<String, Object> common,
                                       List<TaskEvent> events) {
        for (int activeGeneration : List.copyOf(sessionRuns.activeGenerations)) {
            if (activeGeneration == keepGeneration) continue;
            events.add(quietEvent(signal, sessionKey, activeGeneration, title, common,
                    "Superseded by a newer turn"));
            sessionRuns.activeGenerations.remove(activeGeneration);
        }
    }

    private static TaskEvent quietEvent(CodexRawSignal signal, TaskKey sessionKey, int generation,
                                        String title, Map<String, Object> common, String message) {
        TaskKey key = new TaskKey("codex", "session:" + signal.sessionId() + ":run:" + generation);
        Map<String, Object> data = new HashMap<>(common);
        data.put("condition", Condition.QUIET);
        return TaskEvent.of(key, new TaskRelation(TaskScope.RUN, sessionKey, sessionKey),
                EventType.CONDITION_CHANGED, signal.observedAt(), signal.observationMode(),
                EvidenceQuality.HEURISTIC, ProgressImpact.OBSERVATION_ONLY,
                title, message, data);
    }

    private static TaskKey taskKey(CodexRawSignal signal, TaskKey session, Integer generation,
                                   boolean sessionDiscovered) {
        if (sessionDiscovered) return session;
        if (signal.agentId() != null && !signal.agentId().isBlank()) return new TaskKey("codex", "subagent:" + signal.agentId());
        return generation != null ? new TaskKey("codex", "session:" + signal.sessionId() + ":run:" + generation)
                : session;
    }

    private static TaskRelation relation(CodexRawSignal signal, TaskKey key, TaskKey session,
                                         boolean sessionDiscovered) {
        if (sessionDiscovered || key.equals(session)) return TaskRelation.root(TaskScope.SESSION, session);
        if (signal.agentId() != null && !signal.agentId().isBlank()) {
            TaskKey parent = session;
            return new TaskRelation(TaskScope.SUBTASK, session, parent);
        }
        return new TaskRelation(TaskScope.RUN, session, session);
    }

    private static String projectName(String workspace) {
        if (workspace == null || workspace.isBlank()) return "";
        try {
            Path path = Path.of(workspace);
            Path fileName = path.getFileName();
            return fileName == null ? "" : fileName.toString();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static final java.util.regex.Pattern CMD_PATTERN =
            java.util.regex.Pattern.compile("(?:cmd|command)\\s*:\\s*\"([^\"]+)\"");
    private static final java.util.regex.Pattern PATCH_FILE_PATTERN =
            java.util.regex.Pattern.compile("\\*\\*\\* Update File:\\s*([^\r\n]+)");

    private static String safeCommand(CodexRawSignal signal) {
        if (signal.payload() == null) return "";
        var payload = signal.payload().path("payload");
        var commandNode = payload.path("command");
        if (commandNode.isMissingNode() || commandNode.isNull()) {
            commandNode = payload.path("cmd");
        }
        if (commandNode.isMissingNode() || commandNode.isNull()) {
            commandNode = signal.payload().path("item").path("command");
        }
        if (commandNode.isMissingNode() || commandNode.isNull()) {
            commandNode = payload.path("item").path("command");
        }
        String value = "";
        if (commandNode.isArray()) {
            List<String> parts = new ArrayList<>();
            for (var elem : commandNode) {
                if (elem.isTextual() && !elem.asText().isBlank()) {
                    parts.add(elem.asText());
                }
            }
            if (parts.size() >= 3 && (parts.get(0).endsWith("powershell.exe") || parts.get(0).endsWith("pwsh.exe")
                    || parts.get(0).equals("powershell") || parts.get(0).equals("pwsh")
                    || parts.get(0).endsWith("cmd.exe") || parts.get(0).equals("cmd"))
                    && (parts.get(1).equalsIgnoreCase("-Command") || parts.get(1).equalsIgnoreCase("/c"))) {
                value = parts.get(2);
            } else {
                value = String.join(" ", parts);
            }
        } else if (commandNode.isTextual()) {
            value = commandNode.asText("");
        }
        if (value.isBlank()) {
            String input = payload.path("input").asText("");
            if (!input.isBlank()) {
                var m = CMD_PATTERN.matcher(input);
                if (m.find()) {
                    value = m.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
                } else if (input.contains("apply_patch")) {
                    value = "apply_patch";
                }
            }
        }
        if (value.isBlank()) return "";
        String[] tokens = value.trim().split("\\s+");
        return String.join(" ", Arrays.copyOf(tokens, Math.min(tokens.length, 3)));
    }

    private static String extractToolName(CodexRawSignal signal) {
        if (signal.payload() == null) return "";
        var payload = signal.payload().path("payload");
        String name = payload.path("name").asText("");
        if (name.isBlank()) {
            name = payload.path("item").path("type").asText("");
        }
        if (name.isBlank()) {
            name = signal.payload().path("name").asText("");
        }
        if (name.isBlank()) {
            name = signal.payload().path("item").path("type").asText("");
        }
        return name;
    }

    private static String extractTargetFile(CodexRawSignal signal) {
        if (signal.payload() == null) return "";
        var payload = signal.payload().path("payload");
        String target = payload.path("path").asText("");
        if (target.isBlank()) target = payload.path("file").asText("");
        if (target.isBlank()) target = payload.path("item").path("path").asText("");
        if (target.isBlank()) target = payload.path("item").path("file").asText("");
        if (target.isBlank()) target = signal.payload().path("item").path("path").asText("");
        if (target.isBlank()) target = signal.payload().path("item").path("file").asText("");
        if (target.isBlank()) {
            var changes = payload.path("changes");
            if (changes.isObject() && changes.fieldNames().hasNext()) {
                target = changes.fieldNames().next();
            }
        }
        if (target.isBlank()) {
            String input = payload.path("input").asText("");
            var m = PATCH_FILE_PATTERN.matcher(input);
            if (m.find()) {
                target = m.group(1).trim();
            }
        }
        if (target.isBlank()) {
            String stdout = payload.path("stdout").asText("");
            int mIdx = stdout.indexOf("\nM ");
            if (mIdx >= 0) {
                int nextNl = stdout.indexOf('\n', mIdx + 3);
                target = nextNl > 0 ? stdout.substring(mIdx + 3, nextNl).trim() : stdout.substring(mIdx + 3).trim();
            }
        }
        if (target.isBlank()) return "";
        try {
            Path p = Path.of(target);
            Path f = p.getFileName();
            return f == null ? target : f.toString();
        } catch (RuntimeException ignored) {
            int lastSlash = Math.max(target.lastIndexOf('/'), target.lastIndexOf('\\'));
            return lastSlash >= 0 ? target.substring(lastSlash + 1) : target;
        }
    }
}
