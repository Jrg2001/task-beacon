package io.taskbeacon.ui;

import io.taskbeacon.core.model.TaskState;
import io.taskbeacon.core.registry.TaskRegistry;
import io.taskbeacon.core.registry.Subscription;
import io.taskbeacon.core.selection.PrimaryTaskSelector;
import io.taskbeacon.core.selection.SelectionContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;

import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

public final class DesktopController implements AutoCloseable {
    private final TaskRegistry registry;
    private final PrimaryTaskSelector selector;
    private final Clock clock;
    private final java.time.Duration completedTtl;
    private final Supplier<Map<String, String>> sourceHealth;
    private final CompactCardRenderer cardRenderer = new CompactCardRenderer();
    private final PetRenderer petRenderer = new PetRenderer();
    private final StableTaskSlots taskSlots = new StableTaskSlots();
    private StatusRenderer currentRenderer;
    private final TaskViewModelFactory viewModels;
    private final Preferences preferences = Preferences.userNodeForPackage(DesktopController.class);
    private final Timeline refreshTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
    private Stage cardStage;
    private Stage secondaryPetStage;
    private Stage panelStage;
    private ListView<TaskState> taskList;
    private Label healthLabel;
    private Subscription subscription;
    private TrayController tray;

    public DesktopController(TaskRegistry registry, PrimaryTaskSelector selector, Clock clock,
                             java.time.Duration completedTtl,
                             Supplier<Map<String, String>> sourceHealth) {
        this.registry = registry;
        this.selector = selector;
        this.clock = clock;
        this.completedTtl = completedTtl;
        this.sourceHealth = sourceHealth;
        this.viewModels = new TaskViewModelFactory(clock);
        String mode = preferences.get("ui.mode", "compact-card");
        this.currentRenderer = mode.equals("pet") ? petRenderer : cardRenderer;
    }

    public void start(Stage stage) {
        this.cardStage = stage;
        Platform.setImplicitExit(false);
        stage.initStyle(StageStyle.TRANSPARENT);

        cardRenderer.setOnSwitchMode(() -> switchRenderer("pet"));
        cardRenderer.setOnShowPanel(() -> Platform.runLater(this::showPanel));
        cardRenderer.setOnQuit(() -> Platform.runLater(this::quit));

        petRenderer.setOnSwitchMode(() -> switchRenderer("compact-card"));
        petRenderer.setOnShowPanel(() -> Platform.runLater(this::showPanel));
        petRenderer.setOnQuit(() -> Platform.runLater(this::quit));

        Scene scene = new Scene((javafx.scene.Parent) currentRenderer.create());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        installDragging(scene, stage, true, null);
        restoreMainPosition(stage);
        createPanel();
        subscription = registry.subscribe(state -> Platform.runLater(this::refresh));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
        stage.show();
        createSecondaryPetStage();
        tray = new TrayController(this::toggleCard, () -> Platform.runLater(this::showPanel),
                () -> Platform.runLater(this::quit), this::switchRenderer);
        tray.install();
        tray.updateRendererMode(currentRenderer.id());
        refresh();
    }

    public void switchRenderer(String mode) {
        Platform.runLater(() -> {
            StatusRenderer next = mode.equals("pet") ? petRenderer : cardRenderer;
            if (next == currentRenderer) return;
            savePosition(cardStage, mainPositionKey());
            currentRenderer = next;
            preferences.put("ui.mode", currentRenderer.id());
            if (cardStage != null && cardStage.getScene() != null) {
                cardStage.getScene().setRoot((javafx.scene.Parent) currentRenderer.create());
                cardStage.sizeToScene();
                restoreMainPosition(cardStage);
                if (secondaryPetStage != null) {
                    if (currentRenderer == petRenderer && cardStage.isShowing()) {
                        restoreSecondaryPetPosition();
                        secondaryPetStage.show();
                    } else {
                        secondaryPetStage.hide();
                    }
                }
                refresh();
            }
            if (tray != null) {
                tray.updateRendererMode(currentRenderer.id());
            }
        });
    }

