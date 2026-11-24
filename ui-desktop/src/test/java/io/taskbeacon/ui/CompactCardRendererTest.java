package io.taskbeacon.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CompactCardRendererTest {
    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Already initialized by another test class.
        }
    }

    @Test void listsTasksWithSubagentsAndSummarisesTheRest() throws Exception {
        onFxThread(() -> {
            CompactCardRenderer card = new CompactCardRenderer();
            List<TaskListItem> items = new ArrayList<>();
            items.add(new TaskListItem(model("Claude Code", "one"), false));
            items.add(new TaskListItem(model("Claude Code", "one"), true));
            for (String title : List.of("two", "three", "four", "five", "six", "seven")) {
                items.add(new TaskListItem(model("Codex", title), false));
            }
            card.showTasks(items, 0);
            assertEquals(CompactCardRenderer.MAX_ROWS, card.rowCount());
            assertEquals("+2 more active", card.overflowText(), "two top-level tasks did not fit");

            card.showTasks(items.subList(0, 3), 0);
            assertEquals(3, card.rowCount());
            assertEquals("", card.overflowText());

            card.showTasks(List.of(), 0);
            assertEquals(1, card.rowCount(), "empty list falls back to the idle row");
        });
    }

    private static TaskCardViewModel model(String source, String title) {
        return new TaskCardViewModel("●", source, title, "Thinking…", "00:10", "", "", 1);
    }

    private static void onFxThread(Runnable body) throws Exception {
        FutureTask<Void> task = new FutureTask<>(body, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
