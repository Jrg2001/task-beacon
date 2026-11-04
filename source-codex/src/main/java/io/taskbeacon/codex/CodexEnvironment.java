package io.taskbeacon.codex;

import java.nio.file.Path;
import java.util.Map;

public final class CodexEnvironment {
    private CodexEnvironment() { }

    public static Path defaultSessionsRoot(Map<String, String> environment) {
        String codexHome = environment.get("CODEX_HOME");
        if (codexHome != null && !codexHome.isBlank()) return Path.of(codexHome, "sessions");
        String userHome = environment.get("USERPROFILE");
        if (userHome == null || userHome.isBlank()) userHome = System.getProperty("user.home");
        return Path.of(userHome, ".codex", "sessions");
    }
}
