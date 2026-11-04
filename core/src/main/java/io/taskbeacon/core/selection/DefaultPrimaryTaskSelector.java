package io.taskbeacon.core.selection;

import io.taskbeacon.core.model.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class DefaultPrimaryTaskSelector implements PrimaryTaskSelector {
    @Override public Optional<TaskState> select(Collection<TaskState> tasks, SelectionContext context) {
        return tasks.stream().filter(state -> visible(state, context))
                .max(Comparator.comparingInt((TaskState state) -> priority(state, context))
                        .thenComparing(TaskState::lastMeaningfulProgressAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TaskState::updatedAt));
    }

    private boolean visible(TaskState state, SelectionContext context) {
        if (state.attention() != Attention.NONE) return true;
        if (state.lifecycle() == Lifecycle.RUNNING) return state.condition() != Condition.QUIET;
        return state.lifecycle().isTerminal() && state.finishedAt() != null
                && !state.finishedAt().plus(context.completedDisplayTtl()).isBefore(context.now());
    }

    private int priority(TaskState state, SelectionContext context) {
        if (state.attention() == Attention.ERROR) return 1000;
        if (state.attention() == Attention.APPROVAL_REQUIRED) return 900;
        if (state.attention() == Attention.INPUT_REQUIRED) return 800;
        if (state.attention() == Attention.WARNING) return 700;
        if (state.condition() == Condition.STALLED) return 650;
        if (state.lifecycle() == Lifecycle.RUNNING && state.key().equals(context.pinnedTask())) return 600;
        if (state.lifecycle() == Lifecycle.RUNNING) return 500;
        if (state.lifecycle() == Lifecycle.FAILED) return 400;
        if (state.lifecycle() == Lifecycle.SUCCEEDED) return 300;
        return 100;
    }
}
