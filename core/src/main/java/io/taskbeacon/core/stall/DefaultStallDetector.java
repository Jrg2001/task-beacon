package io.taskbeacon.core.stall;

import io.taskbeacon.core.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultStallDetector implements StallDetector {
    private static final Logger log = LoggerFactory.getLogger(DefaultStallDetector.class);
    /**
     * Boolean task attribute set by sources that can tell a tool call is still executing (e.g. a long build).
     * Silence while waiting on a tool is expected, so such tasks get {@link #toolInFlightThreshold} instead.
     */
    public static final String TOOL_IN_FLIGHT_ATTRIBUTE = "toolInFlight";
    public static final Duration DEFAULT_TOOL_IN_FLIGHT_THRESHOLD = Duration.ofMinutes(20);

    private final Duration threshold;
    private final Duration toolInFlightThreshold;

    public DefaultStallDetector(Duration threshold) {
        this(threshold, threshold.compareTo(DEFAULT_TOOL_IN_FLIGHT_THRESHOLD) > 0
                ? threshold : DEFAULT_TOOL_IN_FLIGHT_THRESHOLD);
    }

    public DefaultStallDetector(Duration threshold, Duration toolInFlightThreshold) {
        if (threshold == null || threshold.isNegative() || threshold.isZero()) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        if (toolInFlightThreshold == null || toolInFlightThreshold.compareTo(threshold) < 0) {
            throw new IllegalArgumentException("toolInFlightThreshold must be at least threshold");
        }
        this.threshold = threshold;
        this.toolInFlightThreshold = toolInFlightThreshold;
    }

    @Override public Optional<TaskEvent> evaluate(TaskState state, Instant now) {
        Objects.requireNonNull(state); Objects.requireNonNull(now);
        if (state.lifecycle() != Lifecycle.RUNNING || state.lastMeaningfulProgressAt() == null
                || state.attention() == Attention.APPROVAL_REQUIRED
                || state.attention() == Attention.INPUT_REQUIRED
                || state.condition() == Condition.RATE_LIMITED
                || state.condition() == Condition.QUIET
                || state.activity() == Activity.WAITING
                || state.condition() == Condition.STALLED) return Optional.empty();
        Duration quiet = Duration.between(state.lastMeaningfulProgressAt(), now);
        boolean toolInFlight = Boolean.TRUE.equals(state.attributes().get(TOOL_IN_FLIGHT_ATTRIBUTE));
        if (quiet.compareTo(toolInFlight ? toolInFlightThreshold : threshold) < 0) return Optional.empty();
        log.warn("STALL DETECTED: key={} title={} ws={} startedAt={} discoveredAt={} lastMeaningful={} condition={} activity={} toolInFlight={} eventCount={}",
                state.key(), state.taskTitle(), state.workspace(),
                state.startedAt(), state.discoveredAt(), state.lastMeaningfulProgressAt(),
                state.condition(), state.activity(), toolInFlight, state.eventCount());
        return Optional.of(TaskEvent.of(state.key(), state.relation(), EventType.CONDITION_CHANGED, now,
                ObservationMode.POLLING, EvidenceQuality.HEURISTIC, ProgressImpact.OBSERVATION_ONLY,
                state.taskTitle(), "No meaningful progress for " + quiet.toSeconds() + "s",
                Map.of("condition", Condition.STALLED)));
    }
}
