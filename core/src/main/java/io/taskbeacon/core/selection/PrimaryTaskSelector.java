package io.taskbeacon.core.selection;

import io.taskbeacon.core.model.TaskState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrimaryTaskSelector {
    Optional<TaskState> select(Collection<TaskState> tasks, SelectionContext context);

    /** All tasks worth showing, most important first. The first element equals {@link #select}. */
    default List<TaskState> rank(Collection<TaskState> tasks, SelectionContext context) {
        return select(tasks, context).map(List::of).orElse(List.of());
    }
}
