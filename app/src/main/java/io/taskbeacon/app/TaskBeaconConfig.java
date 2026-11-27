package io.taskbeacon.app;

public record TaskBeaconConfig(Ui ui, Core core, Sources sources, Privacy privacy) {
    public record Ui(boolean alwaysOnTop, boolean animations, long completedDisplaySeconds, String renderer) { }
    public record Core(long stallThresholdSeconds, long recentTaskRetentionMinutes) { }
    public record Sources(Codex codex, Claude claude) { }
    public record Codex(boolean enabled, String mode, String executable, boolean enableHooks,
                        boolean enableSessionLogs, boolean enableExecJson, boolean enableProcessProbe) { }
    public record Claude(boolean enabled, boolean enableSessionLogs, boolean enableHooks) { }
    public record Privacy(boolean showFullCommands, boolean persistEventPayloads, boolean persistPrompts) { }

    public static TaskBeaconConfig defaults() {
        return new TaskBeaconConfig(new Ui(true, true, 8, "pet"),
                new Core(300, 30), new Sources(new Codex(true, "AUTO", "codex",
                false, true, false, false), new Claude(true, true, false)),
                new Privacy(false, false, false));
    }

    public TaskBeaconConfig normalized() {
        TaskBeaconConfig defaults = defaults();
        return new TaskBeaconConfig(ui == null ? defaults.ui : ui,
                core == null ? defaults.core : core,
                normalizeSources(sources, defaults.sources),
                privacy == null ? defaults.privacy : privacy);
    }

    private static Sources normalizeSources(Sources value, Sources defaults) {
        if (value == null) return defaults;
        return new Sources(value.codex == null ? defaults.codex : value.codex,
                value.claude == null ? defaults.claude : value.claude);
    }
}
