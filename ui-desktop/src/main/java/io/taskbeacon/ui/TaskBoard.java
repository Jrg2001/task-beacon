package io.taskbeacon.ui;

import io.taskbeacon.core.model.TaskKey;
import io.taskbeacon.core.model.TaskScope;
import io.taskbeacon.core.model.TaskState;

import java.util.*;

/**
 * Groups visible tasks for display: subtasks (Claude subagents) are attached to the task of the same session
 * instead of being shown and counted as separate tasks.
 */
final class TaskBoard {
    record Group(TaskState task, List<TaskState> subtasks) { }

    private TaskBoard() { }

    /** @param ranked visible tasks, most important first; the group order follows it. */
    static List<Group> group(List<TaskState> ranked) {
        List<TaskState> tops = ranked.stream().filter(task -> !isSubtask(task)).toList();
        Map<TaskKey, List<TaskState>> children = new LinkedHashMap<>();
        Set<TaskKey> orphans = new HashSet<>();
        for (TaskState task : ranked) {
            if (!isSubtask(task)) continue;
            TaskState owner = ownerOf(task, tops);
            if (owner == null) orphans.add(task.key());
            else children.computeIfAbsent(owner.key(), ignored -> new ArrayList<>()).add(task);
        }
        List<Group> groups = new ArrayList<>();
        for (TaskState task : ranked) {
            if (!isSubtask(task)) groups.add(new Group(task, List.copyOf(children.getOrDefault(task.key(), List.of()))));
            else if (orphans.contains(task.key())) groups.add(new Group(task, List.of()));
        }
        return List.copyOf(groups);
    }

    /** "codex", "claude", or the raw source id for other sources (e.g. the demo source). */
    static String sourceKey(TaskState task) {
        String id = task.key().sourceId().toLowerCase(Locale.ROOT);
        String name = task.sourceDisplayName() == null ? "" : task.sourceDisplayName().toLowerCase(Locale.ROOT);
        if (id.contains("claude") || name.contains("claude")) return PetAssigner.CLAUDE;
        if (id.contains("codex") || name.contains("codex")) return PetAssigner.CODEX;
        return id;
    }

    private static boolean isSubtask(TaskState task) {
        return task.relation().scope() == TaskScope.SUBTASK;
    }

    /** A subagent belongs to the task of its session: the session task itself or a run with the same parent. */
    private static TaskState ownerOf(TaskState subtask, List<TaskState> tops) {
        TaskKey session = subtask.relation().parentTaskKey();
        if (session == null) return null;
        return tops.stream().filter(top -> top.key().equals(session)
                        || session.equals(top.relation().parentTaskKey()))
                .findFirst().orElse(null);
    }
}
