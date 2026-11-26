package io.taskbeacon.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BundledPetPacksTest {
    @TempDir Path skins;

    @Test void allDogAndCatBreedsAreBundledAndExtractable() throws Exception {
        assertEquals(8, BundledPetPacks.DOGS.size());
        assertEquals(6, BundledPetPacks.CATS.size());
        assertEquals("Akita", BundledPetPacks.find(BundledPetPacks.DEFAULT_ID).orElseThrow().label());
        assertEquals("Border Collie", BundledPetPacks.find("Dog-7-Sprite").orElseThrow().label());

        for (BundledPetPacks.Pack pack : Stream.concat(
                BundledPetPacks.DOGS.stream(), BundledPetPacks.CATS.stream()).toList()) {
            Path idle = BundledPetPacks.install(pack, skins);
            assertTrue(Files.isRegularFile(idle), pack.id());
            try (Stream<Path> images = Files.list(idle.getParent())) {
                assertTrue(images.filter(file -> file.getFileName().toString().endsWith(".png"))
                        .count() >= 10, pack.id());
            }
        }
    }

    @Test void bothLicensesAndExtraPackArtworkRemainIncluded() throws Exception {
        for (String category : new String[]{"dogs", "cats"}) {
            try (InputStream license = BundledPetPacks.class.getResourceAsStream(
                    "/pets/" + category + "/License.txt")) {
                assertNotNull(license, category);
                assertTrue(new String(license.readAllBytes(), StandardCharsets.UTF_8).contains("CC-0"));
            }
        }
        assertNotNull(BundledPetPacks.class.getResource("/pets/cats/Meow-VFX/Meow-VFX.png"));
        assertNotNull(BundledPetPacks.class.getResource("/pets/dogs/Preview.png"));
    }

    @Test void everyPackHasAChineseMenuName() {
        for (BundledPetPacks.Pack pack : java.util.stream.Stream.concat(
                BundledPetPacks.DOGS.stream(), BundledPetPacks.CATS.stream()).toList()) {
            assertFalse(pack.chineseLabel().isBlank(), pack.id());
            assertEquals(pack.chineseLabel() + " / " + pack.label(), pack.menuLabel());
        }
        assertEquals("\u79cb\u7530\u72ac / Akita", BundledPetPacks.find(BundledPetPacks.DEFAULT_ID).orElseThrow().menuLabel());
    }

    @Test void catLabelsMatchTheOriginalSpritesWithoutChangingSavedIds() {
        assertEquals(java.util.List.of("Orange", "Black", "Siamese", "Tiger",
                        "Ragdoll", "American Shorthair"),
                BundledPetPacks.CATS.stream().map(BundledPetPacks.Pack::label).toList());
        assertEquals(java.util.List.of("Cat-1", "Cat-2", "Cat-3", "Cat-4", "Cat-5", "Cat-6"),
                BundledPetPacks.CATS.stream().map(BundledPetPacks.Pack::id).toList());
    }
}
