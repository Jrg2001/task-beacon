package io.taskbeacon.api;

public interface TaskSource extends AutoCloseable {
    SourceDescriptor descriptor();
    void start(TaskEventSink sink) throws Exception;
    SourceHealth health();
    @Override void close() throws Exception;
}
