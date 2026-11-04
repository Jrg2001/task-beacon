package io.taskbeacon.core.reducer;

import io.taskbeacon.core.model.TaskEvent;
import io.taskbeacon.core.model.TaskState;

public interface TaskStateReducer {
    TaskState reduce(TaskState previous, TaskEvent event);
}
