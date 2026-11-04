package io.taskbeacon.ui;

import java.util.Locale;

final class SourcePalette {
    static final String CODEX = "#72a7ff";
    static final String CLAUDE = "#f59e0b";

    private SourcePalette() { }

    static String accent(String source) {
        String normalized = source == null ? "" : source.toLowerCase(Locale.ROOT);
        return normalized.contains("claude") ? CLAUDE : CODEX;
    }
}
