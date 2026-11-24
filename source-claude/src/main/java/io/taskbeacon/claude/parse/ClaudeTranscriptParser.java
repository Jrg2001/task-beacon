package io.taskbeacon.claude.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.claude.signal.ClaudeRawSignal;
import io.taskbeacon.core.model.EvidenceQuality;
import io.taskbeacon.core.model.ObservationMode;

import java.time.Instant;
import java.util.*;

public final class ClaudeTranscriptParser {
    private final ObjectMapper mapper;

    public ClaudeTranscriptParser(ObjectMapper mapper) { this.mapper = mapper; }

    public List<ClaudeRawSignal> parse(String line, String sessionHint, Instant fallbackTime) {
        try {
            if (line != null && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            JsonNode root = mapper.readTree(line);
            String outer = text(root, "type", "");
            String session = first(root, sessionHint, "sessionId", "session_id");
            String agent = first(root, null, "agentId");
            String workspace = first(root, "", "cwd");
            Instant at = timestamp(root, fallbackTime);
            if (outer.equals("system") && text(root, "subtype", "").equals("turn_duration")) {
                return List.of(signal("turn_completed", at, session, agent, workspace, ""));
            }
            if (outer.equals("user")) {
                JsonNode content = root.path("message").path("content");
                if (!text(root, "interruptedMessageId", "").isBlank()
                        || firstText(content).startsWith("[Request interrupted by user")) {
                    return List.of(signal("turn_cancelled", at, session, agent, workspace, ""));
                }
                int results = countBlocks(content, "tool_result");
                if (results > 0) {
                    // One signal per result so parallel tool calls are balanced against their tool_use blocks.
                    return Collections.nCopies(results, signal("tool_completed", at, session, agent, workspace, ""));
                }
                // A /compact summary is written as a user entry but is not a prompt: no turn follows it, so
                // treating it as one left a run open until the next real prompt (with a stale start time).
                if (!root.path("isMeta").asBoolean(false) && !root.path("isCompactSummary").asBoolean(false)
                        && !root.path("isVisibleInTranscriptOnly").asBoolean(false)
                        && !isLocalCommand(firstText(content))) {
                    return List.of(signal("user_prompt", at, session, agent, workspace, ""));
                }
                return List.of();
            }
            if (!outer.equals("assistant")) return List.of();
            JsonNode content = root.path("message").path("content");
            if (!content.isArray()) return List.of();
            List<ClaudeRawSignal> signals = new ArrayList<>();
            for (JsonNode block : content) {
                String blockType = text(block, "type", "");
                if (blockType.equals("thinking")) signals.add(signal("reasoning", at, session, agent, workspace, ""));
                else if (blockType.equals("tool_use")) signals.add(signal("tool_started", at, session, agent,
                        workspace, text(block, "name", "")));
                else if (blockType.equals("text")) signals.add(signal("assistant_message", at, session, agent, workspace, ""));
            }
            return List.copyOf(signals);
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private static ClaudeRawSignal signal(String type, Instant at, String session, String agent,
                                           String workspace, String tool) {
        return new ClaudeRawSignal(type, at, session, agent, workspace, tool,
                ObservationMode.SESSION_LOG, EvidenceQuality.STRONG);
    }

    /** Slash commands such as /model and `!` shell commands run locally and never produce a turn_duration. */
    private static boolean isLocalCommand(String text) {
        String value = text.stripLeading();
        for (String marker : LOCAL_COMMAND_MARKERS) if (value.startsWith(marker)) return true;
        return false;
    }

    private static final List<String> LOCAL_COMMAND_MARKERS = List.of(
            "<command-name>", "<command-message>", "<local-command-", "<bash-input>", "<bash-stdout>",
            "<bash-stderr>");

    private static String firstText(JsonNode content) {
        if (content.isTextual()) return content.textValue();
        if (!content.isArray()) return "";
        for (JsonNode block : content) {
            if (text(block, "type", "").equals("text")) return text(block, "text", "");
        }
        return "";
    }

    private static int countBlocks(JsonNode content, String type) {
        if (!content.isArray()) return 0;
        int count = 0;
        for (JsonNode block : content) if (text(block, "type", "").equals(type)) count++;
        return count;
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

    private static Instant timestamp(JsonNode node, Instant fallback) {
        try { return Instant.parse(text(node, "timestamp", "")); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
