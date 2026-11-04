package io.taskbeacon.codex.provider.sessionlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.codex.CodexSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.concurrent.*;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SessionLogProviderTest {
    @TempDir Path sessions;

    @Test void explicitOpenTurnIsNotDeclaredQuietOnlyBecauseItsLogIsSilent() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path file = sessions.resolve("rollout-test.jsonl");
        Files.writeString(file,
                "{\"timestamp\":\"2026-09-10T00:00:00Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"s1\",\"cwd\":\"/work\"}}\n"
                + "{\"timestamp\":\"2026-09-10T00:00:10Z\",\"type\":\"event_msg\",\"payload\":{\"type\":\"task_started\",\"turn_id\":\"t1\"}}\n");
        Files.setLastModifiedTime(file, FileTime.from(now));
        CodexSourceConfig config = new CodexSourceConfig(sessions, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        SessionLogProvider provider = new SessionLogProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        java.util.List<io.taskbeacon.codex.signal.CodexRawSignal> received = new CopyOnWriteArrayList<>();
        provider.start(received::add);
        try {
            Thread.sleep(100);
            assertTrue(received.stream().anyMatch(signal -> signal.signalType().equals("turn_started")));
            assertTrue(received.stream().noneMatch(signal -> signal.signalType().equals("session_quiet")));
        } finally {
            provider.close();
        }
    }

    @Test void inferredMidFlightActivityCanStillBecomeQuiet() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path file = sessions.resolve("rollout-inferred.jsonl");
        Files.writeString(file,
                "{\"timestamp\":\"2026-09-10T00:00:00Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"s-inferred\",\"cwd\":\"/work\"}}\n"
                + "{\"timestamp\":\"2026-09-10T00:00:10Z\",\"type\":\"response_item\",\"payload\":{\"type\":\"reasoning\",\"turn_id\":\"t-inferred\"}}\n");
        Files.setLastModifiedTime(file, FileTime.from(now));
        CodexSourceConfig config = new CodexSourceConfig(sessions, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        SessionLogProvider provider = new SessionLogProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        CountDownLatch quiet = new CountDownLatch(1);
        provider.start(signal -> { if (signal.signalType().equals("session_quiet")) quiet.countDown(); });
        try {
            assertTrue(quiet.await(1, TimeUnit.SECONDS));
        } finally {
            provider.close();
        }
    }

    @Test void tracksWorkspaceAndSessionIdAcrossAppends() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        String uuid = "0d0e00ef-ebaf-4752-a6a7-84564621f840";
        Path file = sessions.resolve("rollout-2026-09-14T11-53-04-" + uuid + ".jsonl");
        Files.writeString(file, "{\"timestamp\":\"2026-09-14T03:53:04Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"" + uuid + "\",\"cwd\":\"D:\\\\TaskBeacon\"}}\n");
        Files.setLastModifiedTime(file, FileTime.from(now));

        CodexSourceConfig config = new CodexSourceConfig(sessions, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        SessionLogProvider provider = new SessionLogProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());

        java.util.List<io.taskbeacon.codex.signal.CodexRawSignal> signals = new java.util.concurrent.CopyOnWriteArrayList<>();
        provider.start(signals::add);

        Thread.sleep(100);
        Files.writeString(file, "{\"timestamp\":\"2026-09-14T03:53:07Z\",\"type\":\"event_msg\",\"payload\":{\"turn_id\":\"turn-1\",\"type\":\"task_started\"}}\n", StandardOpenOption.APPEND);
        Files.setLastModifiedTime(file, FileTime.from(now.plusSeconds(3)));

        Thread.sleep(100);
        Files.writeString(file, "{\"timestamp\":\"2026-09-14T03:53:10Z\",\"type\":\"response_item\",\"payload\":{\"turn_id\":\"turn-1\",\"command\":\"git status\",\"name\":\"exec\",\"type\":\"custom_tool_call\"}}\n", StandardOpenOption.APPEND);
        Files.setLastModifiedTime(file, FileTime.from(now.plusSeconds(6)));

        Thread.sleep(100);
        provider.close();

        for (var s : signals) {
            System.out.println("SIGNAL: type=" + s.signalType() + " session=" + s.sessionId() + " ws=" + s.workspace());
        }

        io.taskbeacon.core.reducer.DefaultTaskStateReducer reducer = new io.taskbeacon.core.reducer.DefaultTaskStateReducer();
        io.taskbeacon.core.registry.DefaultTaskRegistry registry = new io.taskbeacon.core.registry.DefaultTaskRegistry(reducer, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));
        io.taskbeacon.codex.interpret.CodexTaskInterpreter interpreter = new io.taskbeacon.codex.interpret.CodexTaskInterpreter();
        java.util.List<io.taskbeacon.core.model.TaskEvent> events = new java.util.ArrayList<>();
        for (var s : signals) {
            for (var e : interpreter.interpret(s)) {
                events.add(e);
                registry.accept(e);
            }
        }
        for (var e : events) {
            System.out.println("EVENT: type=" + e.type() + " key=" + e.taskKey() + " title=" + e.title() + " ws=" + e.data().get("workspace"));
        }
        for (var t : registry.activeTasks()) {
            System.out.println("ACTIVE TASK: key=" + t.key() + " title=" + t.taskTitle() + " ws=" + t.workspace());
        }
    }

    @Test void buffersInitialReplayUntilTheReaderHasCaughtUp() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path file = sessions.resolve("rollout-buffered.jsonl");
        StringBuilder content = new StringBuilder();
        content.append("{\"timestamp\":\"2026-09-09T00:00:00Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"s2\",\"cwd\":\"/work\"}}\n");
        content.append("{\"timestamp\":\"2026-09-09T00:00:01Z\",\"type\":\"event_msg\",\"payload\":{\"type\":\"task_started\",\"turn_id\":\"t1\"}}\n");
        for (int index = 0; index < 8; index++) {
            content.append("{\"timestamp\":\"2026-09-09T00:00:02Z\",\"type\":\"event_msg\",\"payload\":{\"type\":\"token_count\"}}\n");
        }
        content.append("{\"timestamp\":\"2026-09-09T00:00:03Z\",\"type\":\"event_msg\",\"payload\":{\"type\":\"task_complete\",\"turn_id\":\"t1\"}}\n");
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(now));

        CodexSourceConfig config = new CodexSourceConfig(sessions, 160, 4096,
                Duration.ofSeconds(1), Duration.ofHours(1), Duration.ofSeconds(15));
        SessionLogProvider provider = new SessionLogProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        ArrayList<io.taskbeacon.codex.signal.CodexRawSignal> received = new ArrayList<>();
        try {
            provider.scanOnce(received::add);
            provider.scanOnce(received::add);
            assertTrue(received.isEmpty(), "bootstrap events must not leak before catch-up");
            for (int index = 0; index < 20 && received.isEmpty(); index++) provider.scanOnce(received::add);
            assertFalse(received.isEmpty());
            assertTrue(received.stream().anyMatch(signal -> signal.signalType().equals("turn_started")));
            assertTrue(received.stream().anyMatch(signal -> signal.signalType().equals("turn_completed")));
        } finally {
            provider.close();
        }
    }

    @Test void liveAppendUsesObservationTimeInsteadOfOldEmbeddedTimestampForQuietDetection() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path file = sessions.resolve("rollout-live.jsonl");
        Files.writeString(file,
                "{\"timestamp\":\"2026-09-09T00:00:00Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"s-live\",\"cwd\":\"/work\"}}\n");
        Files.setLastModifiedTime(file, FileTime.from(now));
        CodexSourceConfig config = new CodexSourceConfig(sessions, 4096, 4096,
                Duration.ofSeconds(1), Duration.ofHours(1), Duration.ofSeconds(15));
        SessionLogProvider provider = new SessionLogProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        ArrayList<io.taskbeacon.codex.signal.CodexRawSignal> received = new ArrayList<>();
        try {
            provider.scanOnce(received::add);
            received.clear();
            Files.writeString(file,
                    "{\"timestamp\":\"2026-09-09T00:00:01Z\",\"type\":\"response_item\",\"payload\":{\"type\":\"reasoning\",\"turn_id\":\"t-live\"}}\n",
                    StandardOpenOption.APPEND);
            provider.scanOnce(received::add);
            provider.scanOnce(received::add);

            assertTrue(received.stream().anyMatch(signal -> signal.signalType().equals("reasoning")));
            assertTrue(received.stream().noneMatch(signal -> signal.signalType().equals("session_quiet")),
                    "a newly observed append must not be declared quiet because its embedded timestamp is old");
        } finally {
            provider.close();
        }
    }
}
