package io.taskbeacon.codex.provider.sessionlog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppendOnlyJsonlReaderTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC);

    @Test void readsOnlyAppendsAndJoinsPartialLines() throws Exception {
        Path file = directory.resolve("rollout-test.jsonl");
        Files.writeString(file, "{\"a\":1}\n{\"b\":", StandardCharsets.UTF_8);
        AppendOnlyJsonlReader reader = new AppendOnlyJsonlReader(1024, 1024, clock);
        assertEquals(List.of("{\"a\":1}"), reader.readAvailable(file));
        assertTrue(reader.readAvailable(file).isEmpty());
        Files.writeString(file, "2}\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(List.of("{\"b\":2}"), reader.readAvailable(file));
    }

    @Test void recoversAfterTruncateAndBoundsEachRead() throws Exception {
        Path file = directory.resolve("rollout-test.jsonl");
        Files.writeString(file, "one\ntwo\nthree\n", StandardCharsets.UTF_8);
        AppendOnlyJsonlReader reader = new AppendOnlyJsonlReader(8, 1024, clock);
        assertEquals(List.of("one", "two"), reader.readAvailable(file));
        assertTrue(reader.cursor(file).offset() <= 8);
        Files.writeString(file, "new\n", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(List.of("new"), reader.readAvailable(file));
    }

    @Test void initialTailSkipsPartialLeadingLine() throws Exception {
        Path file = directory.resolve("rollout-large.jsonl");
        Files.writeString(file, "ignored-long-line\nkeep-one\nkeep-two\n", StandardCharsets.UTF_8);
        AppendOnlyJsonlReader reader = new AppendOnlyJsonlReader(1024, 20, clock);
        assertEquals(List.of("keep-one", "keep-two"), reader.readAvailable(file));
    }
}
