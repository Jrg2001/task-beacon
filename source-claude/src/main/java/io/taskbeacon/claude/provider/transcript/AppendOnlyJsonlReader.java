package io.taskbeacon.claude.provider.transcript;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.*;

public final class AppendOnlyJsonlReader {
    private final long maxReadBytes;
    private final long initialTailBytes;
    private final Clock clock;
    private final Map<Path, FileCursor> cursors = new HashMap<>();

    public AppendOnlyJsonlReader(long maxReadBytes, long initialTailBytes, Clock clock) {
        if (maxReadBytes <= 0 || initialTailBytes <= 0) throw new IllegalArgumentException("read limits must be positive");
        this.maxReadBytes = maxReadBytes;
        this.initialTailBytes = initialTailBytes;
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized List<String> readAvailable(Path input) throws IOException {
        Path path = input.toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        Object identity = attrs.fileKey() == null ? path : attrs.fileKey();
        FileCursor cursor = cursors.get(path);
        boolean reset = cursor != null && (!Objects.equals(cursor.fileIdentity(), identity) || attrs.size() < cursor.offset());
        long offset = cursor == null || reset ? Math.max(0, attrs.size() - initialTailBytes) : cursor.offset();
        byte[] partial = cursor == null || reset ? new byte[0] : cursor.partialLine();
        if (offset >= attrs.size()) {
            cursors.put(path, new FileCursor(path, offset, identity, partial, clock.instant()));
            return List.of();
        }
        int count = (int) Math.min(maxReadBytes, attrs.size() - offset);
        byte[] bytes = new byte[count];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(offset);
            file.readFully(bytes);
        }
        if ((cursor == null || reset) && offset > 0) {
            int newline = indexOf(bytes, (byte) '\n');
            if (newline < 0) {
                cursors.put(path, new FileCursor(path, offset + count, identity, new byte[0], clock.instant()));
                return List.of();
            }
            bytes = Arrays.copyOfRange(bytes, newline + 1, bytes.length);
        }
        byte[] combined = new byte[partial.length + bytes.length];
        System.arraycopy(partial, 0, combined, 0, partial.length);
        System.arraycopy(bytes, 0, combined, partial.length, bytes.length);
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < combined.length; index++) {
            if (combined[index] != '\n') continue;
            int end = index > start && combined[index - 1] == '\r' ? index - 1 : index;
            if (end > start) lines.add(new String(combined, start, end - start, StandardCharsets.UTF_8));
            start = index + 1;
        }
        cursors.put(path, new FileCursor(path, offset + count, identity,
                Arrays.copyOfRange(combined, start, combined.length), clock.instant()));
        return List.copyOf(lines);
    }

    public synchronized FileCursor cursor(Path path) { return cursors.get(path.toAbsolutePath().normalize()); }

    private static int indexOf(byte[] bytes, byte value) {
        for (int i = 0; i < bytes.length; i++) if (bytes[i] == value) return i;
        return -1;
    }
}
