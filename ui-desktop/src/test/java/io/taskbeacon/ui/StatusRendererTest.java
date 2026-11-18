package io.taskbeacon.ui;

import io.taskbeacon.core.model.*;
import io.taskbeacon.core.reducer.DefaultTaskStateReducer;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatusRendererTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialized
        }
    }

    @Test
    void renderersHaveValidIdsAndDisplayNames() {
        CompactCardRenderer cardRenderer = new CompactCardRenderer();
        assertEquals("compact-card", cardRenderer.id());
        assertEquals("Compact Card", cardRenderer.displayName());

        PetRenderer petRenderer = new PetRenderer();
        assertEquals("pet", petRenderer.id());
        assertEquals("Desktop Pet", petRenderer.displayName());
    }

    @Test
    void sourcePaletteDistinguishesClaudeFromCodex() {
        assertEquals("#72a7ff", SourcePalette.accent("Codex"));
        assertEquals("#f59e0b", SourcePalette.accent("Claude Code"));
    }

    @Test
    void callbacksCanBeConfigured() {
        CompactCardRenderer cardRenderer = new CompactCardRenderer();
        AtomicBoolean cardShowCalled = new AtomicBoolean();
        AtomicBoolean cardSwitchCalled = new AtomicBoolean();
        AtomicBoolean cardQuitCalled = new AtomicBoolean();
        cardRenderer.setOnShowPanel(() -> cardShowCalled.set(true));
        cardRenderer.setOnSwitchMode(() -> cardSwitchCalled.set(true));
        cardRenderer.setOnQuit(() -> cardQuitCalled.set(true));

        PetRenderer petRenderer = new PetRenderer();
        AtomicBoolean petShowCalled = new AtomicBoolean();
        AtomicBoolean petSwitchCalled = new AtomicBoolean();
        AtomicBoolean petQuitCalled = new AtomicBoolean();
        petRenderer.setOnShowPanel(() -> petShowCalled.set(true));
        petRenderer.setOnSwitchMode(() -> petSwitchCalled.set(true));
        petRenderer.setOnQuit(() -> petQuitCalled.set(true));

        assertNotNull(cardRenderer);
        assertNotNull(petRenderer);
    }

    @Test
    void renderersHandleUpdateAndIdleGracefullyOnFxThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                CompactCardRenderer cardRenderer = new CompactCardRenderer();
                PetRenderer petRenderer = new PetRenderer();
                cardRenderer.create();
                petRenderer.create();

                Instant start = Instant.parse("2026-09-10T00:00:00Z");
                Clock clock = Clock.fixed(start.plusSeconds(30), ZoneOffset.UTC);
                TaskViewModelFactory factory = new TaskViewModelFactory(clock);
                DefaultTaskStateReducer reducer = new DefaultTaskStateReducer();

                TaskKey key = new TaskKey("demo", "test1");
                TaskEvent event = TaskEvent.of(key, TaskRelation.root(TaskScope.RUN, key),
                        EventType.TASK_STARTED, start, ObservationMode.EXTERNAL,
                        EvidenceQuality.STRONG, ProgressImpact.MEANINGFUL,
                        "Mock Task", "", Map.of("activity", Activity.EXECUTING_COMMAND, "activityLabel", "npm test"));
                TaskState state = reducer.reduce(null, event);
                TaskCardViewModel model = factory.create(state, 1);

                cardRenderer.update(model, null);
                petRenderer.update(model, null);

                cardRenderer.idle();
                petRenderer.idle();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void petAnimationControllerSupportsCustomImageLifecycle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                PetAnimationController controller = new PetAnimationController();
                assertFalse(controller.hasCustomImage());
                assertTrue(controller.isShadowVisible());

                // Non-existent file should gracefully fail and remain false
                assertFalse(controller.setCustomImage(new File("non_existent_image.png")));
                assertFalse(controller.hasCustomImage());
                assertTrue(controller.isShadowVisible());

                // Create a 1x1 dummy test file
                File tempImg = File.createTempFile("test_pet", ".png");
                tempImg.deleteOnExit();
                // Write minimal 1x1 transparent PNG bytes
                byte[] png1x1 = new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                        0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
                        0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
                        0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00,
                        0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
                };
                Files.write(tempImg.toPath(), png1x1);

                assertTrue(controller.setCustomImage(tempImg));
                assertTrue(controller.hasCustomImage());
                assertFalse(controller.isShadowVisible());

                controller.setCustomScale(1.5);
                assertEquals(1.5, controller.getCustomScale(), 0.001);

                controller.clearCustomImage();
                assertFalse(controller.hasCustomImage());
                assertTrue(controller.isShadowVisible());
            } catch (Exception e) {
                fail(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void petRendererScaleCanBeConfigured() throws Exception {
        Preferences testPreferences = Preferences.userRoot().node(
                "/io/taskbeacon/ui/scale-test-" + UUID.randomUUID());
        try {
            testPreferences.putDouble("pet.scale", 1.75);
            FutureTask<Void> task = new FutureTask<>(() -> {
                PetRenderer renderer = new PetRenderer(testPreferences);
                assertEquals(1.0, renderer.getCustomScale("Codex"), 0.001);
                assertEquals(1.0, renderer.getCustomScale("Claude Code"), 0.001);
                renderer.setCustomScale(1.25);
                assertEquals(1.25, renderer.getCustomScale(), 0.001);
                renderer.setCustomScale("Claude Code", 1.5);
                assertEquals(1.25, renderer.getCustomScale("Codex"), 0.001);
                assertEquals(1.5, renderer.getCustomScale("Claude Code"), 0.001);
                return null;
            });
            Platform.runLater(task);
            task.get(5, TimeUnit.SECONDS);
        } finally {
            testPreferences.removeNode();
        }
    }

    @Test
    void wideHuskySpriteFitsInsidePetWindow(@TempDir Path skins) throws Exception {
        Path huskyIdle = BundledPetPacks.install(
                BundledPetPacks.find("Dog-6-Siberian-Husky").orElseThrow(), skins);
        Preferences testPreferences = Preferences.userRoot().node(
                "/io/taskbeacon/ui/husky-size-test-" + UUID.randomUUID());
        try {
            testPreferences.put("pet.codex.customImage", huskyIdle.toString());
            testPreferences.put("pet.claude.customImage", huskyIdle.toString());
            FutureTask<Void> task = new FutureTask<>(() -> {
                PetRenderer renderer = new PetRenderer(testPreferences);
                VBox root = (VBox) renderer.create();
                Region pet = (Region) root.getChildren().getFirst();
                assertTrue(pet.getPrefWidth() > PetAnimationController.PET_WIDTH);
                assertTrue(root.getPrefWidth() >= pet.getPrefWidth()
                        + root.getPadding().getLeft() + root.getPadding().getRight());
                assertTrue(pet.getMaxWidth() >= pet.getPrefWidth());
                return null;
            });
            Platform.runLater(task);
            task.get(5, TimeUnit.SECONDS);
        } finally {
            testPreferences.removeNode();
        }
    }

    @Test
    void borderCollieUsesSameSpriteLayoutAsOtherDogs(@TempDir Path skins) throws Exception {
        Path stale = Files.createDirectories(skins.resolve("bundled/dogs/Dog-7-Sprite"))
                .resolve("Border-Collie-Run-f8.png");
        Files.writeString(stale, "old artwork");
        Path idle = BundledPetPacks.install(
                BundledPetPacks.find("Dog-7-Sprite").orElseThrow(), skins);
        assertFalse(Files.exists(stale), "files from an older pack version must not shadow new sprites");
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                PetAnimationController controller = new PetAnimationController();
                assertTrue(controller.setCustomImage(idle.toFile()));
                assertEquals("Border-Collie-Idle.png", controller.currentCustomFileName());
                assertEquals(10, controller.currentSpriteFrameCount());
                controller.playManualAction(PetAnimationController.ManualAction.BARK);
                assertEquals("Border-Collie-bark.png", controller.currentCustomFileName());
                assertEquals(3, controller.currentSpriteFrameCount());
                controller.playManualAction(PetAnimationController.ManualAction.LICK);
                assertEquals("Border-Collie-licking1.png", controller.currentCustomFileName());
                controller.playManualAction(PetAnimationController.ManualAction.STRETCH);
                assertEquals("Border-Collie-stretching.png", controller.currentCustomFileName());
                controller.playManualAction(PetAnimationController.ManualAction.RUN);
                assertEquals("Border-Collie-run.png", controller.currentCustomFileName());
                assertEquals(8, controller.currentSpriteFrameCount());
                controller.playManualAction(PetAnimationController.ManualAction.WALK);
                assertEquals("Border-Collie-walk.png", controller.currentCustomFileName());
                controller.playManualAction(PetAnimationController.ManualAction.ITCH);
                assertEquals("Border-Collie-itching.png", controller.currentCustomFileName());
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
    @Test
    void petRendererContainsOnlyThePetWithoutStatusBubble() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                PetRenderer renderer = new PetRenderer();
                VBox root = (VBox) renderer.create();
                VBox secondaryRoot = (VBox) renderer.createSecondary();
                assertEquals(1, root.getChildren().size());
                assertEquals(1, secondaryRoot.getChildren().size());
                assertNotSame(root.getChildren().getFirst(), secondaryRoot.getChildren().getFirst());
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void pixelPetPackUsesStableCropAndSpecificActivityBeforeGenericRunning() throws Exception {
        Path pack = Files.createTempDirectory("taskbeacon-pet-pack");
        File idle = writeSprite(pack, "Akita-Idle.png", 2);
        writeSprite(pack, "Akita-stretching.png", 2);
        writeSprite(pack, "Akita-walk.png", 2);
        writeSprite(pack, "Akita-run.png", 2);
        writeSprite(pack, "Akita-bark.png", 2);
        writeSprite(pack, "Akita-itching.png", 2);
        writeSprite(pack, "Akita-licking1.png", 2);

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                PetAnimationController controller = new PetAnimationController();
                assertTrue(controller.setCustomImage(idle));
                assertNotNull(controller.customPackCrop());
                assertEquals(0, controller.customPackCrop().getWidth() % 2, 0.001);
                assertEquals(0, controller.customPackCrop().getHeight() % 2, 0.001);

                controller.applyState(model("Thinking…", "", 1));
                assertEquals("Akita-stretching.png", controller.currentCustomFileName());

                controller.applyState(model("Using tool", "", 1));
                assertEquals("Akita-walk.png", controller.currentCustomFileName());

                controller.applyState(model("Possibly stalled", "", 1));
                assertEquals("Akita-itching.png", controller.currentCustomFileName());

                controller.applyState(model("Approval required", "!", 1));
                assertEquals("Akita-bark.png", controller.currentCustomFileName());

                controller.applyState(model("Completed", "", 0));
                assertEquals("Akita-licking1.png", controller.currentCustomFileName());

                controller.playManualAction(PetAnimationController.ManualAction.RUN);
                assertEquals("Akita-run.png", controller.currentCustomFileName());

                controller.playManualAction(PetAnimationController.ManualAction.BARK);
                assertEquals("Akita-bark.png", controller.currentCustomFileName());

                controller.playManualAction(PetAnimationController.ManualAction.SLEEP);
                assertEquals("Akita-Idle.png", controller.currentCustomFileName());
                controller.stop();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void bundledCatUsesMeowAndCatSpecificAnimationNames() throws Exception {
        Path idle = BundledPetPacks.install(BundledPetPacks.find("Cat-1").orElseThrow(),
                Files.createTempDirectory("taskbeacon-cat-pack"));
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                PetAnimationController controller = new PetAnimationController();
                assertTrue(controller.setCustomImage(idle.toFile()));
                controller.playManualAction(PetAnimationController.ManualAction.BARK);
                assertEquals("Cat-1-Meow.png", controller.currentCustomFileName());
                controller.playManualAction(PetAnimationController.ManualAction.LICK);
                assertEquals("Cat-1-Licking 1.png", controller.currentCustomFileName());
                controller.playManualAction(PetAnimationController.ManualAction.SLEEP);
                assertEquals("Cat-1-Sleeping1.png", controller.currentCustomFileName());
                controller.stop();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private static TaskCardViewModel model(String status, String badge, int activeCount) {
        return new TaskCardViewModel("", "Codex", "TaskBeacon", status,
                "00:10", badge, "", activeCount);
    }

    private static File writeSprite(Path directory, String name, int frames) throws Exception {
        BufferedImage image = new BufferedImage(frames * 100, 100, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < frames; frame++) {
            for (int x = 40; x < 60; x++) {
                for (int y = 43; y < 58; y++) {
                    image.setRGB(frame * 100 + x, y, 0xffffa040);
                }
            }
        }
        File file = directory.resolve(name).toFile();
        file.deleteOnExit();
        ImageIO.write(image, "png", file);
        return file;
    }
}
