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

    record Pack(String id, String label, String category, String idleFile) {
        String resourceDirectory() {
            return "pets/" + category + "/" + id + "/";
        }
    }

    static final List<Pack> DOGS = List.of(
            new Pack("Dog-1-Golden-Retriever", "Golden Retriever", "dogs", "Golden-Retriever-idle.png"),
            new Pack(DEFAULT_ID, "Akita", "dogs", "Akita-Idle.png"),
            new Pack("Dog-3-Great-Dane", "Great Dane", "dogs", "Great-Dane-idle.png"),
            new Pack("Dog-4-Schnauzer", "Schnauzer", "dogs", "Schnauzer-Idle.png"),
            new Pack("Dog-5-Saint-Bernard", "Saint Bernard", "dogs", "Saint-Bernard-Idle.png"),
            new Pack("Dog-6-Siberian-Husky", "Siberian Husky", "dogs", "Siberian-Husky-Idle.png"),
            new Pack("Dog-7-Sprite", "Border Collie", "dogs", "Border-Collie-Idle-f10.png")
    );
    static final List<Pack> CATS = List.of(
            new Pack("Cat-1", "Orange", "cats", "Cat-1-Idle.png"),
            new Pack("Cat-2", "Black", "cats", "Cat-2-Idle.png"),
            new Pack("Cat-3", "Siamese", "cats", "Cat-3-Idle.png"),
            new Pack("Cat-4", "Brown", "cats", "Cat-4-Idle.png"),
            new Pack("Cat-5", "Silver Longhair", "cats", "Cat-5-Idle.png"),
            new Pack("Cat-6", "Silver Tabby", "cats", "Cat-6-Idle.png")
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
