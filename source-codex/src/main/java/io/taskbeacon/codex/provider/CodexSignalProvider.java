package io.taskbeacon.codex.provider;

import io.taskbeacon.api.ProviderHealth;
import io.taskbeacon.codex.signal.CodexRawSignal;

import java.util.function.Consumer;

public interface CodexSignalProvider extends AutoCloseable {
    String id();
    ProviderHealth health();
    void start(Consumer<CodexRawSignal> sink);
    @Override void close();
}
