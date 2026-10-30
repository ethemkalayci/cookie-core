package be.cookies.studio.core.service;

import be.cookies.studio.core.model.ModLoader;
import be.cookies.studio.core.model.OfficialInstance;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Gestion des instances officielles dans un répertoire Prism indépendant.
 * L'installation silencieuse prend en charge UNIQUEMENT une exportation d'instance
 * Prism/MultiMC contenant instance.cfg, mmc-pack.json et les JAR des mods.
 * Accepte les deux répertoires de jeu rencontrés selon les exports Prism :
 * minecraft/ et .minecraft/.
 * Un export CurseForge (manifest.json + overrides) ne contient pas ces JAR :
 * la CLI officielle --import ouvre un dialogue et ne sait pas les installer en silence.
 * Ce service ne modifie jamais les instances déjà existantes et ne gère pas
 * (encore) les mises à jour, ni l'installation de l'exécutable Prism.
 */
public class PrismLauncherService {

    private static final int MAX_ENTRIES = 30_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024;
    private static final String INSTALL_MARKER = ".cookiecore-install.properties";

    private final Path dataDirectory;

    public PrismLauncherService() {
        this(defaultRoot());
    }

    /** Constructeur injectable pour isoler les fichiers lors des tests. */
    public PrismLauncherService(Path cookieCoreRoot) {
        dataDirectory = cookieCoreRoot.toAbsolutePath().normalize().resolve("prism-data");
    }

