
package be.cookies.studio.core.service;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewsImageCacheService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(3, task -> {
                Thread thread = new Thread(task, "news-image-loader");
                thread.setDaemon(true);
                return thread;
            });

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Path cacheDirectory;

    public NewsImageCacheService() {

        String localAppData = System.getenv("LOCALAPPDATA");

        Path baseDirectory;

        if (localAppData != null && !localAppData.isBlank()) {
            baseDirectory = Path.of(localAppData);
        } else {
            baseDirectory = Path.of(
                    System.getProperty("user.home"),
                    ".cookiecore"
            );
        }

        cacheDirectory = baseDirectory.resolve(
                Path.of("CookiesStudio", "CookieCore", "cache", "images")
        );
    }

    NewsImageCacheService(Path directory) { cacheDirectory=directory; }

    static boolean allowed(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && "ethemkalayci.github.io".equalsIgnoreCase(uri.getHost())
            && uri.getPort()==-1 && uri.getUserInfo()==null && uri.equals(uri.normalize())
            && (uri.getPath().startsWith("/cookie-core-data/news/images/") || uri.getPath().startsWith("/cookie-core-data/instances/images/"));
    }

    public CompletableFuture<Path> resolve(String imageUrl) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadOrDownload(imageUrl);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CompletionException(e);
            }
        }, EXECUTOR);
    }

    private Path loadOrDownload(String imageUrl)
            throws IOException, InterruptedException {

        URI uri;

        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException e) {
            throw new IOException("URL d'image invalide", e);
        }

        // Accepter uniquement les images du dépôt officiel.
        if (!allowed(uri)) {

            throw new IOException("Source d'image non autorisée");
        }

        String extension = uri.getPath()
                .toLowerCase()
                .endsWith(".png") ? ".png" : ".jpg";

        String fileName = sha256(imageUrl) + extension;
        Path cacheFile = cacheDirectory.resolve(fileName);

        // Une image déjà enregistrée est réutilisée.
        if (Files.isRegularFile(cacheFile)) {
            return cacheFile;
        }

        Files.createDirectories(cacheDirectory);

        Path temporaryFile = Files.createTempFile(
                cacheDirectory, "image-", ".tmp"
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try (InputStream input = response.body()) {

                if (response.statusCode() != 200) {
                    throw new IOException(
                            "Erreur HTTP image : " + response.statusCode()
                    );
                }

                try (OutputStream output = Files.newOutputStream(
                        temporaryFile)) {

                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;

                    while ((count = input.read(buffer)) != -1) {

                        total += count;

                        if (total > MAX_IMAGE_SIZE) {
                            throw new IOException("Image trop volumineuse");
                        }

                        output.write(buffer, 0, count);
                    }
                }
            }

            // Vérifier que le fichier est une image lisible.
            try (InputStream imageInput =
                         Files.newInputStream(temporaryFile)) {

                if (ImageIO.read(imageInput) == null) {
                    throw new IOException("Format d'image invalide");
                }
            }

            CacheFiles.publish(temporaryFile, cacheFile, false);

            return cacheFile;

        } finally {
            CacheFiles.cleanup(temporaryFile);
        }
    }

    private String sha256(String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 indisponible", e
            );
        }
    }
}

