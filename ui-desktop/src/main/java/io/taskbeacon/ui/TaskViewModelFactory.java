package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;

import java.time.*;
import java.util.Locale;

public final class TaskViewModelFactory {
    private final Clock clock;

    public TaskViewModelFactory(Clock clock) { this.clock = clock; }

    public TaskCardViewModel create(TaskState state, int activeCount) {
        String badge = badge(state);
        String status = status(state);
        String icon = icon(state);
        Instant from = state.startedAt() == null ? state.discoveredAt() : state.startedAt();
        Instant to = state.finishedAt() == null ? clock.instant() : state.finishedAt();
        Duration duration = Duration.between(from, to);
        if (duration.isNegative()) duration = Duration.ZERO;
        String evidenceHint = state.currentEvidence() == EvidenceQuality.HEURISTIC
                ? "Status inferred from local activity" : "";
        String title = resolveTitle(state);
        return new TaskCardViewModel(icon, state.sourceDisplayName(), title,
                status, formatDuration(duration), badge, evidenceHint, activeCount);
    }

    private static String resolveTitle(TaskState state) {
        String source = state.sourceDisplayName() == null ? "" : state.sourceDisplayName().trim();
        String title = state.taskTitle() == null ? "" : state.taskTitle().trim();
        String workspace = state.workspace();
        String folder = workspaceName(workspace);

        boolean genericTitle = title.isBlank()
                || title.equalsIgnoreCase("Codex task")
                || title.equalsIgnoreCase("Claude task")
                || title.equalsIgnoreCase("Task")
                || (!source.isBlank() && title.equalsIgnoreCase(source));

        if (!genericTitle) {
            return title;
        }
        if (!folder.equals("Task")) {
            return folder;
        }
        return "Active task";
    }

    private static String status(TaskState state) {
        if (state.attention() == Attention.ERROR) return "Failed";
        if (state.attention() == Attention.APPROVAL_REQUIRED) return "Approval required";
        if (state.attention() == Attention.INPUT_REQUIRED) return "Input required";
        if (state.condition() == Condition.STALLED) return "Possibly stalled";
        if (state.condition() == Condition.QUIET) return "No recent activity";
        if (state.condition() == Condition.RATE_LIMITED) return "Rate limited";
        if (state.lifecycle() == Lifecycle.SUCCEEDED) return "Completed";
        if (state.lifecycle() == Lifecycle.FAILED) return "Failed";
        if (state.lifecycle() == Lifecycle.CANCELLED) return "Cancelled";
        if (state.lifecycle() == Lifecycle.DISCOVERED) return "Detected";

        String detail = !state.activityLabel().isBlank() ? state.activityLabel() : state.message();
        return switch (state.activity()) {
            case STARTING -> "Starting…";
            case THINKING -> "Thinking…";
            case EXECUTING_COMMAND -> detail.isBlank() ? "Running command" : formatDetail("Running", detail);
            case EDITING_FILES -> detail.isBlank() ? "Editing files" : formatDetail("Editing", detail);
            case READING_FILES -> detail.isBlank() ? "Reading files" : formatDetail("Reading", detail);
            case SEARCHING -> detail.isBlank() ? "Searching" : formatDetail("Searching", detail);
            case TESTING -> detail.isBlank() ? "Running tests" : formatDetail("Testing", detail);
            case CALLING_TOOL -> detail.isBlank() ? "Using tool" : formatDetail("Tool", detail);
            case BUILDING -> detail.isBlank() ? "Building" : formatDetail("Building", detail);
            case DEPLOYING -> detail.isBlank() ? "Deploying" : formatDetail("Deploying", detail);
            case WAITING -> "Waiting";
            case OTHER -> detail.isBlank() ? "Working" : truncate(detail, 28);
            case IDLE, UNKNOWN -> state.lifecycle() == Lifecycle.RUNNING ? "Running" : "Detected";
        };
    }

    private static String formatDetail(String action, String detail) {
        String clean = detail.trim();
        if (clean.isBlank()) return action;
        if (action.equals("Editing") || action.equals("Reading")) {
            if (clean.contains("/") || clean.contains("\\")) {
                try {
                    var fn = java.nio.file.Path.of(clean).getFileName();
                    if (fn != null) clean = fn.toString();
                } catch (RuntimeException ignored) { }
            }
        }
        clean = truncate(clean, 24);
        if (clean.toLowerCase(Locale.ROOT).startsWith(action.toLowerCase(Locale.ROOT))) {
            return clean;
        }
        return action + ": " + clean;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max - 1) + "…";
    }

    private static String badge(TaskState state) {
        if (state.attention() != Attention.NONE) return "!";
        if (state.condition() == Condition.STALLED || state.condition() == Condition.DEGRADED) return "⚠";
        return "";
    }

    private static String icon(TaskState state) {
        if (state.attention() != Attention.NONE) return "●";
        if (state.condition() == Condition.STALLED) return "◆";
        if (state.lifecycle() == Lifecycle.SUCCEEDED) return "✓";
        if (state.lifecycle() == Lifecycle.FAILED) return "×";
        return "●";
    }

    private static String formatDuration(Duration value) {
        long seconds = value.toSeconds();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remaining = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, remaining)
                : "%02d:%02d".formatted(minutes, remaining);
    }

    private static String workspaceName(String workspace) {
        if (workspace == null || workspace.isBlank()) return "Task";
        try {
            var fileName = java.nio.file.Path.of(workspace).getFileName();
            return fileName == null ? "Task" : fileName.toString();
        } catch (RuntimeException error) {
            return "Task";
        }
    }
}
