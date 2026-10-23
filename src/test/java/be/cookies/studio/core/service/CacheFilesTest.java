package be.cookies.studio.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CacheFilesTest {
    @TempDir Path directory;
    Path temporary(String text) throws IOException {
        Path file = Files.createTempFile(directory, "cache-", ".tmp");
        return Files.writeString(file, text);
    }
    @Test void replacesExistingJsonRepeatedly() throws Exception {
        Path target = directory.resolve("news.json");
        for (int i = 0; i < 30; i++) {
            CacheFiles.publish(temporary("{\"version\":" + i + "}"), target, true);
            assertEquals("{\"version\":" + i + "}", Files.readString(target));
        }
    }
    @Test void concurrentJsonWritersPublishCompleteFiles() throws Exception {
        Path target = directory.resolve("news.json");
        Set<String> values = new HashSet<>();
        List<Future<?>> tasks = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) {
                String value = i + "x".repeat(10000);
                values.add(value);
                tasks.add(pool.submit(() -> {
                    Path source = temporary(value);
                    try { CacheFiles.publish(source, target, true); }
                    finally { CacheFiles.cleanup(source); }
                    return null;
                }));
            }
            for (Future<?> task : tasks) task.get();
        }
        assertTrue(values.contains(Files.readString(target)));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void concurrentImagesReuseFirstPublishedFile() throws Exception {
        Path target = directory.resolve("image.png");
        List<Future<?>> tasks = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) {
                tasks.add(pool.submit(() -> {
                    Path source = temporary("image");
                    try { CacheFiles.publish(source, target, false); }
                    finally { CacheFiles.cleanup(source); }
                    return null;
                }));
            }
            for (Future<?> task : tasks) task.get();
        }
        Path other = temporary("different");
        CacheFiles.publish(other, target, false);
        CacheFiles.cleanup(other);
        assertEquals("image", Files.readString(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void temporaryAccessDeniedIsRetried() throws Exception {
        Path target = Files.writeString(directory.resolve("news.json"), "old");
        Files.setAttribute(target, "dos:readonly", true);
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> unlock = pool.submit(() -> {
                Thread.sleep(180);
                Files.setAttribute(target, "dos:readonly", false);
                return null;
            });
            try {
                CacheFiles.publish(temporary("new"), target, true);
                unlock.get();
                assertEquals("new", Files.readString(target));
            } finally { Files.setAttribute(target, "dos:readonly", false); }
        }
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void permanentAccessDeniedPreservesOldCache() throws Exception {
        Path target = Files.writeString(directory.resolve("news.json"), "old");
        Path source = temporary("new");
        Files.setAttribute(target, "dos:readonly", true);
        try {
            assertThrows(AccessDeniedException.class, () -> CacheFiles.publish(source, target, true));
            assertEquals("old", Files.readString(target));
            assertTrue(Files.exists(source));
        } finally {
            Files.setAttribute(target, "dos:readonly", false);
            CacheFiles.cleanup(source);
        }
    }
}
