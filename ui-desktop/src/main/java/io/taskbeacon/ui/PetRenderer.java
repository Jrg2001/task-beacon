package io.taskbeacon.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PetRenderer implements StatusRenderer {
    public static final double PET_WIDTH = 140;
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
            if (!pet.controller.setCustomImage(idleImage.toFile())) return false;
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
        configureRoot(root, codexPet.controller.node());
        configureRoot(secondaryRoot, claudePet.controller.node());
    }

    private void configureRoot(VBox petRoot, Node petNode) {
        petRoot.setAlignment(Pos.BOTTOM_CENTER);
        petRoot.setPadding(new Insets(2, 4, 2, 4));
        petRoot.setStyle("-fx-background-color: transparent;");
        petRoot.getChildren().add(petNode);
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
        Tooltip.install(petNode, new Tooltip(pet.displayName));
        petNode.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                if (onSwitchMode != null) onSwitchMode.run();
                e.consume();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                showContextMenu(pet, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
    }

    private void showContextMenu(PetSlot pet, double screenX, double screenY) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem petTitle = new MenuItem(pet.displayName + " Pet");
        petTitle.setDisable(true);

        MenuItem switchItem = new MenuItem("Switch to Compact Card");
        switchItem.setOnAction(e -> {
            if (onSwitchMode != null) onSwitchMode.run();
        });

        Menu actionMenu = new Menu("Play Action / \u64ad\u653e\u52a8\u4f5c");
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

        Menu builtInMenu = new Menu("Built-in Pets / \u5185\u7f6e\u5ba0\u7269");
        ToggleGroup packGroup = new ToggleGroup();
        builtInMenu.getItems().addAll(
                packMenu(pet, "Dogs / \u72d7", BundledPetPacks.DOGS, packGroup),
                packMenu(pet, "Cats / \u732b", BundledPetPacks.CATS, packGroup)
        );

        MenuItem selectImageItem = new MenuItem("Choose Custom Image / \u9009\u62e9\u81ea\u5b9a\u4e49\u56fe\u7247...");
        selectImageItem.setOnAction(e -> chooseCustomImage(pet));

        Menu sizeMenu = new Menu("Pet Size / \u5ba0\u7269\u5927\u5c0f");
        ToggleGroup sizeGroup = new ToggleGroup();
        double[] scales = {0.8, 1.0, 1.25, 1.5, 1.75, 2.0};
        String[] labels = {"80% (Small)", "100% (Default)", "125% (Medium)", "150% (Large)", "175% (Extra Large)", "200% (Giant)"};

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

        MenuItem resetImageItem = new MenuItem("Reset to Default Akita / \u6062\u590d\u9ed8\u8ba4\u79cb\u7530\u72ac");
        resetImageItem.setDisable(BundledPetPacks.DEFAULT_ID.equals(pet.packId));
        resetImageItem.setOnAction(e -> BundledPetPacks.find(BundledPetPacks.DEFAULT_ID)
                .ifPresent(pack -> selectPack(pet, pack)));

        MenuItem panelItem = new MenuItem("Tasks Overview");
        panelItem.setOnAction(e -> {
            if (onShowPanel != null) onShowPanel.run();
        });

        MenuItem quitItem = new MenuItem("Quit");
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
            RadioMenuItem item = new RadioMenuItem(pack.label());
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
        updatePet(codexPet, modelForSource("codex", primaryModel, secondaryModel));
        updatePet(claudePet, modelForSource("claude", primaryModel, secondaryModel));
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

        private PetSlot(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            controller.setLogName(id);
        }
    }
}
