package io.taskbeacon.claude.provider.transcript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class AppendOnlyJsonlReaderTest {
    @TempDir Path directory;

    @Test void emitsOnlyCompleteLinesAndResumesPartialLine() throws Exception {
        Path file = directory.resolve("session.jsonl");
        Files.writeString(file, "one\ntwo");
        AppendOnlyJsonlReader reader = new AppendOnlyJsonlReader(1024, 1024,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertEquals(java.util.List.of("one"), reader.readAvailable(file));
        Files.writeString(file, "-done\n", StandardOpenOption.APPEND);
        assertEquals(java.util.List.of("two-done"), reader.readAvailable(file));
        assertTrue(reader.readAvailable(file).isEmpty());
    }

    @Test void resetsAfterTruncation() throws Exception {
        Path file = directory.resolve("session.jsonl");
        Files.writeString(file, "old\n");
        AppendOnlyJsonlReader reader = new AppendOnlyJsonlReader(1024, 1024,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertEquals(java.util.List.of("old"), reader.readAvailable(file));
        Files.writeString(file, "n\n", StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals(java.util.List.of("n"), reader.readAvailable(file));
    }
}
