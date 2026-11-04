package io.taskbeacon.api;

import io.taskbeacon.core.model.TaskEvent;

@FunctionalInterface
public interface TaskEventSink {
    void publish(TaskEvent event);
}
