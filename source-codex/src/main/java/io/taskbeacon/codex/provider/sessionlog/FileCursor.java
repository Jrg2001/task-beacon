package io.taskbeacon.codex.provider.sessionlog;

import java.time.Instant;
import java.nio.file.Path;

public record FileCursor(Path path, long offset, Object fileIdentity, byte[] partialLine, Instant lastReadAt) {
    public FileCursor {
        partialLine = partialLine == null ? new byte[0] : partialLine.clone();
    }
    @Override public byte[] partialLine() { return partialLine.clone(); }
}
