package io.taskbeacon.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;

public final class ConfigLoader {
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public TaskBeaconConfig load(AppPaths paths) {
        try {
            Files.createDirectories(paths.configDirectory());
            Files.createDirectories(paths.logDirectory());
            if (Files.notExists(paths.configFile())) {
                TaskBeaconConfig defaults = TaskBeaconConfig.defaults();
                mapper.writerWithDefaultPrettyPrinter().writeValue(paths.configFile().toFile(), defaults);
                return defaults;
            }
            return mapper.readValue(paths.configFile().toFile(), TaskBeaconConfig.class).normalized();
        } catch (IOException | RuntimeException error) {
            System.err.println("TaskBeacon config could not be loaded; safe defaults are active.");
            return TaskBeaconConfig.defaults();
        }
    }
}
