package be.cookies.studio.core.service;

import be.cookies.studio.core.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Stores a validated snapshot, so the home page works without the online catalog. */
public final class LastInstanceService {
    public record Entry(OfficialInstance instance, Instant launchedAt) {}
    private final Path file;
    public LastInstanceService() { this(new PrismLauncherService().getDataDirectory().getParent()); }
    public LastInstanceService(Path root) { file = root.resolve("last-instance.properties"); }
    public synchronized void save(OfficialInstance i, Instant time) throws IOException {
        Properties p = new Properties();
        p.setProperty("format", "1"); p.setProperty("id", Integer.toString(i.getId()));
        p.setProperty("name", i.getName()); p.setProperty("minecraft", i.getMinecraftVersion());
        p.setProperty("pack", i.getModpackVersion()); p.setProperty("loader", i.getModLoader().name());
        p.setProperty("loaderVersion", Objects.toString(i.getLoaderVersion(), ""));
        p.setProperty("image", Objects.toString(i.getImagePath(), ""));
        p.setProperty("download", i.getDownloadUrl()); p.setProperty("sha256", i.getSha256());
        p.setProperty("launchedAt", time.toString());
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".last-instance-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { p.store(out, "Cookie Core last launch request"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public Optional<Entry> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > 65536) throw new IOException("Historique de lancement trop volumineux.");
        var p = new Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); }
        try {
            if (!"1".equals(p.getProperty("format"))) throw new IllegalArgumentException();
            var i = new OfficialInstance(Integer.parseInt(p.getProperty("id")), p.getProperty("minecraft"),
                    p.getProperty("pack"), ModLoader.valueOf(p.getProperty("loader")), p.getProperty("loaderVersion"),
                    p.getProperty("name"), p.getProperty("image"), p.getProperty("download"), p.getProperty("sha256"));
            return Optional.of(new Entry(i, Instant.parse(p.getProperty("launchedAt"))));
        } catch (RuntimeException e) { throw new IOException("Historique de lancement illisible."); }
    }
}
