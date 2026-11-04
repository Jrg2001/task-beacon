package io.taskbeacon.core.model;

public enum Lifecycle {
    DISCOVERED, RUNNING, SUCCEEDED, FAILED, CANCELLED, UNKNOWN;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
