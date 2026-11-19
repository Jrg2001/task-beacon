package io.taskbeacon.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Locale;

public final class CompactCardRenderer implements StatusRenderer {
    public static final double CARD_WIDTH = 288;
    /** Transparent margin around the card so its drop shadow is not clipped by the window. */
    private static final double SHADOW_MARGIN = 8;
    private static final String BORDER_NORMAL = "rgba(148,163,184,0.20)";
    private static final String BORDER_ATTENTION = "rgba(251,191,36,0.85)";

    private final TaskRow primary = new TaskRow();
    private final TaskRow secondary = new TaskRow();
    private final Region divider = new Region();
    private final Label count = new Label("");
    private final Label modeButton = new Label("🐾");
    private final VBox card = new VBox(0);
    private final StackPane shell = new StackPane(card);

    public CompactCardRenderer() {
        divider.setPrefHeight(1);
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(148,163,184,0.14);");
        VBox.setMargin(divider, new Insets(4, 0, 4, 11));

        count.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        count.setMaxWidth(Double.MAX_VALUE);
        count.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(count, new Insets(2, 0, 0, 0));

        card.getChildren().addAll(primary.node(), divider, secondary.node(), count);
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
        hideSecondary();
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
        primary.update(model);
        int additional = Math.max(0, model.activeTaskCount() - (secondaryModel == null ? 1 : 2));
        count.setText(additional > 0 ? "+" + additional + " more active" : "");
        count.setManaged(additional > 0);
        count.setVisible(additional > 0);
        if (secondaryModel == null) {
            hideSecondary();
        } else {
            secondary.update(secondaryModel);
            secondary.setVisible(true);
            divider.setManaged(true);
            divider.setVisible(true);
        }
        applyBorder(!model.badge().isBlank());
    }

    @Override
    public void idle() {
        primary.idle();
        count.setText("");
        count.setManaged(false);
        count.setVisible(false);
        hideSecondary();
        applyBorder(false);
    }

    private void hideSecondary() {
        secondary.setVisible(false);
        divider.setManaged(false);
        divider.setVisible(false);
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

    /** Visual state of a task, derived from the same status wording the pets react to. */
    enum CardState {
        RUNNING("#22c55e", true), THINKING("#60a5fa", true), ATTENTION("#fbbf24", false),
        STALLED("#fbbf24", false), DONE("#22c55e", false), ENDED("#64748b", false), IDLE("#64748b", false);

        final String color;
        final boolean pulsing;

        CardState(String color, boolean pulsing) {
            this.color = color;
            this.pulsing = pulsing;
        }

        static CardState of(TaskCardViewModel model) {
            String status = model.status() == null ? "" : model.status().toLowerCase(Locale.ROOT);
            if (status.contains("failed") || status.contains("error") || status.contains("required")) return ATTENTION;
            if (status.contains("stalled")) return STALLED;
            if (status.contains("no recent activity")) return IDLE;
            if (status.contains("completed") || status.contains("succeeded")) return DONE;
            if (status.contains("cancelled")) return ENDED;
            if (!model.badge().isBlank()) return ATTENTION;
            if (status.contains("thinking")) return THINKING;
            if (status.contains("detected")) return IDLE;
            return RUNNING;
        }
    }

    private static final class TaskRow {
        /** Idle card belongs to no tool, so it must not borrow the Codex blue. */
        private static final String NEUTRAL = "#94a3b8";
        private final Region accent = new Region();
        private final Circle halo = new Circle(6);
        private final Circle dot = new Circle(4);
        private final Label glyph = new Label();
        private final StackPane indicator = new StackPane(halo, dot, glyph);
        private final Label title = new Label("Ready");
        private final Label elapsed = new Label("");
        private final Label source = new Label("TaskBeacon");
        private final Label status = new Label("Waiting for tasks");
        private final Region spacer = new Region();
        private final HBox titleLine = new HBox(8, indicator, title, spacer, elapsed);
        private final HBox detailLine = new HBox(7, source, status);
        private final VBox text = new VBox(4, titleLine, detailLine);
        private final HBox node = new HBox(10, accent, text);
        private final ParallelTransition pulse;

        private TaskRow() {
            accent.setMinWidth(3);
            accent.setPrefWidth(3);
            accent.setMaxWidth(3);

            indicator.setMinSize(14, 14);
            indicator.setPrefSize(14, 14);
            halo.setOpacity(0);
            glyph.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

            title.setStyle("-fx-font-size: 13px; -fx-text-fill: #f8fafc; -fx-font-weight: bold;");
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            title.setMinWidth(0);
            elapsed.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;"
                    + "-fx-font-family: 'Cascadia Mono', 'Consolas', monospace;");
            elapsed.setMinWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            titleLine.setAlignment(Pos.CENTER_LEFT);

            status.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
            status.setTextOverrun(OverrunStyle.ELLIPSIS);
            status.setMinWidth(0);
            source.setMinWidth(Region.USE_PREF_SIZE);
            detailLine.setAlignment(Pos.CENTER_LEFT);
            detailLine.setPadding(new Insets(0, 0, 0, 22)); // align with the title, past the indicator

            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            node.setPadding(new Insets(4, 0, 4, 0));

            ScaleTransition grow = new ScaleTransition(Duration.millis(1400), halo);
            grow.setFromX(0.7); grow.setFromY(0.7); grow.setToX(1.6); grow.setToY(1.6);
            FadeTransition fade = new FadeTransition(Duration.millis(1400), halo);
            fade.setFromValue(0.55); fade.setToValue(0);
            pulse = new ParallelTransition(grow, fade);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setInterpolator(Interpolator.EASE_OUT);
        }

        private Node node() { return node; }

        private void update(TaskCardViewModel model) {
            CardState state = CardState.of(model);
            applySource(model.source());
            applyState(state);
            title.setText(model.title());
            status.setText(model.status());
            elapsed.setText(model.elapsed());
        }

        private void idle() {
            applySource("TaskBeacon", NEUTRAL);
            applyState(CardState.IDLE);
            title.setText("Ready");
            status.setText("Waiting for tasks");
            elapsed.setText("");
        }

        private void applySource(String sourceName) {
            applySource(sourceName, SourcePalette.accent(sourceName));
        }

        private void applySource(String sourceName, String color) {
            accent.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
            source.setText(sourceName);
            source.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + color + ";"
                    + "-fx-background-color: " + withAlpha(color, 0.16) + "; -fx-background-radius: 6;"
                    + "-fx-padding: 1 6 1 6;");
        }

        private void applyState(CardState state) {
            Color color = Color.web(state.color);
            String glyphText = switch (state) {
                case DONE -> "✓";
                case ATTENTION, STALLED -> "!";
                default -> "";
            };
            boolean useGlyph = !glyphText.isEmpty();
            glyph.setText(glyphText);
            glyph.setVisible(useGlyph);
            glyph.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + state.color + ";");
            dot.setVisible(!useGlyph);
            dot.setFill(color);
            halo.setFill(color);
            if (state.pulsing) {
                if (pulse.getStatus() != Animation.Status.RUNNING) pulse.play();
            } else {
                pulse.stop();
                halo.setOpacity(0);
            }
        }

        private void setVisible(boolean visible) {
            node.setManaged(visible);
            node.setVisible(visible);
        }

        private static String withAlpha(String hex, double alpha) {
            Color c = Color.web(hex);
            return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                    Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
        }
    }
}
