package io.taskbeacon.core.model;

import java.util.Objects;

public record TaskProgress(ProgressMode mode, Long current, Long total, String unit) {
    public TaskProgress {
        Objects.requireNonNull(mode, "mode");
        unit = unit == null ? "" : unit;
        if (mode == ProgressMode.DETERMINATE) {
            if (current == null || total == null || current < 0 || total <= 0 || current > total) {
                throw new IllegalArgumentException("determinate progress requires 0 <= current <= total and total > 0");
            }
        } else if (current != null || total != null) {
            throw new IllegalArgumentException("non-determinate progress cannot have current or total");
        }
    }

    public static TaskProgress none() { return new TaskProgress(ProgressMode.NONE, null, null, ""); }
    public static TaskProgress indeterminate() { return new TaskProgress(ProgressMode.INDETERMINATE, null, null, ""); }
    public static TaskProgress determinate(long current, long total, String unit) {
        return new TaskProgress(ProgressMode.DETERMINATE, current, total, unit);
    }
}
