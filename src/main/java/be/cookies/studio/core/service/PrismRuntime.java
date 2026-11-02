package be.cookies.studio.core.service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

/** Pinned official runtime: never use the Windows-registered personal Prism executable. */
public final class PrismRuntime {
    static final String SHA256 = "05841d0b3bfc0a8212658457cc6035e1c29cd7ffae919464e874e384cbe8d062";
    static final String URL = "https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Windows-MinGW-w64-Portable-11.1.1.zip";
    public static Path executable(Path data) { return data.getParent().resolve("prism-runtime-11.1.1").resolve("prismlauncher.exe"); }
    public static synchronized Path ensure(Path data) throws IOException {
        Path exe = executable(data);
        if (Files.isRegularFile(exe) && Files.exists(exe.getParent().resolve(".cookiecore-runtime"))) return exe;
        if (!System.getProperty("os.name").startsWith("Windows") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch")))
            throw new IOException("Cette version de Cookie Core nécessite Windows x64.");
        Files.createDirectories(data.getParent());
        if (Files.exists(exe.getParent())) throw new IOException("Le dossier du moteur Prism est incomplet. Renomme prism-runtime-11.1.1 pour réessayer.");
        Path staging = Files.createTempDirectory(data.getParent(), ".prism-runtime-");
        Path archive = Files.createTempFile(data.getParent(), "prism-runtime-", ".zip");
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
            var request = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofMinutes(4)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body(); var output = Files.newOutputStream(archive)) {
                if (response.statusCode() != 200) throw new IOException("Téléchargement Prism impossible (HTTP " + response.statusCode() + ").");
                byte[] buffer = new byte[65536]; long total = 0; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    total += n; if (total > 100_000_000) throw new IOException("Archive Prism trop volumineuse.");
                    output.write(buffer, 0, n);
                }
            }
            verify(archive);
            extract(archive, staging);
            if (!Files.isRegularFile(staging.resolve("prismlauncher.exe"))) throw new IOException("Exécutable Prism absent de l'archive.");
            Files.writeString(staging.resolve(".cookiecore-runtime"), SHA256);
            Files.move(staging, exe.getParent());
            return exe;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Préparation de Prism annulée.");
        } finally {
            Files.deleteIfExists(archive);
            if (Files.exists(staging)) try (var paths = Files.walk(staging)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    static void verify(Path archive) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(archive)) { byte[] b = new byte[65536]; int n; while ((n=input.read(b))!=-1) digest.update(b,0,n); }
            if (!HexFormat.of().formatHex(digest.digest()).equals(SHA256)) throw new IOException("L'archive Prism ne correspond pas à l'empreinte officielle.");
        } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    static void extract(Path archive, Path directory) throws IOException {
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; long total = 0; int count = 0; Set<Path> seen = new HashSet<>();
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                Path target = directory.resolve(name).normalize();
                if (++count > 20000 || name.contains(":") || name.contains("\\") || !target.startsWith(directory) || !seen.add(target))
                    throw new IOException("Chemin d'archive Prism invalide.");
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n=zip.read(b))!=-1) { total+=n; if(total>500_000_000) throw new IOException("Archive Prism trop volumineuse."); output.write(b,0,n); }
                }
            }
        }
    }
}
