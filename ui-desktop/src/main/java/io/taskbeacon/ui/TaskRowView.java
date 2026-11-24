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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Locale;

/**
 * One task row, shared by the compact card and the pet task popover. A top-level row shows a source accent bar,
 * a state indicator, the title with elapsed time and a source tag with the status. A subtask row (Claude subagent)
 * is a single indented line under its session.
 */
final class TaskRowView {
    /** Idle rows belong to no tool, so they must not borrow the Codex blue. */
    static final String NEUTRAL = "#94a3b8";
    /** Left edge of the title text, used to indent subtask rows under it. */
    private static final double TITLE_INDENT = 22;

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

    private final boolean subtask;
    private final Region accent = new Region();
    private final Circle halo = new Circle(6);
    private final Circle dot = new Circle(4);
    private final Label glyph = new Label();
    private final StackPane indicator = new StackPane(halo, dot, glyph);
    private final Label title = new Label("Ready");
    private final Label elapsed = new Label("");
    private final Label source = new Label("TaskBeacon");
    private final Label status = new Label("Waiting for tasks");
    private final HBox node;
    private final ParallelTransition pulse;

    TaskRowView() { this(false); }

    TaskRowView(boolean subtask) { this(subtask, true); }

    /** @param showSource false where the surrounding list already names the tool (pet popover). */
    TaskRowView(boolean subtask, boolean showSource) {
        this.subtask = subtask;
        source.setVisible(showSource);
        source.setManaged(showSource);
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
        status.setStyle("-fx-font-size: " + (subtask ? 11 : 12) + "px; -fx-text-fill: "
                + (subtask ? "#94a3b8" : "#cbd5e1") + ";");
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.setMinWidth(0);
        source.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (subtask) {
            Label branch = new Label("↳");
            branch.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            indicator.setScaleX(0.75);
            indicator.setScaleY(0.75);
            node = new HBox(6, branch, indicator, status, spacer, elapsed);
            node.setAlignment(Pos.CENTER_LEFT);
            node.setPadding(new Insets(1, 0, 1, 13 + TITLE_INDENT - 14));
        } else {
            accent.setMinWidth(3);
            accent.setPrefWidth(3);
            accent.setMaxWidth(3);
            HBox titleLine = new HBox(8, indicator, title, spacer, elapsed);
            titleLine.setAlignment(Pos.CENTER_LEFT);
            HBox detailLine = new HBox(7, source, status);
            detailLine.setAlignment(Pos.CENTER_LEFT);
            detailLine.setPadding(new Insets(0, 0, 0, TITLE_INDENT)); // align with the title, past the indicator
            VBox text = new VBox(4, titleLine, detailLine);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            node = new HBox(10, accent, text);
            node.setPadding(new Insets(4, 0, 4, 0));
        }

        ScaleTransition grow = new ScaleTransition(Duration.millis(1400), halo);
        grow.setFromX(0.7); grow.setFromY(0.7); grow.setToX(1.6); grow.setToY(1.6);
        FadeTransition fade = new FadeTransition(Duration.millis(1400), halo);
        fade.setFromValue(0.55); fade.setToValue(0);
        pulse = new ParallelTransition(grow, fade);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_OUT);
    }

    Node node() { return node; }

    boolean isSubtask() { return subtask; }

    void update(TaskCardViewModel model) {
        applySource(model.source(), SourcePalette.accent(model.source()));
        applyState(CardState.of(model));
        title.setText(model.title());
        status.setText(model.status());
        elapsed.setText(model.elapsed());
    }

    void idle() {
        applySource("TaskBeacon", NEUTRAL);
        applyState(CardState.IDLE);
        title.setText("Ready");
        status.setText("Waiting for tasks");
        elapsed.setText("");
    }

    /** Stops the indicator animation; call when the row is discarded. */
    void dispose() { pulse.stop(); }

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

    static String withAlpha(String hex, double alpha) {
        Color c = Color.web(hex);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
    }
}