    private static Path defaultRoot() {
        String local = System.getenv("LOCALAPPDATA");
        return local == null || local.isBlank()
                ? Path.of(System.getProperty("user.home"), ".cookiecore")
                : Path.of(local, "CookiesStudio", "CookieCore");
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    /** Le répertoire est indépendant de %APPDATA%/PrismLauncher. */
    public Optional<Path> findExecutable() {
        String override = System.getenv("COOKIECORE_PRISM_EXECUTABLE");
        if (override != null && !override.isBlank()) {
            Path candidate = Path.of(override);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        Path root = dataDirectory.getParent();
        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve("prism").resolve("prismlauncher.exe"));
        candidates.add(root.resolve("prism").resolve("PrismLauncher.exe"));
        String local = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (local != null) {
            candidates.add(Path.of(local, "Programs", "PrismLauncher", "prismlauncher.exe"));
            candidates.add(Path.of(local, "PrismLauncher", "prismlauncher.exe"));
        }
        if (programFiles != null)
            candidates.add(Path.of(programFiles, "PrismLauncher", "prismlauncher.exe"));
        if (programFilesX86 != null)
            candidates.add(Path.of(programFilesX86, "PrismLauncher", "prismlauncher.exe"));
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    private Path requireExecutable() throws IOException {
        return findExecutable().orElseThrow(() -> new IOException(
                "Prism Launcher introuvable. L'installation automatique de Prism n'est pas encore intégrée. "
                        + "Configure COOKIECORE_PRISM_EXECUTABLE ou installe Prism."));
    }

    /** Identifiant permanent : ne dépend ni du nom ni de la version du modpack. */
    private static String prismId(OfficialInstance instance) {
        return "cookiecore-official-" + instance.getId();
    }

    private Path instanceFolder(OfficialInstance instance) {
        return dataDirectory.resolve("instances").resolve(prismId(instance));
    }

    /** Un simple dossier Prism sans marqueur Cookie Core ne suffit PAS. */
    public Optional<String> installedInstanceId(OfficialInstance instance) throws IOException {
        Path folder = instanceFolder(instance);
        Path marker = folder.resolve(INSTALL_MARKER);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(folder.resolve("instance.cfg"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(folder.resolve("mmc-pack.json"), LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(marker)) {
            properties.load(input);
        }
        if (!Integer.toString(instance.getId()).equals(properties.getProperty("catalogId"))
                || !instance.getModpackVersion().equals(properties.getProperty("modpackVersion"))
                || !instance.getSha256().equalsIgnoreCase(properties.getProperty("sha256", ""))) {
            return Optional.empty();
        }
        if (instance.getModLoader() != ModLoader.VANILLA) {
            long currentModCount = countMods(folder);
            if (currentModCount == 0) return Optional.empty();
            String expectedModCount = properties.getProperty("modJarCount");
            if (expectedModCount != null
                    && !expectedModCount.equals(Long.toString(currentModCount))) {
                return Optional.empty();
            }
        }
        return Optional.of(prismId(instance));
    }

    /** Détecte une version déjà présente sans autoriser son écrasement. */
    public boolean hasManagedInstance(OfficialInstance instance) {
        return Files.exists(instanceFolder(instance), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Installe silencieusement un export Prism COMPLET, sans lancer --import.
     * L'archive provient du téléchargement HTTPS vérifié en SHA-256 par Cookie Core.
     * Aucun fichier existant n'est remplacé ; la publication n'a lieu qu'après validation.
     */
    public synchronized String installModpack(OfficialInstance instance, Path archive)
            throws IOException, InterruptedException {
        if (instance == null || !instance.isDownloadAvailable())
            throw new IOException("Cette instance ne possède pas de téléchargement disponible.");
        if (!Files.isRegularFile(archive))
            throw new IOException("Archive introuvable : " + archive);
        if (hasManagedInstance(instance))
            throw new IOException("Une instance Cookie Core existe déjà pour " + instance.getName()
                    + ". Aucune donnée n'a été écrasée. La mise à jour nécessite un mécanisme séparé.");

        Path instancesDir = dataDirectory.resolve("instances");
        Files.createDirectories(instancesDir);
        Path staging = Files.createTempDirectory(instancesDir, ".cookiecore-install-");
        boolean committed = false;
        try {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                List<? extends ZipEntry> entries = java.util.Collections.list(zip.entries());
                if (entries.size() > MAX_ENTRIES)
                    throw new IOException("Archive trop volumineuse : nombre de fichiers excessif.");

                // Valider TOUS les chemins avant de créer le premier fichier.
                Set<String> paths = new HashSet<>();
                for (ZipEntry entry : entries) {
                    String path = entry.getName();
                    validateEntryName(path);
                    if (!paths.add(path.toLowerCase(Locale.ROOT)))
                        throw new IOException("Chemin ZIP dupliqué : " + path);
                }

                String prefix = detectPrismExportPrefix(paths, entries);
                if (prefix == null) {
                    throw new IOException("L'archive publiée est un export CurseForge (ou un ZIP inconnu). "
                            + "L'installation silencieuse exige un export d'instance Prism COMPLET "
                            + "contenant instance.cfg, mmc-pack.json et les mods autorisés à la redistribution. "
                            + "L'option --import de Prism nécessite une confirmation graphique.");
                }

                long total = 0;
                for (ZipEntry entry : entries) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    String name = entry.getName();
                    if (!name.startsWith(prefix))
                        throw new IOException("Fichier inattendu en dehors de l'instance : " + name);
                    String relativeName = name.substring(prefix.length());
                    if (relativeName.isEmpty()) continue;
                    validateEntryName(relativeName);
                    // Les comptes et les données personnelles ne font pas partie d'un modpack.
                    if (containsPersonalData(relativeName))
                        throw new IOException("L'export contient des données personnelles : " + relativeName);
                    Path destination = staging.resolve(relativeName).normalize();
                    if (!destination.startsWith(staging))
                        throw new IOException("Chemin dangereux dans l'archive : " + name);
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    try (InputStream input = zip.getInputStream(entry);
                         var output = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                            total += read;
                            if (total > MAX_UNCOMPRESSED_BYTES)
                                throw new IOException("Archive dépassant la limite de 4 Gio décompressés.");
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }

            validateInstalledFiles(staging, instance);
            assignUniqueInstanceUuid(staging);
            Properties marker = new Properties();
            marker.setProperty("catalogId", Integer.toString(instance.getId()));
            marker.setProperty("modJarCount", Long.toString(countMods(staging)));
            marker.setProperty("modpackVersion", instance.getModpackVersion());
            marker.setProperty("sha256", instance.getSha256());
            marker.setProperty("minecraftVersion", instance.getMinecraftVersion());
            marker.setProperty("modLoader", instance.getModLoader().name());
            marker.setProperty("loaderVersion", instance.getLoaderVersion() == null
                    ? "" : instance.getLoaderVersion());
            try (var output = Files.newOutputStream(staging.resolve(INSTALL_MARKER))) {
                marker.store(output, "Instance officielle geree par Cookie Core");
            }

            Path target = instanceFolder(instance);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("L'instance existe déjà, aucun écrasement effectué : " + target);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target);
            }
            committed = true;
            return prismId(instance);
        } finally {
            if (!committed) deleteTree(staging);
        }
    }

    private static String detectPrismExportPrefix(Set<String> lowerPaths, List<? extends ZipEntry> entries) {
        if (lowerPaths.contains("instance.cfg") && lowerPaths.contains("mmc-pack.json"))
            return "";
        Set<String> roots = new HashSet<>();
        for (String file : lowerPaths) {
            if (file.endsWith("/instance.cfg")) {
                String prefix = file.substring(0, file.length() - "instance.cfg".length());
                if (lowerPaths.contains(prefix + "mmc-pack.json")) roots.add(prefix);
            }
        }
        if (roots.size() != 1) return null;
        // Le préfixe doit conserver la casse réelle du ZIP.
        String lowerPrefix = roots.iterator().next();
        for (ZipEntry entry : entries) {
            String name = entry.getName();
            if (name.toLowerCase(Locale.ROOT).equals(lowerPrefix + "instance.cfg")) {
                return name.substring(0, name.length() - "instance.cfg".length());
            }
        }
        return null;
    }

    private static void validateEntryName(String name) throws IOException {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.indexOf('\0') >= 0 || name.contains(":"))
            throw new IOException("Chemin interdit dans le ZIP : " + name);
        for (String part : name.split("/")) {
            if (part.equals(".") || part.equals(".."))
                throw new IOException("Traversée de répertoire interdite : " + name);
        }
    }

    private static boolean containsPersonalData(String name) {
        String path = name.toLowerCase(Locale.ROOT);
        if (path.equals("accounts.json") || path.equals("prismlauncher.cfg")
                || path.endsWith("/usercache.json")
                || path.endsWith("/username-cache.json")
                || path.endsWith("/player-volumes.properties")) return true;
        // Certains exports Prism utilisent minecraft/, d'autres .minecraft/.
        if (path.startsWith(".minecraft/")) path = "minecraft/" + path.substring(".minecraft/".length());
        if (path.startsWith("minecraft/saves/")
                || path.startsWith("minecraft/screenshots/")
                || path.startsWith("minecraft/logs/")
                || path.startsWith("minecraft/crash-reports/")) return true;
        // Cette configuration peut contenir un mot de passe Web/API.
        return path.equals("minecraft/config/resourceful-config-web.json");
    }

    /** Le répertoire réellement utilisé dépend du format d'export Prism. */
    private static Path gameDirectory(Path instanceFolder) throws IOException {
        Path standard = instanceFolder.resolve("minecraft");
        Path legacy = instanceFolder.resolve(".minecraft");
        boolean hasStandard = Files.isDirectory(standard, LinkOption.NOFOLLOW_LINKS);
        boolean hasLegacy = Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS);
        if (hasStandard && hasLegacy) {
            throw new IOException("Export Prism ambigu : minecraft/ et .minecraft/ sont tous deux présents.");
        }
        return hasStandard ? standard : legacy;
    }

    private static long countMods(Path instanceFolder) throws IOException {
        Path mods = gameDirectory(instanceFolder).resolve("mods");
        if (!Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) return 0;
        try (var files = Files.list(mods)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .count();
        }
    }

    /** L'UUID de l'exporteur ne doit pas être réutilisé chez tous les joueurs. */
    private static void assignUniqueInstanceUuid(Path staging) throws IOException {
        Path cfg = staging.resolve("instance.cfg");
        String content = Files.readString(cfg);
        String line = "uuid=" + java.util.UUID.randomUUID();
        if (java.util.regex.Pattern.compile("(?m)^uuid=[^\r\n]*").matcher(content).find()) {
            content = content.replaceAll("(?m)^uuid=[^\r\n]*", line);
        } else {
            content += (content.endsWith("\n") ? "" : "\n") + line + "\n";
        }
        Files.writeString(cfg, content);
    }

    private static void validateInstalledFiles(Path staging, OfficialInstance instance) throws IOException {
        if (!Files.isRegularFile(staging.resolve("instance.cfg"))
                || !Files.isRegularFile(staging.resolve("mmc-pack.json")))
            throw new IOException("Export Prism incomplet : instance.cfg ou mmc-pack.json absent.");
        try {
            if (Files.size(staging.resolve("mmc-pack.json")) > 256 * 1024)
                throw new IOException("mmc-pack.json est anormalement volumineux.");
            JsonObject json = JsonParser.parseString(
                    Files.readString(staging.resolve("mmc-pack.json"))).getAsJsonObject();
            JsonArray components = json.getAsJsonArray("components");
            if (components == null) throw new IOException("mmc-pack.json sans composants.");
            String minecraft = null;
            String loader = null;
            String loaderVersion = null;
            for (JsonElement item : components) {
                JsonObject component = item.getAsJsonObject();
                String uid = component.get("uid").getAsString();
                String version = component.get("version").getAsString();
                if (uid.equals("net.minecraft")) minecraft = version;
                if (uid.equals("net.minecraftforge")) { loader = "FORGE"; loaderVersion = version; }
                if (uid.equals("net.neoforged")) { loader = "NEOFORGE"; loaderVersion = version; }
                if (uid.equals("net.fabricmc.fabric-loader")) { loader = "FABRIC"; loaderVersion = version; }
            }
            if (!instance.getMinecraftVersion().equals(minecraft))
                throw new IOException("Version Minecraft différente de celle du catalogue : " + minecraft);
            if (instance.getModLoader() == ModLoader.VANILLA) {
                if (loader != null) throw new IOException("Un modloader inattendu est présent.");
            } else if (!instance.getModLoader().name().equals(loader)
                    || !instance.getLoaderVersion().equals(loaderVersion)) {
                throw new IOException("Modloader différent du catalogue (" + loader + " " + loaderVersion + ").");
            }
            if (instance.getModLoader() != ModLoader.VANILLA) {
                if (countMods(staging) == 0)
                    throw new IOException("Aucun mod .jar dans minecraft/mods (ou .minecraft/mods) : export Prism incomplet.");
                Path mods = gameDirectory(staging).resolve("mods");
                try (var modPaths = Files.list(mods)) {
                    for (Path mod : modPaths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                            .toList()) {
                        // Un simple fichier nommé .jar ne doit pas être considéré comme un mod installé.
                        try (var jar = new java.util.jar.JarFile(mod.toFile())) {
                            if (!jar.entries().hasMoreElements())
                                throw new IOException("Mod .jar vide : " + mod.getFileName());
                        } catch (java.util.zip.ZipException e) {
                            throw new IOException("Mod .jar invalide : " + mod.getFileName(), e);
                        }
                    }
                }
            }
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException e) {
            throw new IOException("mmc-pack.json mal formé ou incomplet.", e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Lancement via l'exécutable officiel ; les comptes restent administrés dans Prism. */
    public void launchInstance(OfficialInstance instance) throws IOException {
        String id = installedInstanceId(instance).orElseThrow(
                () -> new IOException("Instance absente ou version non conforme au catalogue."));
        Path exe = requireExecutable();
        new ProcessBuilder(exe.toString(), "--dir", dataDirectory.toString(), "--launch", id)
                .directory(exe.getParent().toFile()).start();
    }
}
