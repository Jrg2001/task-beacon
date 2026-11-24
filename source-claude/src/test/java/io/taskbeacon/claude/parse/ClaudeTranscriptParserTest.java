package io.taskbeacon.claude.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.core.model.ObservationMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeTranscriptParserTest {
    private final ClaudeTranscriptParser parser = new ClaudeTranscriptParser(new ObjectMapper());
    private final Instant fallback = Instant.parse("2026-09-10T00:00:30Z");

    @Test void parsesLifecycleAndToolMetadataWithoutRetainingContent() {
        var prompt = parser.parse("""
                {"type":"user","sessionId":"s1","cwd":"C:/work/demo","timestamp":"2026-09-10T00:00:00Z","message":{"content":"private prompt"}}
                """, "hint", fallback);
        assertEquals("user_prompt", prompt.getFirst().signalType());
        assertEquals("s1", prompt.getFirst().sessionId());
        assertEquals("C:/work/demo", prompt.getFirst().workspace());
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), prompt.getFirst().observedAt());

        var assistant = parser.parse("""
                {"type":"assistant","sessionId":"s1","timestamp":"2026-09-10T00:00:01Z","message":{"content":[{"type":"thinking","thinking":"secret"},{"type":"tool_use","name":"Bash","input":{"command":"secret command"}},{"type":"text","text":"secret response"}]}}
                """, "hint", fallback);
        assertEquals(3, assistant.size());
        assertEquals("reasoning", assistant.get(0).signalType());
        assertEquals("tool_started", assistant.get(1).signalType());
        assertEquals("Bash", assistant.get(1).toolName());
        assertEquals("assistant_message", assistant.get(2).signalType());
        assertEquals(ObservationMode.SESSION_LOG, assistant.get(1).observationMode());
    }

    @Test void recognizesToolResultsAndTurnCompletion() {
        var result = parser.parse("""
                {"type":"user","session_id":"s2","message":{"content":[{"type":"tool_result","content":"secret output"}]}}
                """, "hint", fallback);
        assertEquals("tool_completed", result.getFirst().signalType());
        assertEquals("s2", result.getFirst().sessionId());

        var completed = parser.parse("""
                {"type":"system","subtype":"turn_duration","sessionId":"s2"}
                """, "hint", fallback);
        assertEquals("turn_completed", completed.getFirst().signalType());
        assertEquals(fallback, completed.getFirst().observedAt());
    }

    @Test void recognizesInterruptedUserRecordAsCancellationNotNewPrompt() {
        var interrupted = parser.parse("""
                {"type":"user","sessionId":"s2","interruptedMessageId":"message-1",
                 "message":{"content":[{"type":"text","text":"interrupted"}]}}
                """, "hint", fallback);
        assertEquals(1, interrupted.size());
        assertEquals("turn_cancelled", interrupted.getFirst().signalType());
        assertEquals("s2", interrupted.getFirst().sessionId());

        var nextPrompt = parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":"next task"}}
                """, "hint", fallback);
        assertEquals("user_prompt", nextPrompt.getFirst().signalType());
    }

    @Test void emitsOneCompletionPerParallelToolResult() {
        var results = parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":[{"type":"tool_result"},{"type":"tool_result"}]}}
                """, "hint", fallback);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(signal -> signal.signalType().equals("tool_completed")));
    }

    @Test void localCommandsAreNotPromptsAndPlainInterruptCancels() {
        assertTrue(parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":"<command-name>/model</command-name>"}}
                """, "hint", fallback).isEmpty());
        assertTrue(parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":"<local-command-stdout>ok</local-command-stdout>"}}
                """, "hint", fallback).isEmpty());
        assertTrue(parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":"<bash-input>ls</bash-input>"}}
                """, "hint", fallback).isEmpty());
        var interrupted = parser.parse("""
                {"type":"user","sessionId":"s2","message":{"content":[{"type":"text","text":"[Request interrupted by user for tool use]"}]}}
                """, "hint", fallback);
        assertEquals("turn_cancelled", interrupted.getFirst().signalType());
    }

    @Test void compactSummaryIsNotAPrompt() {
        assertTrue(parser.parse("""
                {"type":"user","sessionId":"s2","isCompactSummary":true,"isVisibleInTranscriptOnly":true,"message":{"content":"This session is being continued from a previous conversation."}}
                """, "hint", fallback).isEmpty());
    }

    @Test void ignoresMalformedAndUnknownRecords() {
        assertTrue(parser.parse("{broken", "s1", fallback).isEmpty());
        assertTrue(parser.parse("{\"type\":\"file-history-snapshot\"}", "s1", fallback).isEmpty());
    }
}
