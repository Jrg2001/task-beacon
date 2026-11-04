package io.taskbeacon.codex.provider.sessionlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.codex.signal.CodexRawSignal;
import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;

import java.time.Instant;
import java.util.Optional;

public final class SessionLogParser {
    private final ObjectMapper mapper;

    public SessionLogParser(ObjectMapper mapper) { this.mapper = mapper; }

    public Optional<CodexRawSignal> parse(String line, String sessionHint, Instant observedAt) {
        try {
            if (line != null && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            JsonNode root = mapper.readTree(line);
            String outer = text(root, "type", "unknown");
            JsonNode payload = root.path("payload");
            String inner = text(payload, "type", "");
            String name = text(payload, "name", "");
            String signalType = semanticType(outer, inner, name, payload);
            if (signalType.equals("ignore")) return Optional.empty();
            // `payload.id` on response_item is an item id, not a session id. Likewise,
            // `thread_id` is not stable enough to replace the file's session identity.
            // The rollout file is the session boundary. In nested/continued sessions,
            // payload.session_id can point at the parent session and must not merge two files.
            String session = outer.equals("session_meta")
                    ? first(payload, sessionHint, "id")
                    : sessionHint;
            String turn = first(payload, null, "turn_id", "root_turn_id");
            if (turn == null && payload.has("internal_chat_message_metadata_passthrough")) {
                turn = text(payload.path("internal_chat_message_metadata_passthrough"), "turn_id", null);
            }
            String agent = first(payload, null, "agent_id", "subagent_id");
            String parent = first(payload, null, "parent_agent_id");
            String workspace = first(payload, "", "cwd", "workspace");
            EvidenceQuality evidence = signalType.equals("heartbeat")
                    ? EvidenceQuality.HEURISTIC : EvidenceQuality.STRONG;
            Instant eventTime = timestamp(root, observedAt);
            return Optional.of(new CodexRawSignal("session-log", signalType, eventTime, session,
                    turn, agent, parent, workspace, ObservationMode.SESSION_LOG, evidence, root));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    private static String semanticType(String outer, String inner, String name, JsonNode payload) {
        if (outer.equals("session_meta")) return "session_discovered";
        if (outer.equals("event_msg")) {
            if (inner.equals("task_started")) return "turn_started";
            if (inner.equals("task_complete") || inner.equals("task_completed")
                    || inner.equals("turn_completed") || inner.equals("task_finished")) return "turn_completed";
            if (inner.equals("turn_aborted") || inner.equals("task_aborted")) return "turn_cancelled";
            if (inner.contains("patch") || inner.contains("file")) return "file_changed";
            if (inner.equals("item_completed")) {
                String itemType = text(payload.path("item"), "type", "");
                if (itemType.contains("command")) return "command_completed";
                if (itemType.contains("file") || itemType.contains("patch")) return "file_changed";
                return "item_completed";
            }
            if (inner.contains("subagent") && inner.contains("start")) return "subagent_started";
            if (inner.contains("subagent") && (inner.contains("stop") || inner.contains("complete"))) return "subagent_completed";
            if (inner.contains("fail") || inner.contains("error")) return "turn_failed";
            if (inner.contains("approval") || inner.contains("permission")) return "approval_required";
            if (inner.contains("reason")) return "reasoning";
            if (inner.equals("token_count")) return "heartbeat";
        }
        if (outer.equals("response_item")) {
            if (inner.equals("reasoning")) return "reasoning";
            if (inner.equals("function_call") || inner.equals("custom_tool_call")) {
                return name.equals("exec") || name.contains("command") ? "command_started" : "tool_started";
            }
            if (inner.contains("function_call_output") || inner.contains("tool_call_output")) return "tool_completed";
            if (inner.equals("message")) return "message";
        }
        return outer.equals("turn_context") || outer.equals("world_state") ? "heartbeat" : "ignore";
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : fallback;
    }

    private static String first(JsonNode node, String fallback, String... fields) {
        for (String field : fields) {
            String value = text(node, field, "");
            if (!value.isBlank()) return value;
        }
        return fallback;
    }

    private static Instant timestamp(JsonNode root, Instant fallback) {
        String value = text(root, "timestamp", "");
        if (value.isBlank()) return fallback;
        try { return Instant.parse(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
