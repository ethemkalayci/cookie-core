package be.cookies.studio.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LauncherSessionTest {
    @TempDir Path root;
    @Test void secondGuiIsRejectedAndLockReleased() throws Exception {
        Path data = root.resolve("data");
        try (var first = LauncherSession.acquire(data, null)) {
            assertThrows(IOException.class, () -> LauncherSession.acquire(data, null));
        }
        try (var next = LauncherSession.acquire(data, null)) { assertNotNull(next); }
    }
    @Test void legacySupervisorAndOrphanGuiBlockLaunchWithoutChangingData() throws Exception {
        Path legacy = root.resolve("old"); Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("current"), "1.0.0");
        Path data = root.resolve("data"); Files.createDirectories(data.resolve("prism-data"));
        Files.writeString(data.resolve("settings.json"), "{\"theme\":\"custom\"}");
        Files.writeString(data.resolve("prism-data/accounts.json"), "{\"accounts\":[]}");
        for (String name : new String[]{"bootstrap.lock", "app.lock"}) {
            try (var channel = FileChannel.open(legacy.resolve(name), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                assertThrows(IOException.class, () -> LauncherSession.acquire(data, legacy));
            }
            try (var session = LauncherSession.acquire(data, legacy)) {
                assertEquals("1.0.0", Files.readString(legacy.resolve("current")));
                assertEquals("{\"theme\":\"custom\"}", Files.readString(data.resolve("settings.json")));
                assertEquals("{\"accounts\":[]}", Files.readString(data.resolve("prism-data/accounts.json")));
                try (var channel = FileChannel.open(legacy.resolve("bootstrap.lock"), StandardOpenOption.WRITE)) {
                    assertThrows(java.nio.channels.OverlappingFileLockException.class, channel::tryLock);
                }
            }
        }
    }
    @Test void absentLegacyInstallIsNotCreated() throws Exception {
        Path legacy = root.resolve("not-installed");
        try (var session = LauncherSession.acquire(root.resolve("data"), legacy)) { assertFalse(Files.exists(legacy)); }
    }
}