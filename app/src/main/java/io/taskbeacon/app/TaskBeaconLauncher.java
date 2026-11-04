package io.taskbeacon.app;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class TaskBeaconLauncher {
    private static FileChannel lockChannel;
    private static FileLock instanceLock;

    private TaskBeaconLauncher() { }

    public static void main(String[] args) {
        if (!acquireInstanceLock()) return;
        try {
            TaskBeaconApplication.main(args);
        } finally {
            releaseInstanceLock();
        }
    }

    private static boolean acquireInstanceLock() {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path directory = localAppData == null || localAppData.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".taskbeacon")
                    : Path.of(localAppData, "TaskBeacon");
            Files.createDirectories(directory);
            lockChannel = FileChannel.open(directory.resolve("taskbeacon.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            instanceLock = lockChannel.tryLock();
            if (instanceLock != null) return true;
            lockChannel.close();
            lockChannel = null;
            return false;
        } catch (OverlappingFileLockException error) {
            closeChannelQuietly();
            return false;
        } catch (IOException error) {
            // A lock failure must not make the application impossible to start.
            closeChannelQuietly();
            return true;
        }
    }

    private static void releaseInstanceLock() {
        try {
            if (instanceLock != null && instanceLock.isValid()) instanceLock.release();
        } catch (IOException ignored) {
        } finally {
            instanceLock = null;
            closeChannelQuietly();
        }
    }

    private static void closeChannelQuietly() {
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) {
        } finally {
            lockChannel = null;
        }
    }
}
