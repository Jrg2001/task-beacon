package io.taskbeacon.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Pet sprite packs bundled with TaskBeacon. The original CC0 licenses are included in resources. */
final class BundledPetPacks {
    static final String DEFAULT_ID = "Dog-2-Akita";

    /**
     * {@code label} is the English name; the menu shows {@code chineseLabel} first. {@code sizeFactor} scales
     * the pet's 100% size for breeds the artwork draws too big (tools/readme/make_readme_media.py mirrors it).
     */
    record Pack(String id, String label, String chineseLabel, String category, String idleFile, double sizeFactor) {
        Pack(String id, String label, String chineseLabel, String category, String idleFile) {
            this(id, label, chineseLabel, category, idleFile, 1.0);
        }

        String menuLabel() {
            return chineseLabel + " / " + label;
        }

        String resourceDirectory() {
            return "pets/" + category + "/" + id + "/";
        }
    }

    static final List<Pack> DOGS = List.of(
            new Pack("Dog-1-Golden-Retriever", "Golden Retriever", "\u91d1\u6bdb", "dogs", "Golden-Retriever-idle.png"),
            new Pack(DEFAULT_ID, "Akita", "\u79cb\u7530\u72ac", "dogs", "Akita-Idle.png"),
            // Id kept for saved preferences; artwork is the CC0 Great Dane set recoloured as a Weimaraner.
            new Pack("Dog-3-Great-Dane", "Weimaraner", "\u9b4f\u739b\u72ac", "dogs", "Weimaraner-idle.png"),
            new Pack("Dog-4-Schnauzer", "Schnauzer", "\u96ea\u7eb3\u745e", "dogs", "Schnauzer-Idle.png",
                    0.75),   // a small breed; the artwork draws it taller than the Akita
            new Pack("Dog-5-Saint-Bernard", "Saint Bernard", "\u5723\u4f2f\u7eb3", "dogs", "Saint-Bernard-Idle.png"),
            new Pack("Dog-6-Siberian-Husky", "Siberian Husky", "\u54c8\u58eb\u5947", "dogs", "Siberian-Husky-Idle.png"),
            // Id kept for saved preferences; artwork is the CC0 Husky set recoloured to match the pack style.
            new Pack("Dog-7-Sprite", "Border Collie", "\u8fb9\u5883\u7267\u7f8a\u72ac", "dogs", "Border-Collie-Idle.png")
    );
    static final List<Pack> CATS = List.of(
            new Pack("Cat-1", "Orange", "\u6a58\u732b", "cats", "Cat-1-Idle.png"),
            new Pack("Cat-2", "Black", "\u9ed1\u732b", "cats", "Cat-2-Idle.png"),
            new Pack("Cat-3", "Siamese", "\u66b9\u7f57\u732b", "cats", "Cat-3-Idle.png"),
            // Id kept for saved preferences; artwork is the CC0 grey-and-beige cat recoloured as a golden tiger cub.
            new Pack("Cat-4", "Tiger", "\u5c0f\u8001\u864e", "cats", "Cat-4-Idle.png"),
            // Id kept for saved preferences; artwork is the CC0 silver longhair recoloured as a Ragdoll.
            new Pack("Cat-5", "Ragdoll", "\u5e03\u5076\u732b", "cats", "Cat-5-Idle.png"),
            // Id kept for saved preferences; artwork is the CC0 silver tabby recoloured as an American Shorthair.
            new Pack("Cat-6", "American Shorthair", "\u7f8e\u56fd\u77ed\u6bdb\u732b", "cats", "Cat-6-Idle.png")
    );

    private BundledPetPacks() { }

    static Optional<Pack> find(String id) {
        if (id == null) return Optional.empty();
        return Stream.concat(DOGS.stream(), CATS.stream())
                .filter(pack -> pack.id().equals(id)).findFirst();
    }

    static Path install(Pack pack, Path skinsDirectory) throws IOException {
        String prefix = pack.resourceDirectory();
        URL idleResource = BundledPetPacks.class.getResource("/" + prefix + pack.idleFile());
        if (idleResource == null) throw new IOException("Bundled pet not found: " + pack.id());

        Path target = skinsDirectory.resolve("bundled").resolve(pack.category()).resolve(pack.id());
        Files.createDirectories(target);
        clearPreviousInstall(target);
        if (idleResource.getProtocol().equals("jar")) {
            copyFromJar(idleResource, prefix, target);
        } else if (idleResource.getProtocol().equals("file")) {
            copyFromDirectory(idleResource, target);
        } else {
            throw new IOException("Unsupported pet resource: " + idleResource.getProtocol());
        }
        Path idle = target.resolve(pack.idleFile());
        if (!Files.isRegularFile(idle)) throw new IOException("Bundled pet idle image missing: " + pack.id());
        return idle;
    }

    /**
     * Sprite states are matched by file-name keywords, so files left over from an older version of a pack
     * (e.g. renamed artwork) could shadow the new ones. The target directory is owned by TaskBeacon.
     */
    private static void clearPreviousInstall(Path target) throws IOException {
        try (Stream<Path> files = Files.list(target)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) Files.delete(file);
        }
    }

    private static void copyFromJar(URL resource, String prefix, Path target) throws IOException {
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                String fileName = entry.getName().substring(prefix.length());
                if (fileName.isEmpty() || fileName.contains("/")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, target.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyFromDirectory(URL resource, Path target) throws IOException {
        Path directory;
        try {
            directory = Path.of(resource.toURI()).getParent();
        } catch (URISyntaxException error) {
            throw new IOException("Invalid bundled pet path", error);
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
