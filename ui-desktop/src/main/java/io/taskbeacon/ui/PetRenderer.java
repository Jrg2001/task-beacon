package io.taskbeacon.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PetRenderer implements StatusRenderer {
    public static final double PET_WIDTH = 140;
    private static final double POPOVER_WIDTH = 270;
    /** Gap between the pet's head and the popover. */
    private static final double POPOVER_GAP = 4;

    /**
     * What one pet should show.
     *
     * @param model      the task shown by the pet, or null when idle
     * @param borrowed   the pet is lent to the other tool and shows one of its tasks
     * @param listSource display name of the tool whose tasks the popover lists
     * @param tasks      all tasks of that tool, with subagent lines, for the popover
     */
    record PetDisplay(TaskCardViewModel model, boolean borrowed, String listSource, List<TaskListItem> tasks) {
        public PetDisplay {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }
    private static final double DEFAULT_PET_SCALE = 1.0;
    private static final Logger log = LoggerFactory.getLogger(PetRenderer.class);

    private final VBox root = new VBox(0);
    private final VBox secondaryRoot = new VBox(0);
    private final Preferences preferences;
    private final PetSlot codexPet = new PetSlot("codex", "Codex");
    private final PetSlot claudePet = new PetSlot("claude", "Claude Code");

    private Runnable onSwitchMode;
    private Runnable onShowPanel;
    private Runnable onQuit;

    public PetRenderer() {
        this(Preferences.userNodeForPackage(PetRenderer.class));
    }

    PetRenderer(Preferences preferences) {
        this.preferences = java.util.Objects.requireNonNull(preferences);
        codexPet.scale = preferences.getDouble("pet.codex.scale", DEFAULT_PET_SCALE);
        claudePet.scale = preferences.getDouble("pet.claude.scale", DEFAULT_PET_SCALE);
        codexPet.controller.setCustomScale(codexPet.scale);
        claudePet.controller.setCustomScale(claudePet.scale);
        buildLayout();
        setupInteractions();
        loadSavedPets();
        updateDimensions();
        codexPet.manualActionTimer.setOnFinished(event -> restoreLatestState(codexPet));
        claudePet.manualActionTimer.setOnFinished(event -> restoreLatestState(claudePet));
    }

    private void loadSavedPets() {
        loadSavedPet(codexPet);
        loadSavedPet(claudePet);
    }

    private void loadSavedPet(PetSlot pet) {
        String packId = preferences.get("pet." + pet.id + ".pack", null);
        if (packId != null && BundledPetPacks.find(packId).map(pack -> loadPack(pet, pack)).orElse(false)) {
            return;
        }
        String customPath = preferences.get("pet." + pet.id + ".customImage", null);
        if (loadSavedCustomImage(pet, customPath)) return;
        if (pet == codexPet && loadSavedCustomImage(pet, preferences.get("pet.customImage", null))) return;
        BundledPetPacks.find(BundledPetPacks.DEFAULT_ID).ifPresent(pack -> loadPack(pet, pack));
    }

    private boolean loadSavedCustomImage(PetSlot pet, String path) {
        try {
            if (path != null && !path.isBlank()) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    if (pet.controller.setCustomImage(f)) {
                        pet.packId = null;
                        return true;
                    }
                }
            }
        } catch (RuntimeException error) {
            log.warn("Could not load saved {} pet", pet.id, error);
        }
        return false;
    }

    private boolean loadPack(PetSlot pet, BundledPetPacks.Pack pack) {
        try {
            Path idleImage = BundledPetPacks.install(pack, resolveSkinsDir());
            if (!pet.controller.setCustomImage(idleImage.toFile(), pack.sizeFactor())) return false;
            pet.packId = pack.id();
            return true;
        } catch (Exception error) {
            log.warn("Could not load bundled {} pet {}", pet.id, pack.id(), error);
            return false;
        }
    }

    private void selectPack(PetSlot pet, BundledPetPacks.Pack pack) {
        if (!loadPack(pet, pack)) return;
        preferences.put("pet." + pet.id + ".pack", pack.id());
        preferences.remove("pet." + pet.id + ".customImage");
        if (pet == codexPet) preferences.remove("pet.customImage");
        restoreLatestState(pet);
        updateDimensions();
    }

    public void setCustomScale(double scale) {
        setCustomScale(codexPet, scale);
    }

    public void setCustomScale(String source, double scale) {
        setCustomScale(slotForSource(source), scale);
    }

    private void setCustomScale(PetSlot pet, double scale) {
        pet.scale = scale;
        preferences.putDouble("pet." + pet.id + ".scale", scale);
        pet.controller.setCustomScale(scale);
        updateDimensions();
    }

    public double getCustomScale() {
        return codexPet.scale;
    }

    public double getCustomScale(String source) {
        return slotForSource(source).scale;
    }

    private void updateDimensions() {
        resizeRoot(root, petWidth(codexPet));
        resizeRoot(secondaryRoot, petWidth(claudePet));
    }

    private void buildLayout() {
        configureRoot(root, codexPet);
        configureRoot(secondaryRoot, claudePet);
    }

    private void configureRoot(VBox petRoot, PetSlot pet) {
        petRoot.setAlignment(Pos.BOTTOM_CENTER);
        petRoot.setPadding(new Insets(2, 4, 2, 4));
        petRoot.setStyle("-fx-background-color: transparent;");
        petRoot.getChildren().add(pet.controller.node());
    }

    private void resizeRoot(VBox petRoot, double width) {
        petRoot.setMinWidth(width);
        petRoot.setPrefWidth(width);
        Window window = petRoot.getScene() != null ? petRoot.getScene().getWindow() : null;
        if (window instanceof Stage stage) stage.sizeToScene();
    }

    private void setupInteractions() {
        setupPetInteraction(codexPet);
        setupPetInteraction(claudePet);
    }

    private void setupPetInteraction(PetSlot pet) {
        Node petNode = pet.controller.node();
        // A single click toggles a list of every task of the tool the pet represents. The click waits briefly so
        // a double click (switch to the card) does not flash the list; a drag never counts as a click.
        pet.clickDelay.setOnFinished(e -> showPopover(pet));
        pet.popover.setOnHidden(e -> pet.popoverHiddenAt = System.nanoTime());
        petNode.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            pet.popoverOpenAtPress = pet.popover.isShowing() || recentlyHidden(pet);
            pet.popover.hide();
        });
        petNode.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                pet.clickDelay.stop();
                pet.popover.hide();
                if (onSwitchMode != null) onSwitchMode.run();
                e.consume();
            } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                // Clicking while the list is open closes it (the press already hid it); otherwise open it.
                if (e.isStillSincePress() && !pet.popoverOpenAtPress) pet.clickDelay.playFromStart();
                e.consume();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                pet.clickDelay.stop();
                pet.popover.hide();
                showContextMenu(pet, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
    }

    private void showContextMenu(PetSlot pet, double screenX, double screenY) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem petTitle = new MenuItem(pet.displayName + " \u5ba0\u7269 / " + pet.displayName + " Pet");
        petTitle.setDisable(true);

        MenuItem switchItem = new MenuItem("\u5207\u6362\u5230\u7d27\u51d1\u5361\u7247 / Switch to Compact Card");
        switchItem.setOnAction(e -> {
            if (onSwitchMode != null) onSwitchMode.run();
        });

        Menu actionMenu = new Menu("\u64ad\u653e\u52a8\u4f5c / Play Action");
        actionMenu.getItems().addAll(
                actionItem(pet, "\u5f85\u673a / Idle", PetAnimationController.ManualAction.IDLE),
                actionItem(pet, "\u4f38\u61d2\u8170 / Stretch", PetAnimationController.ManualAction.STRETCH),
                actionItem(pet, "\u6563\u6b65 / Walk", PetAnimationController.ManualAction.WALK),
                actionItem(pet, "\u5954\u8dd1 / Run", PetAnimationController.ManualAction.RUN),
                actionItem(pet, "\u5420\u53eb / Bark", PetAnimationController.ManualAction.BARK),
                actionItem(pet, "\u6320\u75d2 / Itch", PetAnimationController.ManualAction.ITCH),
                actionItem(pet, "\u8214\u6bdb / Lick", PetAnimationController.ManualAction.LICK),
                actionItem(pet, "\u7761\u89c9 / Sleep", PetAnimationController.ManualAction.SLEEP),
                new SeparatorMenuItem(),
                restoreActionItem(pet)
        );

        Menu builtInMenu = new Menu("\u5185\u7f6e\u5ba0\u7269 / Built-in Pets");
        ToggleGroup packGroup = new ToggleGroup();
        builtInMenu.getItems().addAll(
                packMenu(pet, "\u72d7 / Dogs", BundledPetPacks.DOGS, packGroup),
                packMenu(pet, "\u732b / Cats", BundledPetPacks.CATS, packGroup)
        );

        MenuItem selectImageItem = new MenuItem("\u9009\u62e9\u81ea\u5b9a\u4e49\u56fe\u7247 / Choose Custom Image...");
        selectImageItem.setOnAction(e -> chooseCustomImage(pet));

        Menu sizeMenu = new Menu("\u5ba0\u7269\u5927\u5c0f / Pet Size");
        ToggleGroup sizeGroup = new ToggleGroup();
        double[] scales = {0.8, 1.0, 1.25, 1.5, 1.75, 2.0};
        String[] labels = {"80% (\u5c0f / Small)", "100% (\u9ed8\u8ba4 / Default)", "125% (\u4e2d / Medium)", "150% (\u5927 / Large)", "175% (\u7279\u5927 / Extra Large)", "200% (\u5de8\u5927 / Giant)"};

        for (int i = 0; i < scales.length; i++) {
            double s = scales[i];
            RadioMenuItem item = new RadioMenuItem(labels[i]);
            item.setToggleGroup(sizeGroup);
            if (Math.abs(pet.scale - s) < 0.05) {
                item.setSelected(true);
            }
            item.setOnAction(e -> setCustomScale(pet, s));
            sizeMenu.getItems().add(item);
        }
        sizeMenu.setDisable(!pet.controller.hasCustomImage());

        MenuItem resetImageItem = new MenuItem("\u6062\u590d\u9ed8\u8ba4\u79cb\u7530\u72ac / Reset to Default Akita");
        resetImageItem.setDisable(BundledPetPacks.DEFAULT_ID.equals(pet.packId));
        resetImageItem.setOnAction(e -> BundledPetPacks.find(BundledPetPacks.DEFAULT_ID)
                .ifPresent(pack -> selectPack(pet, pack)));

        MenuItem panelItem = new MenuItem("\u4efb\u52a1\u603b\u89c8 / Tasks Overview");
        panelItem.setOnAction(e -> {
            if (onShowPanel != null) onShowPanel.run();
        });

        MenuItem quitItem = new MenuItem("\u9000\u51fa / Quit");
        quitItem.setOnAction(e -> {
            if (onQuit != null) onQuit.run();
        });

        contextMenu.getItems().addAll(
                petTitle,
                new SeparatorMenuItem(),
                switchItem,
                actionMenu,
                new SeparatorMenuItem(),
                builtInMenu,
                selectImageItem,
                sizeMenu,
                resetImageItem,
                new SeparatorMenuItem(),
                panelItem,
                quitItem
        );
        contextMenu.show(pet.controller.node(), screenX, screenY);
    }

    private Menu packMenu(PetSlot pet, String title, java.util.List<BundledPetPacks.Pack> packs,
                          ToggleGroup group) {
        Menu menu = new Menu(title);
        for (BundledPetPacks.Pack pack : packs) {
            RadioMenuItem item = new RadioMenuItem(pack.menuLabel());
            item.setToggleGroup(group);
            item.setSelected(pack.id().equals(pet.packId));
            item.setOnAction(event -> selectPack(pet, pack));
            menu.getItems().add(item);
        }
        return menu;
    }

    private MenuItem actionItem(PetSlot pet, String label, PetAnimationController.ManualAction action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> playManualAction(pet, action));
        return item;
    }

    private MenuItem restoreActionItem(PetSlot pet) {
        MenuItem item = new MenuItem("\u6062\u590d\u5f53\u524d\u72b6\u6001 / Resume Current State");
        item.setOnAction(event -> restoreLatestState(pet));
        return item;
    }

    void playManualAction(PetAnimationController.ManualAction action) {
        playManualAction(codexPet, action);
    }

    private void playManualAction(PetSlot pet, PetAnimationController.ManualAction action) {
        pet.manualActionActive = true;
        pet.controller.playManualAction(action);
        pet.manualActionTimer.playFromStart();
    }

    private void restoreLatestState(PetSlot pet) {
        pet.manualActionTimer.stop();
        pet.manualActionActive = false;
        pet.controller.reapplyState(pet.latestModel);
    }

    private void chooseCustomImage(PetSlot pet) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select " + pet.displayName + " Pet Image or GIF");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.gif, *.jpg, *.jpeg, *.webp)", "*.png", "*.gif", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        Window window = pet.controller.node().getScene() != null
                ? pet.controller.node().getScene().getWindow() : null;
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) return;

        File actualFile = resolveSelectedFile(selectedFile);
        if (actualFile != null && actualFile.isFile()) {
            boolean ok = pet.controller.setCustomImage(actualFile);
            if (ok) {
                pet.packId = null;
                preferences.put("pet." + pet.id + ".customImage", actualFile.getAbsolutePath());
                preferences.remove("pet." + pet.id + ".pack");
                if (pet == codexPet) preferences.remove("pet.customImage");
                restoreLatestState(pet);
                updateDimensions();
            }
        }
    }

    private double petWidth(PetSlot pet) {
        return Math.max(PET_WIDTH, pet.controller.displayWidth() + 8);
    }

    private PetSlot slotForSource(String source) {
        return source != null && source.toLowerCase().contains("claude") ? claudePet : codexPet;
    }

    private File resolveSelectedFile(File selectedFile) {
        if (selectedFile == null) return null;
        String fullPath = selectedFile.getAbsolutePath();

        // Check if user browsed inside a .zip file in Windows Explorer
        int zipIdx = fullPath.toLowerCase().indexOf(".zip\\");
        if (zipIdx < 0) {
            zipIdx = fullPath.toLowerCase().indexOf(".zip/");
        }

        if (zipIdx >= 0) {
            String zipPath = fullPath.substring(0, zipIdx + 4);
            String entrySubPath = fullPath.substring(zipIdx + 5).replace('\\', '/');
            File zipFile = new File(zipPath);
            if (zipFile.exists() && zipFile.isFile()) {
                try {
                    Path skinsDir = resolveSkinsDir();
                    String parentName = new File(entrySubPath).getParent();
                    if (parentName == null || parentName.isBlank()) {
                        parentName = "extracted";
                    } else {
                        parentName = parentName.replace('/', '_').replace('\\', '_');
                    }
                    Path targetDir = skinsDir.resolve(parentName);
                    Files.createDirectories(targetDir);

                    String entryFolder = "";
                    int lastSlash = entrySubPath.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        entryFolder = entrySubPath.substring(0, lastSlash + 1);
                    }

                    File primaryExtracted = null;
                    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            java.util.zip.ZipEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (entryFolder.isEmpty() || name.startsWith(entryFolder)) {
                                String relativeName = entryFolder.isEmpty() ? name : name.substring(entryFolder.length());
                                if (!relativeName.contains("/") && !relativeName.isBlank() && !entry.isDirectory()) {
                                    Path outPath = targetDir.resolve(relativeName);
                                    try (java.io.InputStream is = zip.getInputStream(entry)) {
                                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                    if (name.equals(entrySubPath)) {
                                        primaryExtracted = outPath.toFile();
                                    }
                                }
                            }
                        }
                    }
                    if (primaryExtracted != null && primaryExtracted.exists()) {
                        return primaryExtracted;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (selectedFile.exists() && selectedFile.isFile()) {
            try {
                Path skinsDir = resolveSkinsDir();
                File parentDir = selectedFile.getParentFile();
                if (parentDir != null && parentDir.isDirectory()) {
                    String folderName = parentDir.getName();
                    Path targetDir = skinsDir.resolve(folderName);
                    Files.createDirectories(targetDir);

                    File[] siblings = parentDir.listFiles((d, name) -> {
                        String n = name.toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
                    });
                    if (siblings != null) {
                        for (File s : siblings) {
                            Files.copy(s.toPath(), targetDir.resolve(s.getName()), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    Path destPrimary = targetDir.resolve(selectedFile.getName());
                    if (Files.exists(destPrimary)) {
                        return destPrimary.toFile();
                    }
                }
                Path destFile = skinsDir.resolve("custom_pet_" + selectedFile.getName());
                Files.copy(selectedFile.toPath(), destFile, StandardCopyOption.REPLACE_EXISTING);
                return destFile.toFile();
            } catch (Exception e) {
                e.printStackTrace();
                return selectedFile;
            }
        }
        return null;
    }

    private Path resolveSkinsDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "TaskBeacon", "skins");
        }
        return Path.of(System.getProperty("user.home"), ".taskbeacon", "skins");
    }

    @Override public String id() { return "pet"; }
    @Override public String displayName() { return "Desktop Pet"; }
    @Override public Node create() { return root; }
    public Node createSecondary() { return secondaryRoot; }

    @Override
    public void update(TaskCardViewModel primaryModel, TaskCardViewModel secondaryModel) {
        TaskCardViewModel codex = modelForSource("codex", primaryModel, secondaryModel);
        TaskCardViewModel claude = modelForSource("claude", primaryModel, secondaryModel);
        updatePets(simpleDisplay(codex, codexPet), simpleDisplay(claude, claudePet));
    }

    private static PetDisplay simpleDisplay(TaskCardViewModel model, PetSlot pet) {
        return new PetDisplay(model, false, pet.displayName,
                model == null ? List.of() : List.of(new TaskListItem(model, false)));
    }

    /** Applies the pet assignment: each pet's task, lending state and popover list. */
    public void updatePets(PetDisplay codex, PetDisplay claude) {
        applyDisplay(codexPet, codex);
        applyDisplay(claudePet, claude);
    }

    private void applyDisplay(PetSlot pet, PetDisplay display) {
        updatePet(pet, display.model());
        if (display.borrowed() != pet.borrowed) {
            log.info("Pet {} {}", pet.id, display.borrowed()
                    ? "lent to " + display.listSource() + ": " + (display.model() == null ? "" : display.model().title())
                    : "returned to " + pet.displayName);
        }
        pet.display = display;
        pet.borrowed = display.borrowed();
        if (pet.popover.isShowing()) fillPopover(pet);
    }

    /** The auto-hide may close the list just before our press handler runs; treat that as "was open". */
    private static boolean recentlyHidden(PetSlot pet) {
        return System.nanoTime() - pet.popoverHiddenAt < 300_000_000L;
    }

    private void showPopover(PetSlot pet) {
        Node petNode = pet.controller.node();
        if (petNode.getScene() == null || petNode.getScene().getWindow() == null) return;
        fillPopover(pet);
        pet.popover.show(petNode.getScene().getWindow());
        positionPopover(pet);
    }

    private void fillPopover(PetSlot pet) {
        PetDisplay display = pet.display;
        List<Node> children = new ArrayList<>();
        String source = display == null ? pet.displayName : display.listSource();
        List<TaskListItem> tasks = display == null ? List.of() : display.tasks();
        long count = tasks.stream().filter(item -> !item.subtask()).count();
        Label header = new Label(source + (count > 0 ? "  ·  " + count + " active" : ""));
        header.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + SourcePalette.accent(source) + ";");
        VBox.setMargin(header, new Insets(0, 0, 4, 0));
        children.add(header);
        if (tasks.isEmpty()) {
            Label empty = new Label("No active tasks");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
            children.add(empty);
        }
        for (int index = 0; index < tasks.size(); index++) {
            TaskListItem item = tasks.get(index);
            TaskRowView row = popoverRow(pet, index, item.subtask());
            row.update(item.model());
            children.add(row.node());
        }
        while (pet.popoverRows.size() > tasks.size()) pet.popoverRows.removeLast().dispose();
        if (display != null && display.borrowed()) {
            Label note = new Label("Lent by the " + pet.displayName + " pet while it has no tasks");
            note.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
            note.setWrapText(true);
            VBox.setMargin(note, new Insets(4, 0, 0, 0));
            children.add(note);
        }
        pet.popoverContent.getChildren().setAll(children);
        if (pet.popover.isShowing()) positionPopover(pet);
    }

    private static TaskRowView popoverRow(PetSlot pet, int index, boolean subtask) {
        if (index < pet.popoverRows.size() && pet.popoverRows.get(index).isSubtask() == subtask) {
            return pet.popoverRows.get(index);
        }
        TaskRowView row = new TaskRowView(subtask, false);
        if (index < pet.popoverRows.size()) pet.popoverRows.set(index, row).dispose();
        else pet.popoverRows.add(row);
        return row;
    }

    /**
     * Just above the pet's head when there is room, otherwise below it; kept on the pet's screen.
     * The popover is anchored by its content edge, so its real height (and drop-shadow margin) cannot push it
     * down over the pet - which would steal the hover and close it immediately.
     */
    private static void positionPopover(PetSlot pet) {
        Bounds bounds = pet.controller.visibleScreenBounds();
        if (bounds == null) return;
        pet.popoverContent.applyCss();
        pet.popoverContent.layout();
        double height = pet.popoverContent.prefHeight(POPOVER_WIDTH);
        Rectangle2D screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double x = bounds.getMinX() + bounds.getWidth() / 2 - POPOVER_WIDTH / 2;
        x = Math.max(screen.getMinX() + 4, Math.min(x, screen.getMaxX() - POPOVER_WIDTH - 4));
        boolean above = bounds.getMinY() - POPOVER_GAP - height - 16 >= screen.getMinY() + 4;
        pet.popover.setAnchorLocation(above ? PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT
                : PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
        // The window also covers the drop shadow around the content; keep that clear of the pet as well.
        Bounds visual = pet.popoverContent.getBoundsInLocal();
        Bounds layout = pet.popoverContent.getLayoutBounds();
        double shadowBelow = Math.max(0, visual.getMaxY() - layout.getMaxY());
        double shadowAbove = Math.max(0, layout.getMinY() - visual.getMinY());
        pet.popover.setAnchorX(x);
        pet.popover.setAnchorY(above ? bounds.getMinY() - POPOVER_GAP - shadowBelow
                : bounds.getMaxY() + POPOVER_GAP + shadowAbove);
    }

    private TaskCardViewModel modelForSource(String source, TaskCardViewModel primary,
                                             TaskCardViewModel secondary) {
        if (matchesSource(primary, source)) return primary;
        if (matchesSource(secondary, source)) return secondary;
        return null;
    }

    private boolean matchesSource(TaskCardViewModel model, String source) {
        if (model == null || model.source() == null) return false;
        return model.source().toLowerCase().contains(source);
    }

    private void updatePet(PetSlot pet, TaskCardViewModel model) {
        String summary = model == null ? "<none> (idle)"
                : "status=" + model.status() + " badge=" + model.badge() + " active=" + model.activeTaskCount()
                + " title=" + model.title();
        if (!summary.equals(pet.lastLoggedSummary)) {
            // Diagnostic trail for "pet looks idle while the tool is running" reports.
            log.info("Pet {} <- {}{}", pet.id, summary, pet.manualActionActive ? " (manual action active)" : "");
            pet.lastLoggedSummary = summary;
        }
        pet.latestModel = model;
        if (!pet.manualActionActive) pet.controller.applyState(model);
    }

    @Override
    public void idle() {
        updatePet(codexPet, null);
        updatePet(claudePet, null);
    }

    public void setOnSwitchMode(Runnable onSwitchMode) { this.onSwitchMode = onSwitchMode; }
    public void setOnShowPanel(Runnable onShowPanel) { this.onShowPanel = onShowPanel; }
    public void setOnQuit(Runnable onQuit) { this.onQuit = onQuit; }

    private static final class PetSlot {
        private final String id;
        private final String displayName;
        private final PetAnimationController controller = new PetAnimationController();
        private final PauseTransition manualActionTimer = new PauseTransition(Duration.seconds(5));
        private double scale = 1.0;
        private String packId;
        private TaskCardViewModel latestModel;
        private String lastLoggedSummary;
        private boolean manualActionActive;
        private final Popup popover = new Popup();
        private final VBox popoverContent = new VBox(2);
        private final List<TaskRowView> popoverRows = new ArrayList<>();
        /** Slightly longer than a typical double-click interval, so a double click never opens the list. */
        private final PauseTransition clickDelay = new PauseTransition(Duration.millis(260));
        private long popoverHiddenAt = Long.MIN_VALUE / 2;
        private boolean popoverOpenAtPress;
        private PetDisplay display;
        private boolean borrowed;

        private PetSlot(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            controller.setLogName(id);
            popoverContent.setPrefWidth(POPOVER_WIDTH);
            popoverContent.setMaxWidth(POPOVER_WIDTH);
            popoverContent.setPadding(new Insets(10, 12, 10, 10));
            popoverContent.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(30,41,59,0.97), rgba(15,23,42,0.97));"
                    + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: rgba(148,163,184,0.22);");
            popoverContent.setEffect(new javafx.scene.effect.DropShadow(12, 0, 3, javafx.scene.paint.Color.rgb(2, 6, 23, 0.45)));
            popover.getContent().add(popoverContent);
            // Closes on clicks elsewhere in TaskBeacon and when the app loses focus, without swallowing that
            // click (swallowing it made the first attempt to drag the pet do nothing).
            popover.setAutoHide(true);
            popover.setConsumeAutoHidingEvents(false);
        }
    }
}
