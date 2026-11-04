package io.taskbeacon.claude.provider.transcript;

import java.nio.file.Path;
import java.time.Instant;

public record FileCursor(Path path, long offset, Object fileIdentity, byte[] partialLine, Instant lastReadAt) {
    public FileCursor {
        partialLine = partialLine == null ? new byte[0] : partialLine.clone();
    }

    @Override public byte[] partialLine() { return partialLine.clone(); }
}
