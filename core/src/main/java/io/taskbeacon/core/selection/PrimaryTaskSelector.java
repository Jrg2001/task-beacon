package io.taskbeacon.core.selection;

import io.taskbeacon.core.model.TaskState;

import java.util.Collection;
import java.util.Optional;

public interface PrimaryTaskSelector {
    Optional<TaskState> select(Collection<TaskState> tasks, SelectionContext context);
}
