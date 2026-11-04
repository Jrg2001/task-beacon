package io.taskbeacon.claude.provider.transcript;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

public final class TranscriptFileDiscovery {
    private final Path root;
    private final Duration staleThreshold;
    private final Clock clock;

    public TranscriptFileDiscovery(Path root, Duration staleThreshold, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.staleThreshold = staleThreshold;
        this.clock = clock;
    }

    public List<Path> discover() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        var cutoff = clock.instant().minus(staleThreshold);
        List<Path> result = new ArrayList<>();
        try (var paths = Files.find(root, 10, (path, attrs) -> isTranscript(path, attrs)
                && attrs.lastModifiedTime().toInstant().isAfter(cutoff))) {
            paths.sorted(Comparator.comparing((Path path) -> {
                try { return Files.getLastModifiedTime(path); }
                catch (IOException error) { return FileTime.fromMillis(0); }
            }).reversed()).forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static boolean isTranscript(Path path, BasicFileAttributes attrs) {
        return attrs.isRegularFile() && path.getFileName().toString().endsWith(".jsonl");
    }
}
