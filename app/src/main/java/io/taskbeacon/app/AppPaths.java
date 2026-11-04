package io.taskbeacon.app;

import java.nio.file.Path;
import java.util.Map;

public record AppPaths(Path configDirectory, Path configFile, Path logDirectory, Path codexSessionsDirectory,
                       Path claudeProjectsDirectory) {
    public static AppPaths resolve(Map<String, String> environment, String osName, Path userHome) {
        String os = osName.toLowerCase();
        Path configRoot;
        if (os.contains("win") && present(environment.get("LOCALAPPDATA"))) {
            configRoot = Path.of(environment.get("LOCALAPPDATA"), "TaskBeacon");
        } else if (os.contains("mac")) {
            configRoot = userHome.resolve("Library/Application Support/TaskBeacon");
        } else if (present(environment.get("XDG_CONFIG_HOME"))) {
            configRoot = Path.of(environment.get("XDG_CONFIG_HOME"), "taskbeacon");
        } else {
            configRoot = userHome.resolve(".config/taskbeacon");
        }
        Path codexHome = present(environment.get("CODEX_HOME"))
                ? Path.of(environment.get("CODEX_HOME")) : userHome.resolve(".codex");
        Path claudeHome = present(environment.get("CLAUDE_CONFIG_DIR"))
                ? Path.of(environment.get("CLAUDE_CONFIG_DIR")) : userHome.resolve(".claude");
        return new AppPaths(configRoot, configRoot.resolve("config.json"),
                configRoot.resolve("logs"), codexHome.resolve("sessions"), claudeHome.resolve("projects"));
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
