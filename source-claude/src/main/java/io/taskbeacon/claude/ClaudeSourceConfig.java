package io.taskbeacon.claude;

import java.nio.file.Path;
import java.time.Duration;

public record ClaudeSourceConfig(Path projectsRoot, long maxReadBytes, long initialTailBytes,
                                 Duration rescanInterval, Duration staleSessionThreshold,
                                 Duration quietThreshold) {
    public ClaudeSourceConfig {
        if (projectsRoot == null) throw new IllegalArgumentException("projectsRoot is required");
        if (maxReadBytes <= 0 || initialTailBytes <= 0) throw new IllegalArgumentException("read sizes must be positive");
        if (rescanInterval == null || rescanInterval.isZero() || rescanInterval.isNegative()) {
            throw new IllegalArgumentException("rescanInterval must be positive");
        }
        if (staleSessionThreshold == null || staleSessionThreshold.isNegative()) {
            throw new IllegalArgumentException("staleSessionThreshold must not be negative");
        }
        if (quietThreshold == null || quietThreshold.isZero() || quietThreshold.isNegative()) {
            throw new IllegalArgumentException("quietThreshold must be positive");
        }
    }
}