    private void createSecondaryPetStage() {
        secondaryPetStage = new Stage(StageStyle.TRANSPARENT);
        Scene scene = new Scene((javafx.scene.Parent) petRenderer.createSecondary());
        scene.setFill(Color.TRANSPARENT);
        secondaryPetStage.setScene(scene);
        secondaryPetStage.setAlwaysOnTop(true);
        installDragging(scene, secondaryPetStage, false, "window.pet.claude");
        secondaryPetStage.sizeToScene();
        restoreSecondaryPetPosition();
        if (currentRenderer == petRenderer && cardStage.isShowing()) secondaryPetStage.show();
    }

    private void installDragging(Scene scene, Stage targetStage, boolean mainStage, String fixedPositionKey) {
        double[] dragOffset = new double[2];
        boolean[] dragged = new boolean[1];
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                dragOffset[0] = event.getScreenX() - targetStage.getX();
                dragOffset[1] = event.getScreenY() - targetStage.getY();
                dragged[0] = false;
            }
        });
        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                dragged[0] = true;
                targetStage.setX(event.getScreenX() - dragOffset[0]);
                targetStage.setY(event.getScreenY() - dragOffset[1]);
            }
        });
        scene.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                String key = fixedPositionKey == null ? mainPositionKey() : fixedPositionKey;
                savePosition(targetStage, key);
            }
        });
        scene.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (!mainStage || dragged[0]) return;
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                if (currentRenderer instanceof CompactCardRenderer card) {
                    card.showContextMenu(event.getScreenX(), event.getScreenY());
                }
            } else if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                if (event.getClickCount() == 2) {
                    switchRenderer(currentRenderer.id().equals("pet") ? "compact-card" : "pet");
                } else if (event.getClickCount() == 1 && currentRenderer.id().equals("compact-card")) {
                    togglePanel();
                }
            }
        });
    }

    private void createPanel() {
        panelStage = new Stage(StageStyle.UTILITY);
        panelStage.setTitle("TaskBeacon tasks");
        taskList = new ListView<>();
        taskList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(TaskState state, boolean empty) {
                super.updateItem(state, empty);
                if (empty || state == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                TaskCardViewModel model = viewModels.create(state, registry.activeTasks().size());
                Label source = new Label(model.source());
                source.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                        + SourcePalette.accent(model.source()) + ";");
                Label detail = new Label(" · " + model.title() + " — "
                        + model.status() + " " + model.elapsed());
                setText(null);
                setGraphic(new HBox(source, detail));
            }
        });
        healthLabel = new Label();
        healthLabel.setWrapText(true);
        BorderPane root = new BorderPane(taskList);
        root.setBottom(healthLabel);
        BorderPane.setMargin(healthLabel, new javafx.geometry.Insets(8));
        panelStage.setScene(new Scene(root, 420, 320));
    }

    private void refresh() {
        try {
            refreshUnsafe();
        } catch (RuntimeException error) {
            // A failure here would freeze every pet in its last pose, so make it visible in the log.
            org.slf4j.LoggerFactory.getLogger(DesktopController.class).warn("UI refresh failed", error);
        }
    }

    private void refreshUnsafe() {
        List<TaskState> tasks = registry.recentTasks();
        List<TaskState> activeTasks = registry.activeTasks();
        int active = activeTasks.size();
        if (currentRenderer == petRenderer) {
            TaskState codex = selectSourceTask(tasks, "codex");
            TaskState claude = selectSourceTask(tasks, "claude");
            int codexActive = countActiveForSource(activeTasks, "codex");
            int claudeActive = countActiveForSource(activeTasks, "claude");
            petRenderer.update(codex == null ? null : viewModels.create(codex, codexActive),
                    claude == null ? null : viewModels.create(claude, claudeActive));
        } else {
            StableTaskSlots.Selection slots = taskSlots.select(activeTasks);
            if (slots.primary() != null) {
                TaskCardViewModel secondary = slots.secondary() == null ? null
                        : viewModels.create(slots.secondary(), active);
                currentRenderer.update(viewModels.create(slots.primary(), active), secondary);
            } else {
                selector.select(tasks, new SelectionContext(clock.instant(), completedTtl, null))
                        .ifPresentOrElse(state -> currentRenderer.update(viewModels.create(state, active), null),
                                () -> currentRenderer.idle());
            }
        }
        cardStage.sizeToScene();
        taskList.setItems(FXCollections.observableArrayList(tasks));
        String health = sourceHealth.get().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce((left, right) -> left + "   " + right).orElse("No sources");
        healthLabel.setText(health);
        if (tray != null) tray.update(active, (int) tasks.stream()
                .filter(task -> task.attention() != io.taskbeacon.core.model.Attention.NONE).count());
    }

    private TaskState selectSourceTask(List<TaskState> tasks, String sourceId) {
        List<TaskState> sourceTasks = tasks.stream()
                .filter(task -> belongsToSource(task, sourceId))
                .toList();
        return selector.select(sourceTasks,
                new SelectionContext(clock.instant(), completedTtl, null)).orElse(null);
    }

    private int countActiveForSource(List<TaskState> activeTasks, String sourceId) {
        return (int) activeTasks.stream().filter(task -> belongsToSource(task, sourceId)).count();
    }

    private boolean belongsToSource(TaskState task, String sourceId) {
        String id = task.key().sourceId().toLowerCase(Locale.ROOT);
        String name = task.sourceDisplayName().toLowerCase(Locale.ROOT);
        return id.contains(sourceId) || name.contains(sourceId);
    }

    private void togglePanel() { if (panelStage.isShowing()) panelStage.hide(); else showPanel(); }
    private void showPanel() { panelStage.show(); panelStage.toFront(); }
    private void toggleCard() {
        Platform.runLater(() -> {
            if (cardStage.isShowing()) {
                cardStage.hide();
                if (secondaryPetStage != null) secondaryPetStage.hide();
            } else {
                cardStage.show();
                if (currentRenderer == petRenderer && secondaryPetStage != null) secondaryPetStage.show();
            }
        });
    }

    private String mainPositionKey() {
        return currentRenderer == petRenderer ? "window.pet.codex" : "window.card";
    }

    private void savePosition(Stage stage, String key) {
        if (stage == null) return;
        preferences.putDouble(key + ".x", stage.getX());
        preferences.putDouble(key + ".y", stage.getY());
    }

    private void restoreMainPosition(Stage stage) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        if (currentRenderer == petRenderer) {
            restorePosition(stage, "window.pet.codex",
                    bounds.getMaxX() - PetRenderer.PET_WIDTH * 2 - 32,
                    bounds.getMinY() + 32, true);
        } else {
            restorePosition(stage, "window.card",
                    bounds.getMaxX() - CompactCardRenderer.CARD_WIDTH - 16,
                    bounds.getMinY() + 32, true);
        }
    }

    private void restoreSecondaryPetPosition() {
        if (secondaryPetStage == null) return;
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double defaultX = Math.min(bounds.getMaxX() - PetRenderer.PET_WIDTH - 16,
                cardStage.getX() + PetRenderer.PET_WIDTH + 16);
        restorePosition(secondaryPetStage, "window.pet.claude", defaultX, cardStage.getY(), false);
    }

    private void restorePosition(Stage stage, String key, double defaultX, double defaultY,
                                 boolean inheritLegacyPosition) {
        double x = preferences.getDouble(key + ".x", Double.NaN);
        double y = preferences.getDouble(key + ".y", Double.NaN);
        if (inheritLegacyPosition) {
            if (Double.isNaN(x)) x = preferences.getDouble("window.x", defaultX);
            if (Double.isNaN(y)) y = preferences.getDouble("window.y", defaultY);
        }
        if (Double.isNaN(x)) x = defaultX;
        if (Double.isNaN(y)) y = defaultY;
        double resolvedX = x;
        double resolvedY = y;
        boolean visible = Screen.getScreens().stream().map(Screen::getVisualBounds)
                .anyMatch(screen -> screen.contains(resolvedX, resolvedY));
        stage.setX(visible ? resolvedX : defaultX);
        stage.setY(visible ? resolvedY : defaultY);
    }

    public void quit() { close(); Platform.exit(); }

    @Override public void close() {
        refreshTimer.stop();
        if (subscription != null) subscription.close();
        if (tray != null) tray.close();
        if (panelStage != null) panelStage.close();
        if (secondaryPetStage != null) secondaryPetStage.close();
        if (cardStage != null) cardStage.close();
    }
}
