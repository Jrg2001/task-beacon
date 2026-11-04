package io.taskbeacon.codex.provider.sessionlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionLogParserTest {
    private final SessionLogParser parser = new SessionLogParser(new ObjectMapper());

    @Test void toleratesMalformedAndUnknownLines() throws Exception {
        assertTrue(parser.parse("not-json", "hint", Instant.EPOCH).isEmpty());
        assertTrue(parser.parse("{\"type\":\"future\",\"payload\":{}}", "hint", Instant.EPOCH).isEmpty());
    }

    @Test void parsesLineWithBom() {
        String lineWithBom = "\uFEFF{\"timestamp\":\"2026-09-14T03:53:04Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"s1\",\"cwd\":\"D:\\\\TaskBeacon\"}}";
        var signal = parser.parse(lineWithBom, "hint", Instant.EPOCH);
        System.out.println("PARSED BOM RESULT: " + signal.isPresent());
        assertTrue(signal.isPresent());
    }

    @Test void parsesSanitizedFixtureWithoutPersistingContent() throws Exception {
        String line;
        try (var input = getClass().getResourceAsStream("/fixtures/session-log/simple-session.jsonl")) {
            assertNotNull(input);
            line = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElseThrow();
        }
        var signal = parser.parse(line, "hint", Instant.EPOCH).orElseThrow();
        assertEquals("session_discovered", signal.signalType());
        assertEquals("session-1", signal.sessionId());
        assertEquals("/work/demo", signal.workspace());
        assertEquals(Instant.parse("2026-09-10T00:00:00Z"), signal.observedAt());
    }

    @Test void responseItemIdIsNeverMistakenForSessionId() {
        var signal = parser.parse("{\"type\":\"response_item\",\"payload\":{\"type\":\"reasoning\",\"id\":\"item-302\"}}",
                "session-from-file", Instant.EPOCH).orElseThrow();
        assertEquals("session-from-file", signal.sessionId());
        assertNotEquals("item-302", signal.sessionId());
    }

    @Test void fileSessionIdentityWinsOverParentSessionReferences() {
        var meta = parser.parse("{\"type\":\"session_meta\",\"payload\":{\"id\":\"child-session\",\"session_id\":\"parent-session\"}}",
                "file-hint", Instant.EPOCH).orElseThrow();
        assertEquals("child-session", meta.sessionId());

        var event = parser.parse("{\"type\":\"event_msg\",\"payload\":{\"type\":\"task_started\",\"session_id\":\"parent-session\",\"turn_id\":\"turn-1\"}}",
                meta.sessionId(), Instant.EPOCH).orElseThrow();
        assertEquals("child-session", event.sessionId());
    }

    @Test void parsesMockLinesAndInterpretsCorrectly() {
        String l1 = "{\"timestamp\":\"2026-09-14T03:53:04.6304291Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"0d0e00ef-ebaf-4752-a6a7-84564621f840\",\"cwd\":\"D:\\\\TaskBeacon\"}}";
        String l2 = "{\"timestamp\":\"2026-09-14T03:53:07.6849303Z\",\"type\":\"event_msg\",\"payload\":{\"turn_id\":\"turn-1\",\"type\":\"task_started\"}}";
        String l3 = "{\"timestamp\":\"2026-09-14T03:53:10.6975637Z\",\"type\":\"response_item\",\"payload\":{\"turn_id\":\"turn-1\",\"command\":\"git status\",\"name\":\"exec\",\"type\":\"custom_tool_call\"}}";
        String l4 = "{\"timestamp\":\"2026-09-14T03:53:13.7167471Z\",\"type\":\"response_item\",\"payload\":{\"turn_id\":\"turn-1\",\"name\":\"read_file\",\"type\":\"custom_tool_call\"}}";

        var s1 = parser.parse(l1, "hint", Instant.EPOCH).orElseThrow();
        assertEquals("session_discovered", s1.signalType());
        assertEquals("0d0e00ef-ebaf-4752-a6a7-84564621f840", s1.sessionId());
        assertEquals("D:\\TaskBeacon", s1.workspace());

        var s2 = parser.parse(l2, s1.sessionId(), Instant.EPOCH).orElseThrow();
        assertEquals("0d0e00ef-ebaf-4752-a6a7-84564621f840", s2.sessionId());

        var s3 = parser.parse(l3, s1.sessionId(), Instant.EPOCH).orElseThrow();
        assertEquals("command_started", s3.signalType());

        var s4 = parser.parse(l4, s1.sessionId(), Instant.EPOCH).orElseThrow();
        assertEquals("tool_started", s4.signalType());

        var interp = new io.taskbeacon.codex.interpret.CodexTaskInterpreter();
        var e1 = interp.interpret(s1);
        var e2 = interp.interpret(s2);
        var e3 = interp.interpret(s3);
        var e4 = interp.interpret(s4);

        assertEquals("TaskBeacon", e2.getFirst().title());
        assertEquals("TaskBeacon", e4.getFirst().title());
    }
}
