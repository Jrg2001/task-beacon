package io.taskbeacon.api;

import java.util.Objects;
import java.util.Set;

public record SourceDescriptor(String id, String displayName, String version,
                               Set<SourceCapability> capabilities) {
    public SourceDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        version = version == null ? "unknown" : version;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
