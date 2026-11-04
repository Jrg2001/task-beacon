package io.taskbeacon.core.registry;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.TaskStateReducer;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultTaskRegistry implements TaskRegistry {
    private final Map<TaskKey, TaskState> states = new ConcurrentHashMap<>();
    private final List<TaskStateListener> listeners = new CopyOnWriteArrayList<>();
    private final TaskStateReducer reducer;
    private final Clock clock;
    private final Duration retention;

    public DefaultTaskRegistry(TaskStateReducer reducer, Clock clock, Duration retention) {
        this.reducer = Objects.requireNonNull(reducer);
        this.clock = Objects.requireNonNull(clock);
        this.retention = Objects.requireNonNull(retention);
    }

    @Override
    public synchronized void accept(TaskEvent event) {
        TaskState next = reducer.reduce(states.get(event.taskKey()), event);
        states.put(event.taskKey(), next);
        notifyListeners(next);
        if (event.type() == EventType.TASK_FAILED && event.relation().parentTaskKey() != null) {
            markParentDegraded(event);
        }
        pruneExpired();
    }

    private void markParentDegraded(TaskEvent childFailure) {
        TaskKey parent = childFailure.relation().parentTaskKey();
        TaskState parentState = states.get(parent);
        if (parentState == null || parentState.lifecycle().isTerminal()) return;
        Map<String, Object> warning = Map.of("attention", Attention.WARNING);
        TaskEvent event = TaskEvent.of(parent, parentState.relation(), EventType.ATTENTION_REQUIRED,
                childFailure.occurredAt(), childFailure.observationMode(), childFailure.evidenceQuality(),
                ProgressImpact.OBSERVATION_ONLY, parentState.taskTitle(), "A child task failed", warning);
        TaskState next = reducer.reduce(parentState, event);
        Map<String, Object> degraded = Map.of("condition", Condition.DEGRADED);
        next = reducer.reduce(next, TaskEvent.of(parent, next.relation(), EventType.CONDITION_CHANGED,
                childFailure.occurredAt(), childFailure.observationMode(), childFailure.evidenceQuality(),
                ProgressImpact.OBSERVATION_ONLY, next.taskTitle(), "A child task failed", degraded));
        states.put(parent, next);
        notifyListeners(next);
    }

    private void notifyListeners(TaskState state) {
        for (TaskStateListener listener : listeners) {
            try { listener.onStateChanged(state); } catch (RuntimeException ignored) { }
        }
    }

    private void pruneExpired() {
        var cutoff = clock.instant().minus(retention);
        states.entrySet().removeIf(entry -> {
            TaskState state = entry.getValue();
            return state.finishedAt() != null && state.finishedAt().isBefore(cutoff)
                    || state.condition() == Condition.QUIET && state.lastObservedAt().isBefore(cutoff);
        });
    }

    @Override public Optional<TaskState> get(TaskKey key) { return Optional.ofNullable(states.get(key)); }

    @Override public List<TaskState> activeTasks() {
        return states.values().stream().filter(state -> state.lifecycle() == Lifecycle.RUNNING
                        && state.condition() != Condition.QUIET)
                .sorted(Comparator.comparing(TaskState::updatedAt).reversed()).toList();
    }

    @Override public List<TaskState> recentTasks() {
        pruneExpired();
        return states.values().stream().sorted(Comparator.comparing(TaskState::updatedAt).reversed()).toList();
    }

    @Override public List<TaskState> childrenOf(TaskKey parent) {
        return states.values().stream().filter(state -> parent.equals(state.relation().parentTaskKey()))
                .sorted(Comparator.comparing(TaskState::updatedAt).reversed()).toList();
    }

    @Override public Subscription subscribe(TaskStateListener listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
}
