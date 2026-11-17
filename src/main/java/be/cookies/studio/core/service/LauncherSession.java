package be.cookies.studio.core.service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Per-data-directory GUI lock; also interoperates with the former installation's locks.
 * Never activates, downloads or removes an application version or user data. */
public final class LauncherSession implements AutoCloseable {
    private final List<FileChannel> channels = new ArrayList<>();
    private final List<FileLock> locks = new ArrayList<>();

    public static LauncherSession acquire() throws IOException {
        Path data = new PrismLauncherService().getDataDirectory().getParent();
        String local = System.getenv("LOCALAPPDATA");
        Path legacy = local == null || local.isBlank() ? null : Path.of(local, "Programs", "CookieCore");
        return acquire(data, legacy);
    }

    static LauncherSession acquire(Path data, Path legacy) throws IOException {
        var session = new LauncherSession();
        try {
            Files.createDirectories(data);
            session.lock(data.resolve("launcher.lock"));
            // Hold both legacy locks for our full lifetime: an old supervisor cannot
            // start alongside us, and an orphan old GUI is detected as well.
            if (legacy != null && Files.isRegularFile(legacy.resolve("current"))) {
                session.lock(legacy.resolve("bootstrap.lock"));
                session.lock(legacy.resolve("app.lock"));
            }
            return session;
        } catch (IOException | RuntimeException error) {
            session.close();
            throw error;
        }
    }

    private void lock(Path file) throws IOException {
        var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        channels.add(channel);
        try {
            var lock = channel.tryLock();
            if (lock == null) throw new IOException("Fermez l’autre Cookie Core (y compris l’ancienne version), puis réessayez.");
            locks.add(lock);
        } catch (OverlappingFileLockException error) {
            throw new IOException("Cookie Core est déjà ouvert pour ces données.", error);
        }
    }

    @Override public void close() {
        for (int i = locks.size() - 1; i >= 0; i--) try { locks.get(i).close(); } catch (IOException ignored) { }
        for (int i = channels.size() - 1; i >= 0; i--) try { channels.get(i).close(); } catch (IOException ignored) { }
        locks.clear(); channels.clear();
    }
}