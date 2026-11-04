package io.taskbeacon.codex;

import java.nio.file.Path;
import java.time.Duration;

public record CodexSourceConfig(Path sessionsRoot, long maxReadBytes, long initialTailBytes,
                                Duration rescanInterval, Duration staleSessionThreshold,
                                Duration quietThreshold) {
    public CodexSourceConfig {
        if (sessionsRoot == null) throw new IllegalArgumentException("sessionsRoot is required");
        if (maxReadBytes <= 0 || initialTailBytes <= 0) throw new IllegalArgumentException("read sizes must be positive");
        if (rescanInterval == null || rescanInterval.isNegative() || rescanInterval.isZero()) {
            throw new IllegalArgumentException("rescanInterval must be positive");
        }
        if (staleSessionThreshold == null || staleSessionThreshold.isNegative()) {
            throw new IllegalArgumentException("staleSessionThreshold must not be negative");
        }
        if (quietThreshold == null || quietThreshold.isNegative() || quietThreshold.isZero()) {
            throw new IllegalArgumentException("quietThreshold must be positive");
        }
    }
}
