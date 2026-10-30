
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.OfficialInstance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class ModpackDownloadService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Path downloadModpack(OfficialInstance instance)
            throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "L'instance officielle ne peut pas être null."
            );
        }

        if (!instance.isDownloadAvailable()) {
            throw new IllegalArgumentException(
                    "Aucun téléchargement disponible pour "
                            + instance.getName()
            );
        }

        Path tempFile = Files.createTempFile(
                "cookiecore-modpack-",
                ".zip"
        );

        boolean completed = false;

        try {
            URI downloadUri = URI.create(instance.getDownloadUrl());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(downloadUri)
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(tempFile)
            );

            if (response.statusCode() != 200) {
                throw new IOException(
                        "Échec du téléchargement du modpack. HTTP : "
                                + response.statusCode()
                );
            }

            // Calcul de l'empreinte du fichier téléchargé
            String actualSha256 = calculateSha256(tempFile);

            // Comparaison avec l'empreinte du catalogue
            if (!actualSha256.equalsIgnoreCase(instance.getSha256())) {
                throw new IOException(
                        "L'intégrité du modpack "
                                + instance.getName()
                                + " n'a pas pu être vérifiée."
                );
            }

            completed = true;

            return tempFile;

        } finally {
            // Nettoyage si téléchargement ou vérification échoue
            if (!completed) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    // Le fichier temporaire pourra nécessiter
                    // un nettoyage ultérieur.
                }
            }
        }
    }

    private String calculateSha256(Path file) throws IOException {

        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 n'est pas disponible.", e
            );
        }

        try (InputStream inputStream = new DigestInputStream(
                Files.newInputStream(file),
                digest
        )) {
            byte[] buffer = new byte[8192];

            while (inputStream.read(buffer) != -1) {
                // La lecture alimente automatiquement le digest.
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}
