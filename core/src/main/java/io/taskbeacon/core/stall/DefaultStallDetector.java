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
    private final Duration threshold;

    public DefaultStallDetector(Duration threshold) {
        if (threshold == null || threshold.isNegative() || threshold.isZero()) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
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
        if (quiet.compareTo(threshold) < 0) return Optional.empty();
        log.warn("STALL DETECTED: key={} title={} ws={} startedAt={} discoveredAt={} lastMeaningful={} condition={} activity={} eventCount={}",
                state.key(), state.taskTitle(), state.workspace(),
                state.startedAt(), state.discoveredAt(), state.lastMeaningfulProgressAt(),
                state.condition(), state.activity(), state.eventCount());
        return Optional.of(TaskEvent.of(state.key(), state.relation(), EventType.CONDITION_CHANGED, now,
                ObservationMode.POLLING, EvidenceQuality.HEURISTIC, ProgressImpact.OBSERVATION_ONLY,
                state.taskTitle(), "No meaningful progress for " + quiet.toSeconds() + "s",
                Map.of("condition", Condition.STALLED)));
    }
}
