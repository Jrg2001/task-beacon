package io.taskbeacon.ui;

import io.taskbeacon.core.model.Attention;
import io.taskbeacon.core.model.Condition;
import io.taskbeacon.core.model.Lifecycle;
import io.taskbeacon.core.model.TaskKey;
import io.taskbeacon.core.model.TaskState;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Decides which task each of the two pets shows. A pet always shows its own tool's most important task. When its
 * tool has had nothing to show for {@code lendDelay} and the other tool has two or more tasks, the pet is lent to
 * the other tool; the owner reclaims it as soon as it has a task again. Assignments are sticky, so pets do not
 * trade tasks on every refresh.
 */
final class PetAssigner {
    static final String CODEX = "codex";
    static final String CLAUDE = "claude";
    static final List<String> OWNERS = List.of(CODEX, CLAUDE);

    /** @param task null when the pet is idle; {@code borrowed} when it shows the other tool's task. */
    record PetView(String owner, TaskState task, String taskSource, boolean borrowed) { }

    record Result(Map<String, PetView> pets) { }

    private final Duration lendDelay;
    private final Map<String, TaskKey> shownKey = new HashMap<>();
    private final Map<String, Boolean> shownBorrowed = new HashMap<>();
    private final Map<String, Instant> idleSince = new HashMap<>();

    PetAssigner(Duration lendDelay) { this.lendDelay = Objects.requireNonNull(lendDelay); }

    /** @param tasksBySource top-level visible tasks per owner, most important first. */
    Result assign(Map<String, List<TaskState>> tasksBySource, Instant now) {
        for (String owner : OWNERS) {
            if (tasks(tasksBySource, owner).isEmpty()) idleSince.putIfAbsent(owner, now);
            else idleSince.remove(owner);
        }
        Map<String, PetView> pets = new LinkedHashMap<>();
        // A pet shows its own tool first, avoiding the task that the other (lent) pet is already showing.
        for (String owner : OWNERS) {
            List<TaskState> own = tasks(tasksBySource, owner);
            if (own.isEmpty()) continue;
            String other = other(owner);
            TaskKey avoid = wasBorrowed(other) ? shownKey.get(other) : null;
            TaskKey previous = wasBorrowed(owner) ? null : shownKey.get(owner);
            pets.put(owner, new PetView(owner, pickOwn(own, previous, avoid), owner, false));
        }
        for (String owner : OWNERS) {
            if (pets.containsKey(owner)) continue;
            String lender = other(owner);
            PetView lenderPet = pets.get(lender);
            List<TaskState> candidates = new ArrayList<>(tasks(tasksBySource, lender));
            if (lenderPet != null) candidates.removeIf(task -> task.key().equals(lenderPet.task().key()));
            boolean idleLongEnough = Duration.between(idleSince.getOrDefault(owner, now), now)
                    .compareTo(lendDelay) >= 0;
            if (candidates.isEmpty() || !idleLongEnough) {
                pets.put(owner, new PetView(owner, null, owner, false));
                continue;
            }
            TaskKey previous = wasBorrowed(owner) ? shownKey.get(owner) : null;
            TaskState borrowed = candidates.stream().filter(task -> task.key().equals(previous)).findFirst()
                    .orElse(candidates.getFirst());
            pets.put(owner, new PetView(owner, borrowed, lender, true));
        }
        for (PetView pet : pets.values()) {
            shownKey.put(pet.owner(), pet.task() == null ? null : pet.task().key());
            shownBorrowed.put(pet.owner(), pet.borrowed());
        }
        return new Result(Map.copyOf(pets));
    }

    /** Keeps the previous task while it is as important as the top one, so equal tasks do not trade places. */
    private static TaskState pickOwn(List<TaskState> own, TaskKey previous, TaskKey avoid) {
        int top = importance(own.getFirst());
        for (TaskState task : own) {
            if (importance(task) == top && task.key().equals(previous)) return task;
        }
        for (TaskState task : own) {
            if (importance(task) != top) break;
            if (!task.key().equals(avoid)) return task;
        }
        return own.getFirst();
    }

    /** Coarse importance; finer ordering within a level comes from the selector's ranking. */
    static int importance(TaskState task) {
        if (task.attention() != Attention.NONE) return 3;
        if (task.condition() == Condition.STALLED) return 2;
        if (task.lifecycle() == Lifecycle.RUNNING) return 1;
        return 0;
    }

    private boolean wasBorrowed(String owner) { return Boolean.TRUE.equals(shownBorrowed.get(owner)); }

    private static List<TaskState> tasks(Map<String, List<TaskState>> tasksBySource, String owner) {
        return tasksBySource.getOrDefault(owner, List.of());
    }

    private static String other(String owner) { return owner.equals(CODEX) ? CLAUDE : CODEX; }
}
