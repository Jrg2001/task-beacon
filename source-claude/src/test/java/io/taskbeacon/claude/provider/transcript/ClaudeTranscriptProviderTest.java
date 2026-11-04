package io.taskbeacon.claude.provider.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskbeacon.claude.ClaudeSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeTranscriptProviderTest {
    @TempDir Path projects;

    @Test void emitsQuietAfterCaughtUpWithoutPretendingCompletion() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path project = Files.createDirectory(projects.resolve("demo"));
        Path file = project.resolve("s1.jsonl");
        Files.writeString(file,
                "{\"type\":\"user\",\"sessionId\":\"s1\",\"cwd\":\"C:/work\",\"timestamp\":\"2026-09-10T00:00:00Z\",\"message\":{\"content\":\"prompt\"}}\n");
        Files.setLastModifiedTime(file, FileTime.from(now));
        ClaudeSourceConfig config = new ClaudeSourceConfig(projects, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        ClaudeTranscriptProvider provider = new ClaudeTranscriptProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        CountDownLatch quiet = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        provider.start(signal -> {
            if (signal.signalType().equals("session_quiet")) quiet.countDown();
            if (signal.signalType().equals("turn_completed")) completed.countDown();
        });
        try {
            assertTrue(quiet.await(1, TimeUnit.SECONDS));
            assertEquals(1, completed.getCount());
        } finally {
            provider.close();
        }
    }

    @Test void quietSignalFromSubagentKeepsItsAgentId() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path subagents = Files.createDirectories(projects.resolve("demo").resolve("s1").resolve("subagents"));
        Path file = subagents.resolve("agent-a7.jsonl");
        Files.writeString(file, """
                {"type":"assistant","sessionId":"s1","agentId":"a7","cwd":"C:/work","timestamp":"2026-09-10T00:00:00Z","message":{"content":[{"type":"thinking","thinking":"private"}]}}
                """);
        Files.setLastModifiedTime(file, FileTime.from(now));
        ClaudeSourceConfig config = new ClaudeSourceConfig(projects, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        ClaudeTranscriptProvider provider = new ClaudeTranscriptProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        List<io.taskbeacon.claude.signal.ClaudeRawSignal> signals = new CopyOnWriteArrayList<>();
        CountDownLatch quiet = new CountDownLatch(1);
        provider.start(signal -> {
            signals.add(signal);
            if (signal.signalType().equals("session_quiet")) quiet.countDown();
        });
        try {
            assertTrue(quiet.await(1, TimeUnit.SECONDS));
            var quietSignal = signals.stream().filter(signal -> signal.signalType().equals("session_quiet"))
                    .findFirst().orElseThrow();
            assertEquals("s1", quietSignal.sessionId());
            assertEquals("a7", quietSignal.agentId());
        } finally {
            provider.close();
        }
    }

    @Test void interruptionEndsTranscriptWithoutLaterQuietSignal() throws Exception {
        Instant now = Instant.parse("2026-09-10T00:01:00Z");
        Path project = Files.createDirectory(projects.resolve("cancelled"));
        Path file = project.resolve("s2.jsonl");
        Files.writeString(file, """
                {"type":"user","sessionId":"s2","timestamp":"2026-09-10T00:00:00Z","message":{"content":"prompt"}}
                {"type":"user","sessionId":"s2","timestamp":"2026-09-10T00:00:05Z","interruptedMessageId":"message-1","message":{"content":[{"type":"text","text":"interrupted"}]}}
                """);
        Files.setLastModifiedTime(file, FileTime.from(now));
        ClaudeSourceConfig config = new ClaudeSourceConfig(projects, 4096, 4096,
                Duration.ofMillis(10), Duration.ofHours(1), Duration.ofSeconds(15));
        ClaudeTranscriptProvider provider = new ClaudeTranscriptProvider(config,
                Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
        List<io.taskbeacon.claude.signal.ClaudeRawSignal> signals = new java.util.ArrayList<>();
        try {
            provider.scanOnce(signals::add);
            provider.scanOnce(signals::add);
            assertEquals(List.of("user_prompt", "turn_cancelled"),
                    signals.stream().map(io.taskbeacon.claude.signal.ClaudeRawSignal::signalType).toList());
        } finally {
            provider.close();
        }
    }
}
