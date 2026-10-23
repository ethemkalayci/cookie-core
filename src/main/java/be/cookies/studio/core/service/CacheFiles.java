package be.cookies.studio.core.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;

/** Publication de fichiers complets, avec attente bornée des verrous Windows. */
final class CacheFiles {
    private static final ReentrantLock[] LOCKS = new ReentrantLock[64];
    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }
    private CacheFiles() {}

    static void publish(Path temporary, Path target, boolean replace) throws IOException {
        Path key = target.toAbsolutePath().normalize();
        ReentrantLock lock = LOCKS[Math.floorMod(key.hashCode(), LOCKS.length)];
        lock.lock();
        try {
            for (int attempt = 0; ; attempt++) {
                // Images immuables : ne pas remplacer un fichier lu par JavaFX.
                if (!replace && Files.isRegularFile(target)) return;
                try {
                    if (replace) {
                        try {
                            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
                            // ATOMIC_MOVE peut ignorer REPLACE_EXISTING selon le fournisseur.
                            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        Files.move(temporary, target);
                    }
                    return;
                } catch (FileAlreadyExistsException | AccessDeniedException e) {
                    if (!replace && Files.isRegularFile(target)) return;
                    if (attempt == 5) throw e;
                    try {
                        Thread.sleep(50L << attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        java.io.InterruptedIOException failure = new java.io.InterruptedIOException(
                                "Écriture du cache interrompue");
                        failure.initCause(interrupted);
                        failure.addSuppressed(e);
                        throw failure;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    static void cleanup(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            // Ne masquer ni une publication réussie ni son erreur.
            System.getLogger(CacheFiles.class.getName()).log(System.Logger.Level.WARNING,
                    "Impossible de supprimer le fichier temporaire : " + temporary, e);
        }
    }
}
