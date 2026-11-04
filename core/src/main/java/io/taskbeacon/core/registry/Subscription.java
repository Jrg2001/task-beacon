package io.taskbeacon.core.registry;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override void close();
}
