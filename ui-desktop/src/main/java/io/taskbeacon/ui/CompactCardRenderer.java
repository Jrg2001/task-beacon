package io.taskbeacon.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class CompactCardRenderer implements StatusRenderer {
    public static final double CARD_WIDTH = 288;
    /** Rows shown at most (tasks and their subagent lines); the rest is summarised as "+N more". */
    static final int MAX_ROWS = 6;
    /** Transparent margin around the card so its drop shadow is not clipped by the window. */
    private static final double SHADOW_MARGIN = 8;
    private static final String BORDER_NORMAL = "rgba(148,163,184,0.20)";
    private static final String BORDER_ATTENTION = "rgba(251,191,36,0.85)";

    private final VBox rows = new VBox(0);
    private final List<TaskRowView> rowViews = new ArrayList<>();
    private final Label count = new Label("");
    private final Label modeButton = new Label("🐾");
    private final VBox card = new VBox(0, rows);
    private final StackPane shell = new StackPane(card);

    public CompactCardRenderer() {
        count.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        count.setMaxWidth(Double.MAX_VALUE);
        count.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(count, new Insets(2, 0, 0, 0));
        card.getChildren().add(count);

        card.setPadding(new Insets(10, 14, 10, 12));
        card.setMinWidth(CARD_WIDTH);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        applyBorder(false);
        card.setEffect(new DropShadow(14, 0, 4, Color.rgb(2, 6, 23, 0.45)));

        // The mode switch only appears while hovering, so it does not read as a stray mark.
        modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-cursor: hand;");
        modeButton.setOnMouseEntered(e -> modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: #93c5fd; -fx-cursor: hand;"));
        modeButton.setOnMouseExited(e -> modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-cursor: hand;"));
        javafx.scene.control.Tooltip.install(modeButton, new javafx.scene.control.Tooltip("Switch to Desktop Pet"));
        modeButton.setOnMouseClicked(e -> {
            e.consume();
            if (onSwitchMode != null) onSwitchMode.run();
        });
        modeButton.setVisible(false);
        modeButton.setManaged(false);
        StackPane.setAlignment(modeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(modeButton, new Insets(SHADOW_MARGIN + 6, SHADOW_MARGIN + 10, 0, 0));
        shell.getChildren().add(modeButton);
        shell.setOnMouseEntered(e -> modeButton.setVisible(true));
        shell.setOnMouseExited(e -> modeButton.setVisible(false));

        shell.setPadding(new Insets(SHADOW_MARGIN));
        shell.setStyle("-fx-background-color: transparent;");
        idle();
    }

    private Runnable onSwitchMode;
    private Runnable onShowPanel;
    private Runnable onQuit;

    @Override public String id() { return "compact-card"; }
    @Override public String displayName() { return "Compact Card"; }
    @Override public Node create() { return shell; }
    @Override public void update(TaskCardViewModel model) { update(model, null); }

    @Override
    public void update(TaskCardViewModel model, TaskCardViewModel secondaryModel) {
        List<TaskListItem> items = new ArrayList<>();
        items.add(new TaskListItem(model, false));
        if (secondaryModel != null) items.add(new TaskListItem(secondaryModel, false));
        showTasks(items, Math.max(0, model.activeTaskCount() - items.size()));
    }

    /**
     * Shows every task (with its subagent lines) up to {@link #MAX_ROWS} rows.
     *
     * @param extraHidden tasks the caller already left out of {@code items}
     */
    void showTasks(List<TaskListItem> items, int extraHidden) {
        if (items.isEmpty()) {
            idle();
            return;
        }
        List<TaskListItem> shown = items.size() > MAX_ROWS ? items.subList(0, MAX_ROWS) : items;
        int truncated = (int) items.subList(shown.size(), items.size()).stream()
                .filter(item -> !item.subtask()).count();
        List<Node> children = new ArrayList<>();
        for (int index = 0; index < shown.size(); index++) {
            TaskListItem item = shown.get(index);
            TaskRowView row = rowAt(index, item.subtask());
            row.update(item.model());
            if (!item.subtask() && index > 0) children.add(divider());
            children.add(row.node());
        }
        trimRows(shown.size());
        rows.getChildren().setAll(children);
        setOverflow(truncated + extraHidden);
        applyBorder(items.stream().anyMatch(item -> !item.model().badge().isBlank()));
    }

    @Override
    public void idle() {
        TaskRowView row = rowAt(0, false);
        row.idle();
        trimRows(1);
        rows.getChildren().setAll(row.node());
        setOverflow(0);
        applyBorder(false);
    }

    /** Reuses the row at {@code index} when it has the right kind, so its pulse animation keeps running smoothly. */
    private TaskRowView rowAt(int index, boolean subtask) {
        if (index < rowViews.size() && rowViews.get(index).isSubtask() == subtask) return rowViews.get(index);
        TaskRowView row = new TaskRowView(subtask);
        if (index < rowViews.size()) {
            rowViews.get(index).dispose();
            rowViews.set(index, row);
        } else {
            rowViews.add(row);
        }
        return row;
    }

    private void trimRows(int size) {
        while (rowViews.size() > size) rowViews.removeLast().dispose();
    }

    private void setOverflow(int hidden) {
        count.setText(hidden > 0 ? "+" + hidden + " more active" : "");
        count.setManaged(hidden > 0);
        count.setVisible(hidden > 0);
    }

    String overflowText() { return count.isVisible() ? count.getText() : ""; }

    int rowCount() { return rowViews.size(); }

    private static Region divider() {
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(148,163,184,0.14);");
        VBox.setMargin(divider, new Insets(4, 0, 4, 11));
        return divider;
    }

    private void applyBorder(boolean attention) {
        card.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(30,41,59,0.96), rgba(15,23,42,0.96));"
                + "-fx-background-radius: 14; -fx-border-radius: 14; -fx-border-width: 1;"
                + "-fx-border-color: " + (attention ? BORDER_ATTENTION : BORDER_NORMAL) + ";");
    }

    public void setOnSwitchMode(Runnable onSwitchMode) { this.onSwitchMode = onSwitchMode; }
    public void setOnShowPanel(Runnable onShowPanel) { this.onShowPanel = onShowPanel; }
    public void setOnQuit(Runnable onQuit) { this.onQuit = onQuit; }

    public void showContextMenu(double screenX, double screenY) {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem switchItem = new javafx.scene.control.MenuItem("Switch to Desktop Pet");
        switchItem.setOnAction(e -> {
            if (onSwitchMode != null) onSwitchMode.run();
        });

        javafx.scene.control.MenuItem panelItem = new javafx.scene.control.MenuItem("Tasks Overview");
        panelItem.setOnAction(e -> {
            if (onShowPanel != null) onShowPanel.run();
        });

        javafx.scene.control.MenuItem quitItem = new javafx.scene.control.MenuItem("Quit");
        quitItem.setOnAction(e -> {
            if (onQuit != null) onQuit.run();
        });

        contextMenu.getItems().addAll(switchItem, panelItem, quitItem);
        contextMenu.show(shell, screenX, screenY);
    }
}
