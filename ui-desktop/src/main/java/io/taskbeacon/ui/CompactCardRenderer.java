package io.taskbeacon.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public final class CompactCardRenderer implements StatusRenderer {
    public static final double CARD_WIDTH = 288;
    private final TaskRow primary = new TaskRow();
    private final TaskRow secondary = new TaskRow();
    private final Region divider = new Region();
    private final Label count = new Label("");
    private final VBox root = new VBox(8);

    public CompactCardRenderer() {
        divider.setPrefHeight(1);
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(148,163,184,0.18);");
        count.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
        count.setMaxWidth(Double.MAX_VALUE);
        count.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        root.getChildren().addAll(primary.node(), divider, secondary.node(), count);
        root.setPadding(new Insets(12, 14, 12, 14));
        root.setMinWidth(CARD_WIDTH);
        root.setPrefWidth(CARD_WIDTH);
        root.setMaxWidth(CARD_WIDTH);
        root.setStyle("-fx-background-color: rgba(15,23,42,0.94); -fx-background-radius: 16;"
                + "-fx-border-color: rgba(148,163,184,0.22); -fx-border-radius: 16;");
        secondary.hideModeButton();
        hideSecondary();
    }

    private Runnable onSwitchMode;
    private Runnable onShowPanel;
    private Runnable onQuit;

    @Override public String id() { return "compact-card"; }
    @Override public String displayName() { return "Compact Card"; }
    @Override public Node create() { return root; }
    @Override public void update(TaskCardViewModel model) { update(model, null); }

    @Override
    public void update(TaskCardViewModel model, TaskCardViewModel secondaryModel) {
        primary.update(model);
        int additional = Math.max(0, model.activeTaskCount() - (secondaryModel == null ? 1 : 2));
        count.setText(additional > 0 ? "+" + additional + " active" : "");
        count.setManaged(additional > 0);
        count.setVisible(additional > 0);
        if (secondaryModel == null) {
            hideSecondary();
        } else {
            secondary.update(secondaryModel);
            secondary.setVisible(true);
            divider.setManaged(true);
            divider.setVisible(true);
            root.setPrefHeight(additional > 0 ? 148 : 132);
        }
        root.setBorder(new Border(new BorderStroke(
                model.badge().isBlank() ? Color.rgb(148, 163, 184, .22) : Color.rgb(251, 191, 36, .8),
                BorderStrokeStyle.SOLID, new CornerRadii(16), new BorderWidths(1))));
    }

    @Override
    public void idle() {
        primary.idle();
        count.setText("");
        count.setManaged(false);
        count.setVisible(false);
        hideSecondary();
    }

    private void hideSecondary() {
        secondary.setVisible(false);
        divider.setManaged(false);
        divider.setVisible(false);
        root.setPrefHeight(76);
    }

    public void setOnSwitchMode(Runnable onSwitchMode) {
        this.onSwitchMode = onSwitchMode;
        primary.setOnSwitchMode(onSwitchMode);
    }

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
        contextMenu.show(root, screenX, screenY);
    }

    private static final class TaskRow {
        private final Label icon = new Label("\u25cf");
        private final Label source = new Label("TaskBeacon");
        private final Label title = new Label("Ready");
        private final Label status = new Label("Waiting for tasks");
        private final Label modeButton = new Label("🐾");
        private final HBox heading = new HBox(7, source, title, modeButton);
        private final VBox text = new VBox(2, heading, status);
        private final HBox node = new HBox(12, icon, text);
        private Runnable onSwitchMode;

        private TaskRow() {
            icon.setStyle("-fx-font-size: 18px; -fx-text-fill: #72a7ff;");
            source.setStyle("-fx-font-size: 11px; -fx-text-fill: #72a7ff; -fx-font-weight: bold;");
            title.setStyle("-fx-font-size: 13px; -fx-text-fill: white; -fx-font-weight: bold;");
            status.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);

            modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(148, 163, 184, 0.45); -fx-cursor: hand;");
            modeButton.setOnMouseEntered(e -> modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: #93c5fd; -fx-cursor: hand;"));
            modeButton.setOnMouseExited(e -> modeButton.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(148, 163, 184, 0.45); -fx-cursor: hand;"));
            javafx.scene.control.Tooltip.install(modeButton, new javafx.scene.control.Tooltip("Switch to Desktop Pet"));
            modeButton.setOnMouseClicked(e -> {
                e.consume();
                if (onSwitchMode != null) onSwitchMode.run();
            });
        }

        private void setOnSwitchMode(Runnable onSwitchMode) { this.onSwitchMode = onSwitchMode; }
        private void hideModeButton() { modeButton.setVisible(false); modeButton.setManaged(false); }

        private Node node() { return node; }

        private void update(TaskCardViewModel model) {
            icon.setText(model.badge().isBlank() ? model.icon() : model.badge());
            source.setText(model.source());
            applySourceAccent(model.source());
            title.setText(model.title());
            status.setText(model.status() + " · " + model.elapsed());
        }

        private void idle() {
            icon.setText("\u25cf");
            source.setText("TaskBeacon");
            applySourceAccent("TaskBeacon");
            title.setText("Ready");
            status.setText("Waiting for tasks");
        }

        private void applySourceAccent(String sourceName) {
            String accent = SourcePalette.accent(sourceName);
            icon.setStyle("-fx-font-size: 18px; -fx-text-fill: " + accent + ";");
            source.setStyle("-fx-font-size: 11px; -fx-text-fill: " + accent
                    + "; -fx-font-weight: bold;");
        }

        private void setVisible(boolean visible) {
            node.setManaged(visible);
            node.setVisible(visible);
        }
    }
}
