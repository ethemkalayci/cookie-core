package be.cookies.studio.core.service;

import javafx.scene.image.Image;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Décodage hors thread JavaFX ; six images de 1920 × 1080 au maximum en cache. */
public final class ArtworkService {
    public static final String FALLBACK = Objects.requireNonNull(ArtworkService.class.getResource("/images/banners/default-background.png")).toExternalForm();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, r -> { var t = new Thread(r, "artwork-decode"); t.setDaemon(true); return t; });
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(8, .75f, true);
    private static final Map<String, CompletableFuture<Image>> PENDING = new HashMap<>();
    private ArtworkService() {}
    public static synchronized CompletableFuture<Image> load(String source) {
        String key = source == null || source.isBlank() ? FALLBACK : source;
        if (CACHE.containsKey(key)) return CompletableFuture.completedFuture(CACHE.get(key));
        if (PENDING.containsKey(key)) return PENDING.get(key);
        if (PENDING.size() >= 12) return CompletableFuture.completedFuture(null);
        var result = new CompletableFuture<Image>();
        PENDING.put(key, result);
        WORKERS.execute(() -> {
            Image image = null;
            try {
                String resolved = key;
                if (key.startsWith("https://")) resolved = new NewsImageCacheService().resolve(key).get(20, TimeUnit.SECONDS).toUri().toString();
                URI uri = URI.create(resolved);
                if (!Set.of("file", "jar").contains(uri.getScheme())) throw new IllegalArgumentException("Source non locale");
                try (var input = uri.toURL().openStream()) {
                    image = new Image(input, 1920, 1080, true, true);
                    if (image.isError() || image.getWidth() <= 0) image = null;
                }
            } catch (Exception ignored) { /* Le composant choisit le fond de secours. */ }
            synchronized (ArtworkService.class) {
                PENDING.remove(key);
                if (image != null) { CACHE.put(key, image); while (CACHE.size() > 6) CACHE.remove(CACHE.keySet().iterator().next()); }
            }
            result.complete(image);
        });
        return result;
    }
}
