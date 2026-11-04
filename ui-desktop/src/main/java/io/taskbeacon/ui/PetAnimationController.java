package io.taskbeacon.ui;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetAnimationController {
    public static final double PET_WIDTH = 96;
    public static final double PET_HEIGHT = 96;
    private static final Pattern DECLARED_FRAME_COUNT = Pattern.compile(
            "(?:^|[-_])f(\\d+)(?=[-_.]|$)", Pattern.CASE_INSENSITIVE);

    public enum ManualAction {
        IDLE,
        STRETCH,
        WALK,
        RUN,
        BARK,
        ITCH,
        LICK,
        SLEEP
    }

    private final Pane container = new Pane();
    private final Group avatar = new Group();

    // Custom avatar support
    private final ImageView customImageView = new ImageView();
    private final StackPane customImageWrapper = new StackPane();
    private boolean hasCustomImage = false;
    private double customScale = 1.0;
    private File customImageFile = null;
    private File currentlyLoadedCustomFile = null;
    private final java.util.Map<String, File> customStateFiles = new java.util.HashMap<>();
    private Timeline spriteTimeline;
    private int currentSpriteFrame = 0;
    private int currentSpriteFrameCount = 1;
    private List<Rectangle2D> currentSpriteViewports = List.of();
    private final Map<String, List<Rectangle2D>> declaredFrameCache = new HashMap<>();
    private Rectangle2D customPackCrop;
    private boolean pixelSpritePack;
    private String currentCustomMode = "";
    private final PauseTransition customIdleTimer = new PauseTransition(Duration.seconds(30));

    // Body parts
    private final Ellipse shadow = new Ellipse(48, 88, 30, 7);
    private final Circle body = new Circle(48, 54, 32);
    private final Polygon leftEar = new Polygon(26, 32, 18, 12, 38, 24);
    private final Polygon rightEar = new Polygon(58, 24, 78, 12, 70, 32);
    private final Polygon leftEarInner = new Polygon(27, 30, 22, 16, 36, 25);
    private final Polygon rightEarInner = new Polygon(60, 25, 74, 16, 69, 30);

    // Eyes
    private final Group leftEyeGroup = new Group();
    private final Group rightEyeGroup = new Group();
    private final Circle leftEye = new Circle(38, 52, 5.5, Color.web("#0f172a"));
    private final Circle rightEye = new Circle(58, 52, 5.5, Color.web("#0f172a"));
    private final Circle leftPupil = new Circle(40, 50.5, 2, Color.WHITE);
    private final Circle rightPupil = new Circle(60, 50.5, 2, Color.WHITE);
    private final Arc leftHappyEye = new Arc(38, 54, 6, 6, 0, 180);
    private final Arc rightHappyEye = new Arc(58, 54, 6, 6, 0, 180);
    private final Arc leftClosedEye = new Arc(38, 53, 5.5, 3.5, 180, 180);
    private final Arc rightClosedEye = new Arc(58, 53, 5.5, 3.5, 180, 180);

    // Cheeks & Mouth
    private final Circle leftCheek = new Circle(30, 60, 4.5, Color.web("#fb7185", 0.5));
    private final Circle rightCheek = new Circle(66, 60, 4.5, Color.web("#fb7185", 0.5));
    private final Arc mouth = new Arc(48, 60, 4, 3, 180, 180);

    // Antenna & Halo
    private final Line antennaStem = new Line(48, 22, 48, 12);
    private final Circle antennaLight = new Circle(48, 10, 4.5);
    private final Circle antennaHalo = new Circle(48, 10, 8.0);

    // Hands / Paws
    private final Ellipse leftPaw = new Ellipse(34, 74, 6.5, 5);
    private final Ellipse rightPaw = new Ellipse(62, 74, 6.5, 5);

    // 1. Idle Sleep Prop: zZ
    private final Label sleepZ = new Label("zZ");
    private final Timeline sleepTimeline;

    // 2. Typing Cyber Laptop
    private final Group laptopGroup = new Group();
    private final Rectangle laptopBase = new Rectangle(32, 69, 32, 7.5);
    private final Line laptopKeys1 = new Line(35, 72, 61, 72);
    private final Line laptopKeys2 = new Line(35, 74.5, 61, 74.5);
    private final Rectangle laptopScreen = new Rectangle(35, 52, 26, 17);
    private final Rectangle laptopDisplay = new Rectangle(37, 54, 22, 13);
    private final Line codeLine1 = new Line(39, 58, 50, 58);
    private final Line codeLine2 = new Line(39, 61, 55, 61);
    private final Rectangle cursor = new Rectangle(39, 63.5, 3.5, 1.8);

    // 3. Thinking Props
    private final Group thoughtGroup = new Group();
    private final Circle thoughtDot1 = new Circle(54, 24, 2.0);
    private final Circle thoughtDot2 = new Circle(60, 17, 3.0);
    private final Circle thoughtBulb = new Circle(68, 9, 5.5);
    private final Rectangle bulbBase = new Rectangle(66.5, 14.5, 3, 2);

    // 4. Editing Props (Notepad + Pencil)
    private final Group notepadGroup = new Group();
    private final Rectangle notepadPaper = new Rectangle(54, 52, 18, 22);
    private final Line paperLine1 = new Line(57, 57, 68, 57);
    private final Line paperLine2 = new Line(57, 61, 68, 61);
    private final Line paperLine3 = new Line(57, 65, 66, 65);
    private final Group pencilGroup = new Group();
    private final Rectangle pencilBody = new Rectangle(62, 58, 14, 3.5);
    private final Polygon pencilTip = new Polygon(62, 58, 58, 60, 62, 61.5);
    private final Rectangle pencilEraser = new Rectangle(74, 58, 3, 3.5);

    // 5. Tool Props (Magnifier)
    private final Group toolGroup = new Group();
    private final Circle toolGlass = new Circle(66, 56, 8.5);
    private final Line toolHandle = new Line(60, 62, 52, 70);

    // 6. Celebration Star
    private final Label celebrationSparkle = new Label("\u2728");

    // 7. Attention Badge
    private final Label badge = new Label("!");

    // Timelines
    private final Timeline breatheTimeline;
    private final Timeline blinkTimeline;
    private final Timeline actionTimeline;
    private final Random random = new Random();

    private String currentActivity = "NONE";

    public PetAnimationController() {
        setupStyles();
        buildHierarchy();

        // 1. Breathing timeline
        breatheTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(avatar.scaleYProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(avatar.translateYProperty(), 0.0, Interpolator.EASE_BOTH),
                        new KeyValue(shadow.scaleXProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1400),
                        new KeyValue(avatar.scaleYProperty(), 1.03, Interpolator.EASE_BOTH),
                        new KeyValue(avatar.translateYProperty(), -2.0, Interpolator.EASE_BOTH),
                        new KeyValue(shadow.scaleXProperty(), 0.92, Interpolator.EASE_BOTH))
        );
        breatheTimeline.setAutoReverse(true);
        breatheTimeline.setCycleCount(Animation.INDEFINITE);

        // 2. Random eye blink (when eyes open)
        blinkTimeline = new Timeline(
                new KeyFrame(Duration.seconds(3.5), event -> triggerBlink())
        );
        blinkTimeline.setCycleCount(Animation.INDEFINITE);

        // 3. Sleep zZ floating timeline
        sleepTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sleepZ.translateYProperty(), 0),
                        new KeyValue(sleepZ.translateXProperty(), 0),
                        new KeyValue(sleepZ.opacityProperty(), 0.85)),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(sleepZ.translateYProperty(), -16),
                        new KeyValue(sleepZ.translateXProperty(), 7),
                        new KeyValue(sleepZ.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(2400),
                        new KeyValue(sleepZ.opacityProperty(), 0.0))
        );
        sleepTimeline.setCycleCount(Animation.INDEFINITE);

        // 4. Action timeline
        actionTimeline = new Timeline();
        actionTimeline.setCycleCount(Animation.INDEFINITE);

        start();
        setIdle();
    }

    private void setupStyles() {
        container.setPrefSize(PET_WIDTH, PET_HEIGHT);
        container.setMinSize(PET_WIDTH, PET_HEIGHT);
        container.setMaxSize(PET_WIDTH, PET_HEIGHT);

        shadow.setFill(Color.web("#090d16", 0.35));

        LinearGradient bodyGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#475569")),
                new Stop(1, Color.web("#1e293b")));
        body.setFill(bodyGrad);
        body.setStroke(Color.web("#64748b", 0.4));
        body.setStrokeWidth(1.5);

        leftEar.setFill(Color.web("#334155"));
        rightEar.setFill(Color.web("#334155"));
        leftEarInner.setFill(Color.web("#f472b6", 0.7));
        rightEarInner.setFill(Color.web("#f472b6", 0.7));

        mouth.setType(ArcType.OPEN);
        mouth.setFill(null);
        mouth.setStroke(Color.web("#0f172a", 0.8));
        mouth.setStrokeWidth(1.2);

        antennaStem.setStroke(Color.web("#64748b"));
        antennaStem.setStrokeWidth(2);
        antennaLight.setFill(Color.web("#10b981", 0.7));
        antennaLight.setStroke(Color.web("#bae6fd", 0.8));
        antennaLight.setStrokeWidth(1);

        antennaHalo.setFill(null);
        antennaHalo.setStroke(Color.web("#38bdf8", 0.35));
        antennaHalo.setStrokeWidth(1.5);
        antennaHalo.setVisible(false);

        leftPaw.setFill(Color.web("#64748b"));
        rightPaw.setFill(Color.web("#64748b"));

        // Happy eyes
        leftHappyEye.setType(ArcType.OPEN);
        leftHappyEye.setFill(null);
        leftHappyEye.setStroke(Color.web("#0f172a"));
        leftHappyEye.setStrokeWidth(2.2);
        leftHappyEye.setVisible(false);

        rightHappyEye.setType(ArcType.OPEN);
        rightHappyEye.setFill(null);
        rightHappyEye.setStroke(Color.web("#0f172a"));
        rightHappyEye.setStrokeWidth(2.2);
        rightHappyEye.setVisible(false);

        // Closed sleeping eyes
        leftClosedEye.setType(ArcType.OPEN);
        leftClosedEye.setFill(null);
        leftClosedEye.setStroke(Color.web("#0f172a", 0.85));
        leftClosedEye.setStrokeWidth(2.0);
        leftClosedEye.setVisible(false);

        rightClosedEye.setType(ArcType.OPEN);
        rightClosedEye.setFill(null);
        rightClosedEye.setStroke(Color.web("#0f172a", 0.85));
        rightClosedEye.setStrokeWidth(2.0);
        rightClosedEye.setVisible(false);

        // Sleep zZ label
        sleepZ.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        sleepZ.setLayoutX(68);
        sleepZ.setLayoutY(36);
        sleepZ.setOpacity(0);
        sleepZ.setVisible(false);

        // Cyber Mini Laptop
        laptopBase.setFill(Color.web("#334155"));
        laptopBase.setStroke(Color.web("#64748b"));
        laptopBase.setStrokeWidth(1);
        laptopBase.setArcWidth(4);
        laptopBase.setArcHeight(4);

        laptopKeys1.setStroke(Color.web("#475569"));
        laptopKeys1.setStrokeWidth(1);
        laptopKeys2.setStroke(Color.web("#475569"));
        laptopKeys2.setStrokeWidth(1);

        laptopScreen.setFill(Color.web("#0f172a"));
        laptopScreen.setStroke(Color.web("#475569"));
        laptopScreen.setStrokeWidth(1);
        laptopScreen.setArcWidth(4);
        laptopScreen.setArcHeight(4);

        laptopDisplay.setFill(Color.web("#051626"));
        laptopDisplay.setArcWidth(2);
        laptopDisplay.setArcHeight(2);

        codeLine1.setStroke(Color.web("#38bdf8"));
        codeLine1.setStrokeWidth(1.6);
        codeLine2.setStroke(Color.web("#4ade80"));
        codeLine2.setStrokeWidth(1.6);
        cursor.setFill(Color.web("#f1f5f9"));
        cursor.setStroke(null);

        laptopGroup.getChildren().addAll(laptopBase, laptopKeys1, laptopKeys2,
                laptopScreen, laptopDisplay, codeLine1, codeLine2, cursor);
        laptopGroup.setVisible(false);

        // Thinking props
        thoughtDot1.setFill(Color.web("#fef08a"));
        thoughtDot2.setFill(Color.web("#fde047"));
        thoughtBulb.setFill(Color.web("#facc15"));
        thoughtBulb.setStroke(Color.web("#ca8a04"));
        thoughtBulb.setStrokeWidth(1);
        bulbBase.setFill(Color.web("#71717a"));
        thoughtGroup.getChildren().addAll(thoughtDot1, thoughtDot2, thoughtBulb, bulbBase);
        thoughtGroup.setVisible(false);

        // Notepad & pencil
        notepadPaper.setFill(Color.web("#f8fafc"));
        notepadPaper.setStroke(Color.web("#cbd5e1"));
        notepadPaper.setStrokeWidth(1);
        notepadPaper.setArcWidth(3);
        notepadPaper.setArcHeight(3);

        paperLine1.setStroke(Color.web("#93c5fd"));
        paperLine2.setStroke(Color.web("#93c5fd"));
        paperLine3.setStroke(Color.web("#93c5fd"));

        pencilBody.setFill(Color.web("#fbbf24"));
        pencilTip.setFill(Color.web("#f87171"));
        pencilEraser.setFill(Color.web("#f43f5e"));
        pencilGroup.getChildren().addAll(pencilBody, pencilTip, pencilEraser);
        notepadGroup.getChildren().addAll(notepadPaper, paperLine1, paperLine2, paperLine3, pencilGroup);
        notepadGroup.setVisible(false);

        // Tool magnifier
        toolGlass.setFill(Color.web("#38bdf8", 0.25));
        toolGlass.setStroke(Color.web("#94a3b8"));
        toolGlass.setStrokeWidth(2);
        toolHandle.setStroke(Color.web("#64748b"));
        toolHandle.setStrokeWidth(3);
        toolGroup.getChildren().addAll(toolGlass, toolHandle);
        toolGroup.setVisible(false);

        // Celebration sparkle
        celebrationSparkle.setStyle("-fx-font-size: 14px; -fx-text-fill: #facc15;");
        celebrationSparkle.setLayoutX(68);
        celebrationSparkle.setLayoutY(14);
        celebrationSparkle.setVisible(false);

        // Badge
        badge.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");
        badge.setAlignment(Pos.CENTER);
        badge.setLayoutX(42);
        badge.setLayoutY(-8);
        badge.setVisible(false);
    }

    private void buildHierarchy() {
        leftEyeGroup.getChildren().addAll(leftEye, leftPupil, leftHappyEye, leftClosedEye);
        rightEyeGroup.getChildren().addAll(rightEye, rightPupil, rightHappyEye, rightClosedEye);

        avatar.getChildren().addAll(
                leftEar, rightEar, leftEarInner, rightEarInner,
                antennaStem, antennaHalo, antennaLight,
                body, leftCheek, rightCheek, mouth,
                leftEyeGroup, rightEyeGroup,
                sleepZ,
                thoughtGroup,
                notepadGroup,
                toolGroup,
                laptopGroup,
                leftPaw, rightPaw,
                celebrationSparkle,
                badge
        );

        customImageView.setPreserveRatio(true);
        customImageView.setSmooth(false);
        customImageView.setFitWidth(96);
        customImageView.setFitHeight(72);

        customImageWrapper.setPrefSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setMinSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setAlignment(Pos.BOTTOM_CENTER);
        customImageWrapper.setPadding(new Insets(0, 0, 2, 0));
        customImageWrapper.getChildren().add(customImageView);
        customImageWrapper.setVisible(false);

        container.getChildren().addAll(shadow, avatar, customImageWrapper);
    }

    public Node node() { return container; }

    double displayWidth() { return container.getPrefWidth(); }

    boolean isShadowVisible() { return shadow.isVisible(); }

    public double getCustomScale() { return customScale; }

    public void setCustomScale(double scale) {
        this.customScale = Math.max(0.5, Math.min(3.0, scale));
        updateCustomDimensions();
    }

    private void updateCustomDimensions() {
        double baseW = pixelSpritePack && customPackCrop != null ? customPackCrop.getWidth() * 3.0 : 84.0;
        double baseH = pixelSpritePack && customPackCrop != null ? customPackCrop.getHeight() * 3.0 : 76.0;
        double fitW = baseW * customScale;
        double fitH = baseH * customScale;
        customImageView.setFitWidth(fitW);
        customImageView.setFitHeight(fitH);

        double boxW = Math.max(PET_WIDTH, fitW + 12);
        double boxH = Math.max(PET_HEIGHT, fitH + 12);
        container.setPrefSize(boxW, boxH);
        container.setMinSize(boxW, boxH);
        container.setMaxSize(boxW, boxH);
        customImageWrapper.setPrefSize(boxW, boxH);
        customImageWrapper.setMinSize(boxW, boxH);
        customImageWrapper.setMaxSize(boxW, boxH);
    }

    public boolean setCustomImage(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            clearCustomImage();
            return false;
        }
        this.customImageFile = file;
        this.hasCustomImage = true;

        scanStateFiles(file);
        updateCustomDimensions();

        File idleFile = customStateFiles.getOrDefault("idle", file);
        boolean loaded = displayCustomFile(idleFile);
        if (!loaded) {
            clearCustomImage();
            return false;
        }
        currentlyLoadedCustomFile = idleFile;
        currentCustomMode = "";
        switchCustomState("idle");

        customImageWrapper.setVisible(true);
        avatar.setVisible(false);
        shadow.setVisible(false);
        return true;
    }

    private void scanStateFiles(File primaryFile) {
        customStateFiles.clear();
        customStateFiles.put("idle", primaryFile);
        customPackCrop = null;
        pixelSpritePack = false;

        File parentDir = primaryFile.getParentFile();
        if (parentDir != null && parentDir.isDirectory()) {
            File[] siblings = parentDir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
            });
            if (siblings != null) {
                Arrays.sort(siblings, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File f : siblings) {
                    String name = f.getName().toLowerCase();
                    if (name.contains("idle")) {
                        customStateFiles.put("idle", f);
                    }
                    if (name.contains("sit")) customStateFiles.put("sitting", f);

                    if (name.contains("run")) {
                        customStateFiles.put("running", f);
                    }
                    if (name.contains("walk")) customStateFiles.put("working", f);

                    if (name.contains("sleeping1") || name.contains("sleeping 1")
                            || name.contains("sleep") && !customStateFiles.containsKey("sleeping")) {
                        customStateFiles.put("sleeping", f);
                    }
                    if (name.contains("lying") || name.contains("laying")) customStateFiles.put("lying", f);

                    if (name.contains("bark") || name.contains("meow")) customStateFiles.put("attention", f);
                    if (name.contains("licking1") || name.contains("licking 1"))
                        customStateFiles.put("completed", f);
                    else if (name.contains("lick") && !customStateFiles.containsKey("completed"))
                        customStateFiles.put("completed", f);

                    if (name.contains("itch")) customStateFiles.put("stalled", f);
                    if (name.contains("stretch")) {
                        customStateFiles.put("thinking", f);
                    }
                }
                computePackCrop(siblings);
            }
        }
        if (!customStateFiles.containsKey("idle")) {
            customStateFiles.put("idle", primaryFile);
        }
    }

    private void computePackCrop(File[] siblings) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -1, maxY = -1;
        boolean found = false;
        for (File file : siblings) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".png")) continue;
            Image image = new Image(file.toURI().toString());
            if (image.isError() || Math.round(image.getHeight()) != 100
                    || Math.round(image.getWidth()) % 100 != 0) continue;
            int frames = Math.max(1, (int) Math.round(image.getWidth() / 100.0));
            Rectangle2D crop = computeContentCrop(image, 100, 100, frames);
            minX = Math.min(minX, crop.getMinX());
            minY = Math.min(minY, crop.getMinY());
            maxX = Math.max(maxX, crop.getMaxX());
            maxY = Math.max(maxY, crop.getMaxY());
            found = true;
        }
        if (!found) return;

        double width = maxX - minX;
        double height = maxY - minY;
        width = Math.max(width, height * 4.0 / 3.0);
        height = Math.max(height, width * 3.0 / 4.0);
        width = Math.ceil(width / 2.0) * 2.0;
        height = Math.ceil(height / 2.0) * 2.0;
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double x = Math.max(0, Math.min(100 - width, Math.floor(centerX - width / 2.0)));
        double y = Math.max(0, Math.min(100 - height, Math.floor(centerY - height / 2.0)));
        customPackCrop = new Rectangle2D(x, y, width, height);
        pixelSpritePack = true;
    }

    private Rectangle2D computeContentCrop(Image image, double frameW, double frameH, int frameCount) {
        if (image == null || frameW <= 0 || frameH <= 0) {
            return new Rectangle2D(0, 0, frameW, frameH);
        }
        try {
            PixelReader reader = image.getPixelReader();
            if (reader == null) {
                return new Rectangle2D(0, 0, frameW, frameH);
            }

            int fw = (int) frameW;
            int fh = (int) frameH;
            int framesToScan = Math.max(1, Math.min(frameCount, 10));
            int step = Math.max(1, Math.min(fw, fh) / 100);

            int minX = fw;
            int maxX = -1;
            int minY = fh;
            int maxY = -1;

            for (int f = 0; f < framesToScan; f++) {
                int offsetX = (int) Math.round(f * frameW);
                for (int x = 0; x < fw; x += step) {
                    for (int y = 0; y < fh; y += step) {
                        Color c = reader.getColor(offsetX + x, y);
                        if (c.getOpacity() > 0.08) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return new Rectangle2D(0, 0, frameW, frameH);
            }

            if (step > 1) {
                minX = Math.max(0, minX - step);
                maxX = Math.min(fw - 1, maxX + step);
                minY = Math.max(0, minY - step);
                maxY = Math.min(fh - 1, maxY + step);
            }

            int contentW = maxX - minX + 1;
            int contentH = maxY - minY + 1;

            if (contentW >= fw * 0.85 && contentH >= fh * 0.85) {
                return new Rectangle2D(0, 0, frameW, frameH);
            }

            int padX = Math.max(1, (int) Math.round(contentW * 0.06));
            int padY = Math.max(1, (int) Math.round(contentH * 0.06));

            int cropX = Math.max(0, minX - padX);
            int cropY = Math.max(0, minY - padY);
            int cropW = Math.min(fw - cropX, contentW + padX * 2);
            int cropH = Math.min(fh - cropY, contentH + padY * 2);

            return new Rectangle2D(cropX, cropY, cropW, cropH);
        } catch (Exception ignored) {
            return new Rectangle2D(0, 0, frameW, frameH);
        }
    }

    private boolean displayCustomFile(File file) {
        if (file == null || !file.exists()) return false;
        try {
            Image image = new Image(file.toURI().toString());
            if (image.isError()) return false;

            if (spriteTimeline != null) {
                spriteTimeline.stop();
                spriteTimeline = null;
            }

            customImageView.setImage(image);

            double w = image.getWidth();
            double h = image.getHeight();

            int declaredFrameCount = declaredFrameCount(file);
            // Standard packs use square frames; generated packs can declare their frame count as -fN.
            if (declaredFrameCount > 1 || h > 0 && w >= 2 * h
                    && (w % h == 0 || Math.abs((w / h) - Math.round(w / h)) < 0.05)) {
                int frameCount = declaredFrameCount > 1
                        ? declaredFrameCount : (int) Math.round(w / h);
                double frameWidth = w / frameCount;
                double frameHeight = h;
                currentSpriteFrameCount = frameCount;

                List<Rectangle2D> detectedFrames = declaredFrameCount > 1
                        ? detectDeclaredFrames(file, image, frameCount) : List.of();
                currentSpriteViewports = detectedFrames;
                Rectangle2D crop = !detectedFrames.isEmpty() ? detectedFrames.getFirst()
                        : pixelSpritePack && frameWidth == 100 && frameHeight == 100
                        ? customPackCrop : computeContentCrop(image, frameWidth, frameHeight, frameCount);
                double cropX = crop.getMinX();
                double cropY = crop.getMinY();
                double cropW = crop.getWidth();
                double cropH = crop.getHeight();

                boolean isPixelArt = pixelSpritePack || cropW < 64 || cropH < 64;
                customImageView.setSmooth(!isPixelArt);

                currentSpriteFrame = 0;
                customImageView.setViewport(crop);

                SpritePlayback playback = playbackFor(file, frameCount);
                spriteTimeline = new Timeline(new KeyFrame(playback.frameDuration(), e -> {
                    currentSpriteFrame = (currentSpriteFrame + 1) % frameCount;
                    if (!currentSpriteViewports.isEmpty()) {
                        customImageView.setViewport(currentSpriteViewports.get(currentSpriteFrame));
                    } else {
                        customImageView.setViewport(new Rectangle2D(
                                currentSpriteFrame * frameWidth + cropX,
                                cropY,
                                cropW,
                                cropH
                        ));
                    }
                }));
                spriteTimeline.setCycleCount(playback.cycles());
                if (playback.fallbackState() != null) {
                    spriteTimeline.setOnFinished(event -> displayFallbackState(playback.fallbackState()));
                }
                spriteTimeline.play();
            } else {
                currentSpriteFrameCount = 1;
                currentSpriteViewports = List.of();
                Rectangle2D crop = pixelSpritePack && w == 100 && h == 100
                        ? customPackCrop : computeContentCrop(image, w, h, 1);
                boolean isPixelArt = pixelSpritePack || crop.getWidth() < 64 || crop.getHeight() < 64;
                customImageView.setSmooth(!isPixelArt);

                if (crop.getWidth() < w || crop.getHeight() < h) {
                    customImageView.setViewport(crop);
                } else {
                    customImageView.setViewport(null);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<Rectangle2D> detectDeclaredFrames(File file, Image image, int expectedCount) {
        String cacheKey = file.getAbsolutePath() + ":" + file.lastModified();
        List<Rectangle2D> cached = declaredFrameCache.get(cacheKey);
        if (cached != null) return cached;

        List<Rectangle2D> result = detectDeclaredFrames(image, expectedCount);
        declaredFrameCache.put(cacheKey, result);
        return result;
    }

    private List<Rectangle2D> detectDeclaredFrames(Image image, int expectedCount) {
        try {
            int width = (int) Math.round(image.getWidth());
            int height = (int) Math.round(image.getHeight());
            PixelReader reader = image.getPixelReader();
            if (reader == null || width <= 0 || height <= 0 || expectedCount <= 1) return List.of();

            int[] pixels = new int[width * height];
            reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), pixels, 0, width);
            BitSet visited = new BitSet(pixels.length);
            int[] queue = new int[pixels.length];
            List<SpriteComponent> components = new ArrayList<>();
            int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
            int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

            for (int origin = 0; origin < pixels.length; origin++) {
                if (visited.get(origin) || (pixels[origin] >>> 24) <= 20) continue;
                int head = 0, tail = 0, count = 0;
                int minX = width, minY = height, maxX = 0, maxY = 0;
                queue[tail++] = origin;
                visited.set(origin);
                while (head < tail) {
                    int point = queue[head++];
                    int x = point % width;
                    int y = point / width;
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    for (int direction = 0; direction < dx.length; direction++) {
                        int nextX = x + dx[direction];
                        int nextY = y + dy[direction];
                        if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) continue;
                        int next = nextY * width + nextX;
                        if (!visited.get(next) && (pixels[next] >>> 24) > 20) {
                            visited.set(next);
                            queue[tail++] = next;
                        }
                    }
                }
                if (count > 100) {
                    components.add(new SpriteComponent(count,
                            new Rectangle2D(minX, minY, maxX - minX + 1, maxY - minY + 1)));
                }
            }

            if (components.size() < expectedCount) return List.of();
            components.sort(Comparator.comparingInt(SpriteComponent::pixels).reversed());
            List<Rectangle2D> bounds = components.stream().limit(expectedCount)
                    .map(SpriteComponent::bounds)
                    .sorted(Comparator.comparingDouble(Rectangle2D::getMinX))
                    .toList();

            List<Rectangle2D> frames = new ArrayList<>(bounds.size());
            for (int index = 0; index < bounds.size(); index++) {
                Rectangle2D bound = bounds.get(index);
                double centerX = bound.getMinX() + bound.getWidth() / 2.0;
                double leftLimit = index == 0 ? 0 : (centerX
                        + bounds.get(index - 1).getMinX() + bounds.get(index - 1).getWidth() / 2.0) / 2.0;
                double rightLimit = index == bounds.size() - 1 ? width : (centerX
                        + bounds.get(index + 1).getMinX() + bounds.get(index + 1).getWidth() / 2.0) / 2.0;
                double padX = Math.max(2, bound.getWidth() * 0.06);
                double padY = Math.max(2, bound.getHeight() * 0.06);
                double x = Math.max(leftLimit, bound.getMinX() - padX);
                double y = Math.max(0, bound.getMinY() - padY);
                double right = Math.min(rightLimit, bound.getMaxX() + padX);
                double bottom = Math.min(height, bound.getMaxY() + padY);
                frames.add(new Rectangle2D(x, y, right - x, bottom - y));
            }
            return List.copyOf(frames);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private record SpriteComponent(int pixels, Rectangle2D bounds) { }
    private int declaredFrameCount(File file) {
        Matcher matcher = DECLARED_FRAME_COUNT.matcher(file.getName());
        if (!matcher.find()) return 0;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record SpritePlayback(Duration frameDuration, int cycles, String fallbackState) { }

    private SpritePlayback playbackFor(File file, int frameCount) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.contains("run")) return new SpritePlayback(Duration.millis(95), Animation.INDEFINITE, null);
        if (name.contains("walk")) return new SpritePlayback(Duration.millis(145), Animation.INDEFINITE, null);
        if (name.contains("idle")) return new SpritePlayback(Duration.millis(200), Animation.INDEFINITE, null);
        if (name.contains("itch")) return new SpritePlayback(Duration.millis(220), Animation.INDEFINITE, null);
        if (name.contains("bark") || name.contains("meow"))
            return new SpritePlayback(Duration.millis(170), Animation.INDEFINITE, null);
        if (name.contains("stretch")) return new SpritePlayback(Duration.millis(170), frameCount, "idle");
        if (name.contains("lying") || name.contains("laying"))
            return new SpritePlayback(Duration.millis(180), frameCount, "sleeping");
        if (name.contains("lick")) return new SpritePlayback(Duration.millis(160), frameCount, "idle");
        return new SpritePlayback(Duration.millis(140), Animation.INDEFINITE, null);
    }

    private void displayFallbackState(String state) {
        File fallback = customStateFiles.get(state);
        if (fallback != null && !fallback.equals(currentlyLoadedCustomFile)) {
            currentlyLoadedCustomFile = fallback;
            displayCustomFile(fallback);
        }
    }

    private void switchCustomState(String state) {
        switchCustomState(state, false);
    }

    private void switchCustomState(String state, boolean restart) {
        if (!hasCustomImage || (!restart && state.equals(currentCustomMode))) return;
        currentCustomMode = state;
        customIdleTimer.stop();
        File target = customStateFiles.getOrDefault(state, customStateFiles.get("idle"));
        if (target != null && (restart || !target.equals(currentlyLoadedCustomFile))) {
            currentlyLoadedCustomFile = target;
            displayCustomFile(target);
        }
        if (state.equals("idle")) {
            customIdleTimer.setOnFinished(event -> {
                if (!"idle".equals(currentCustomMode)) return;
                File lying = customStateFiles.get("lying");
                if (lying != null) {
                    currentlyLoadedCustomFile = lying;
                    displayCustomFile(lying);
                } else {
                    displayFallbackState("sleeping");
                }
            });
            customIdleTimer.playFromStart();
        }
    }

    public void clearCustomImage() {
        hasCustomImage = false;
        customImageFile = null;
        currentlyLoadedCustomFile = null;
        customStateFiles.clear();
        customPackCrop = null;
        pixelSpritePack = false;
        currentCustomMode = "";
        customIdleTimer.stop();
        if (spriteTimeline != null) {
            spriteTimeline.stop();
            spriteTimeline = null;
        }
        customImageView.setImage(null);
        customImageView.setViewport(null);
        currentSpriteFrameCount = 1;
        currentSpriteViewports = List.of();
        customImageView.setFitWidth(96);
        customImageView.setFitHeight(72);
        container.setPrefSize(PET_WIDTH, PET_HEIGHT);
        container.setMinSize(PET_WIDTH, PET_HEIGHT);
        container.setMaxSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setPrefSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setMinSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setMaxSize(PET_WIDTH, PET_HEIGHT);
        customImageWrapper.setVisible(false);
        avatar.setVisible(true);
        shadow.setVisible(true);
    }

    public boolean hasCustomImage() {
        return hasCustomImage;
    }

    String currentCustomFileName() {
        return currentlyLoadedCustomFile == null ? "" : currentlyLoadedCustomFile.getName();
    }

    Rectangle2D customPackCrop() {
        return customPackCrop;
    }

    int currentSpriteFrameCount() {
        return currentSpriteFrameCount;
    }

    int currentSpriteViewportCount() {
        return currentSpriteViewports.size();
    }

    public void start() {
        breatheTimeline.play();
        blinkTimeline.play();
    }

    public void stop() {
        breatheTimeline.stop();
        blinkTimeline.stop();
        sleepTimeline.stop();
        actionTimeline.stop();
        customIdleTimer.stop();
        if (spriteTimeline != null) spriteTimeline.stop();
    }

    private void triggerBlink() {
        if (hasCustomImage || !leftEye.isVisible()) return;
        Timeline blink = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(leftEyeGroup.scaleYProperty(), 1.0), new KeyValue(rightEyeGroup.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(120), new KeyValue(leftEyeGroup.scaleYProperty(), 0.1), new KeyValue(rightEyeGroup.scaleYProperty(), 0.1)),
                new KeyFrame(Duration.millis(240), new KeyValue(leftEyeGroup.scaleYProperty(), 1.0), new KeyValue(rightEyeGroup.scaleYProperty(), 1.0))
        );
        blink.play();
    }

    public void applyState(TaskCardViewModel model) {
        if (model == null) {
            setIdle();
            return;
        }

        String badgeText = model.badge();
        boolean attention = badgeText != null && !badgeText.isBlank();
        badge.setVisible(attention);
        badge.setText(attention ? badgeText : "!");

        if (hasCustomImage) {
            avatar.setVisible(false);
            shadow.setVisible(false);
            String status = model.status().toLowerCase();
            if (status.contains("failed") || status.contains("error") || attention) {
                switchCustomState("attention");
            } else if (status.contains("stalled") || status.contains("no recent activity")) {
                switchCustomState("stalled");
            } else if (status.contains("completed") || status.contains("succeeded")) {
                switchCustomState("completed");
            } else if (status.contains("thinking")) {
                switchCustomState("thinking");
            } else if (status.contains("editing") || status.contains("tool") || status.contains("reading")
                    || status.contains("searching")) {
                switchCustomState("working");
            } else if (status.contains("running") || status.contains("executing") || status.contains("testing")
                    || status.contains("building") || status.contains("starting") || model.activeTaskCount() > 0) {
                switchCustomState("running");
            } else {
                switchCustomState("idle");
            }
            return;
        }

        String status = model.status().toLowerCase();
        if (status.contains("failed") || status.contains("error")) {
            setFailed();
        } else if (status.contains("completed") || status.contains("succeeded")) {
            setSucceeded();
        } else if (attention) {
            setAttention();
        } else if (status.contains("running") || status.contains("executing")
                || status.contains("testing") || status.contains("building")
                || status.contains("starting")) {
            setTyping();
        } else if (status.contains("editing")) {
            setEditing();
        } else if (status.contains("tool") || status.contains("reading") || status.contains("searching")) {
            setUsingTool();
        } else if (status.contains("thinking")) {
            setThinking();
        } else if (model.activeTaskCount() > 0) {
            setTyping();
        } else {
            setIdle();
        }
    }

    public void reapplyState(TaskCardViewModel model) {
        currentActivity = "";
        currentCustomMode = "";
        applyState(model);
    }

    public void playManualAction(ManualAction action) {
        Objects.requireNonNull(action, "action");
        if (hasCustomImage) {
            avatar.setVisible(false);
            shadow.setVisible(false);
            String state = switch (action) {
                case IDLE -> "idle";
                case STRETCH -> "thinking";
                case WALK -> "working";
                case RUN -> "running";
                case BARK -> "attention";
                case ITCH -> "stalled";
                case LICK -> "completed";
                case SLEEP -> customStateFiles.containsKey("sleeping") ? "sleeping"
                        : customStateFiles.containsKey("lying") ? "lying" : "idle";
            };
            switchCustomState(state, true);
            return;
        }

        currentActivity = "";
        switch (action) {
            case IDLE -> setReady();
            case STRETCH -> setThinking();
            case WALK -> setUsingTool();
            case RUN -> setTyping();
            case BARK -> setAttention();
            case ITCH -> setFailed();
            case LICK -> setSucceeded();
            case SLEEP -> setIdle();
        }
    }

    private void setReady() {
        currentActivity = "READY";
        resetParts();
        antennaLight.setFill(Color.web("#10b981", 0.65));
        antennaHalo.setVisible(false);
        breatheTimeline.setRate(1.0);
    }

    public void setIdle() {
        if (hasCustomImage) {
            avatar.setVisible(false);
            shadow.setVisible(false);
            switchCustomState("idle");
            return;
        }
        if (currentActivity.equals("IDLE")) return;
        currentActivity = "IDLE";
        resetParts();

        // Sleepy closed eyes
        leftEye.setVisible(false);
        leftPupil.setVisible(false);
        rightEye.setVisible(false);
        rightPupil.setVisible(false);
        leftClosedEye.setVisible(true);
        rightClosedEye.setVisible(true);

        antennaLight.setFill(Color.web("#10b981", 0.65));
        antennaHalo.setVisible(false);

        sleepZ.setVisible(true);
        sleepTimeline.playFromStart();

        breatheTimeline.setRate(0.85);
    }

    private void setTyping() {
        if (currentActivity.equals("TYPING")) return;
        currentActivity = "TYPING";
        resetParts();

        antennaLight.setFill(Color.web("#f59e0b"));
        antennaHalo.setStroke(Color.web("#f59e0b", 0.4));
        antennaHalo.setVisible(true);

        laptopGroup.setVisible(true);

        leftPaw.setCenterX(37);
        leftPaw.setCenterY(73);
        rightPaw.setCenterX(59);
        rightPaw.setCenterY(73);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(leftPaw.translateYProperty(), 0),
                        new KeyValue(rightPaw.translateYProperty(), -5.0),
                        new KeyValue(cursor.opacityProperty(), 1.0),
                        new KeyValue(codeLine1.opacityProperty(), 1.0),
                        new KeyValue(avatar.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(110),
                        new KeyValue(leftPaw.translateYProperty(), -5.0),
                        new KeyValue(rightPaw.translateYProperty(), 0),
                        new KeyValue(cursor.opacityProperty(), 0.0),
                        new KeyValue(codeLine1.opacityProperty(), 0.6),
                        new KeyValue(avatar.translateYProperty(), -1)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(leftPaw.translateYProperty(), 0),
                        new KeyValue(rightPaw.translateYProperty(), -5.0),
                        new KeyValue(cursor.opacityProperty(), 1.0),
                        new KeyValue(codeLine1.opacityProperty(), 1.0),
                        new KeyValue(avatar.translateYProperty(), 0))
        );
        actionTimeline.play();
        breatheTimeline.setRate(1.6);
    }

    private void setThinking() {
        if (currentActivity.equals("THINKING")) return;
        currentActivity = "THINKING";
        resetParts();

        antennaLight.setFill(Color.web("#818cf8"));
        antennaHalo.setStroke(Color.web("#818cf8", 0.4));
        antennaHalo.setVisible(true);

        thoughtGroup.setVisible(true);

        leftPupil.setCenterY(48.5);
        leftPupil.setCenterX(39.5);
        rightPupil.setCenterY(48.5);
        rightPupil.setCenterX(59.5);

        rightPaw.setTranslateY(-7);
        rightPaw.setTranslateX(-2);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(avatar.rotateProperty(), 0),
                        new KeyValue(thoughtGroup.scaleXProperty(), 1.0),
                        new KeyValue(thoughtGroup.scaleYProperty(), 1.0)),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(avatar.rotateProperty(), -4.5),
                        new KeyValue(thoughtGroup.scaleXProperty(), 1.15),
                        new KeyValue(thoughtGroup.scaleYProperty(), 1.15)),
                new KeyFrame(Duration.millis(1600),
                        new KeyValue(avatar.rotateProperty(), 4.5),
                        new KeyValue(thoughtGroup.scaleXProperty(), 0.95),
                        new KeyValue(thoughtGroup.scaleYProperty(), 0.95)),
                new KeyFrame(Duration.millis(2400),
                        new KeyValue(avatar.rotateProperty(), 0),
                        new KeyValue(thoughtGroup.scaleXProperty(), 1.0),
                        new KeyValue(thoughtGroup.scaleYProperty(), 1.0))
        );
        actionTimeline.play();
        breatheTimeline.setRate(1.0);
    }

    private void setEditing() {
        if (currentActivity.equals("EDITING")) return;
        currentActivity = "EDITING";
        resetParts();

        antennaLight.setFill(Color.web("#10b981"));
        antennaHalo.setStroke(Color.web("#10b981", 0.35));
        antennaHalo.setVisible(true);

        notepadGroup.setVisible(true);

        leftPupil.setCenterX(41);
        rightPupil.setCenterX(61);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(pencilGroup.translateXProperty(), 0),
                        new KeyValue(pencilGroup.translateYProperty(), 0),
                        new KeyValue(pencilGroup.rotateProperty(), -10)),
                new KeyFrame(Duration.millis(120),
                        new KeyValue(pencilGroup.translateXProperty(), -4),
                        new KeyValue(pencilGroup.translateYProperty(), 3),
                        new KeyValue(pencilGroup.rotateProperty(), 5)),
                new KeyFrame(Duration.millis(240),
                        new KeyValue(pencilGroup.translateXProperty(), 0),
                        new KeyValue(pencilGroup.translateYProperty(), 0),
                        new KeyValue(pencilGroup.rotateProperty(), -10))
        );
        actionTimeline.play();
        breatheTimeline.setRate(1.4);
    }

    private void setUsingTool() {
        if (currentActivity.equals("TOOL")) return;
        currentActivity = "TOOL";
        resetParts();

        antennaLight.setFill(Color.web("#38bdf8"));
        antennaHalo.setStroke(Color.web("#38bdf8", 0.4));
        antennaHalo.setVisible(true);

        toolGroup.setVisible(true);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(toolGroup.rotateProperty(), -12),
                        new KeyValue(toolGroup.translateXProperty(), -3)),
                new KeyFrame(Duration.millis(600),
                        new KeyValue(toolGroup.rotateProperty(), 18),
                        new KeyValue(toolGroup.translateXProperty(), 4)),
                new KeyFrame(Duration.millis(1200),
                        new KeyValue(toolGroup.rotateProperty(), -12),
                        new KeyValue(toolGroup.translateXProperty(), -3))
        );
        actionTimeline.play();
        breatheTimeline.setRate(1.3);
    }

    private void setAttention() {
        if (currentActivity.equals("ATTENTION")) return;
        currentActivity = "ATTENTION";
        resetParts();

        antennaLight.setFill(Color.web("#ef4444"));
        antennaHalo.setStroke(Color.web("#ef4444", 0.5));
        antennaHalo.setVisible(true);
        badge.setVisible(true);

        leftPaw.setTranslateY(-8);
        rightPaw.setTranslateY(-8);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(avatar.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(160), new KeyValue(avatar.translateYProperty(), -9)),
                new KeyFrame(Duration.millis(320), new KeyValue(avatar.translateYProperty(), 0))
        );
        actionTimeline.play();
    }

    private void setSucceeded() {
        currentActivity = "SUCCEEDED";
        resetParts();

        antennaLight.setFill(Color.web("#22c55e"));
        antennaHalo.setStroke(Color.web("#22c55e", 0.5));
        antennaHalo.setVisible(true);

        leftEye.setVisible(false);
        leftPupil.setVisible(false);
        rightEye.setVisible(false);
        rightPupil.setVisible(false);
        leftHappyEye.setVisible(true);
        rightHappyEye.setVisible(true);
        celebrationSparkle.setVisible(true);

        Timeline jump = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(avatar.translateYProperty(), 0),
                        new KeyValue(celebrationSparkle.opacityProperty(), 1.0),
                        new KeyValue(celebrationSparkle.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(avatar.translateYProperty(), -14),
                        new KeyValue(celebrationSparkle.opacityProperty(), 0.4),
                        new KeyValue(celebrationSparkle.translateYProperty(), -8)),
                new KeyFrame(Duration.millis(400),
                        new KeyValue(avatar.translateYProperty(), 0),
                        new KeyValue(celebrationSparkle.opacityProperty(), 1.0),
                        new KeyValue(celebrationSparkle.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(600),
                        new KeyValue(avatar.translateYProperty(), -14),
                        new KeyValue(celebrationSparkle.opacityProperty(), 0.4),
                        new KeyValue(celebrationSparkle.translateYProperty(), -8)),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(avatar.translateYProperty(), 0),
                        new KeyValue(celebrationSparkle.opacityProperty(), 1.0),
                        new KeyValue(celebrationSparkle.translateYProperty(), 0))
        );
        jump.play();
    }

    private void setFailed() {
        currentActivity = "FAILED";
        resetParts();

        antennaLight.setFill(Color.web("#dc2626"));
        antennaHalo.setStroke(Color.web("#dc2626", 0.5));
        antennaHalo.setVisible(true);

        actionTimeline.stop();
        actionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(avatar.translateXProperty(), -3.5)),
                new KeyFrame(Duration.millis(100), new KeyValue(avatar.translateXProperty(), 3.5)),
                new KeyFrame(Duration.millis(200), new KeyValue(avatar.translateXProperty(), -3.5)),
                new KeyFrame(Duration.millis(300), new KeyValue(avatar.translateXProperty(), 3.5)),
                new KeyFrame(Duration.millis(400), new KeyValue(avatar.translateXProperty(), 0))
        );
        actionTimeline.play();
    }

    private void resetParts() {
        actionTimeline.stop();
        sleepTimeline.stop();

        avatar.setRotate(0);
        avatar.setTranslateX(0);
        avatar.setTranslateY(0);

        leftEye.setVisible(true);
        leftPupil.setVisible(true);
        rightEye.setVisible(true);
        rightPupil.setVisible(true);
        leftPupil.setCenterX(40);
        leftPupil.setCenterY(50.5);
        rightPupil.setCenterX(60);
        rightPupil.setCenterY(50.5);

        leftHappyEye.setVisible(false);
        rightHappyEye.setVisible(false);
        leftClosedEye.setVisible(false);
        rightClosedEye.setVisible(false);

        sleepZ.setVisible(false);
        sleepZ.setOpacity(0);

        laptopGroup.setVisible(false);
        thoughtGroup.setVisible(false);
        notepadGroup.setVisible(false);
        toolGroup.setVisible(false);
        celebrationSparkle.setVisible(false);

        leftPaw.setCenterX(34);
        leftPaw.setCenterY(74);
        leftPaw.setTranslateX(0);
        leftPaw.setTranslateY(0);

        rightPaw.setCenterX(62);
        rightPaw.setCenterY(74);
        rightPaw.setTranslateX(0);
        rightPaw.setTranslateY(0);

        badge.setVisible(false);
        antennaHalo.setVisible(false);

        if (!hasCustomImage) {
            avatar.setVisible(true);
        }
    }
}
