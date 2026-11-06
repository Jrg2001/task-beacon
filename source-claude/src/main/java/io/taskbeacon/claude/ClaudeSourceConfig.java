package io.taskbeacon.claude;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @param quietThreshold         silence after which an idle (between-turns) transcript is reported quiet
 * @param openTurnQuietThreshold silence tolerated while a main-session turn is still open. Claude Code writes
 *                               nothing while a long tool runs or while a large response/tool call is being
 *                               generated, so an open turn must not be hidden after the normal quiet threshold.
 */
public record ClaudeSourceConfig(Path projectsRoot, long maxReadBytes, long initialTailBytes,
                                 Duration rescanInterval, Duration staleSessionThreshold,
                                 Duration quietThreshold, Duration openTurnQuietThreshold) {
    public static final Duration DEFAULT_OPEN_TURN_QUIET_THRESHOLD = Duration.ofMinutes(30);

    public ClaudeSourceConfig(Path projectsRoot, long maxReadBytes, long initialTailBytes,
                              Duration rescanInterval, Duration staleSessionThreshold, Duration quietThreshold) {
        this(projectsRoot, maxReadBytes, initialTailBytes, rescanInterval, staleSessionThreshold, quietThreshold,
                DEFAULT_OPEN_TURN_QUIET_THRESHOLD);
    }

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
        if (openTurnQuietThreshold == null || openTurnQuietThreshold.compareTo(quietThreshold) < 0) {
            throw new IllegalArgumentException("openTurnQuietThreshold must be at least quietThreshold");
        }
    }
}
