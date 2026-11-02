package be.cookies.studio.core.service;

import be.cookies.studio.core.model.ModLoader;
import be.cookies.studio.core.model.OfficialInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PrismLauncherServiceTest {
    @TempDir Path temp;

    private static OfficialInstance montDino() {
        return new OfficialInstance(1, "1.20.1", "1.0.0", ModLoader.FORGE,
                "47.4.28", "MontDino", "", "https://example.org/MontDino.zip", "a".repeat(64));
    }

    private Path archive(Map<String, String> files) throws IOException {
        Path path = Files.createTempFile(temp, "pack-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var item : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                if (item.getKey().endsWith(".jar") && item.getValue().equals("jar-placeholder")) {
                    java.io.ByteArrayOutputStream jarBytes = new java.io.ByteArrayOutputStream();
                    try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(jarBytes)) {
                        jar.putNextEntry(new java.util.jar.JarEntry("example.txt"));
                        jar.write("test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        jar.closeEntry();
                    }
                    zip.write(jarBytes.toByteArray());
                } else {
                    zip.write(item.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return path;
    }

    private static Map<String, String> fullPack() {
        return Map.of(
                "Export/instance.cfg", "InstanceType=OneSix\nname=MontDino\n",
                "Export/mmc-pack.json", """
                        {"formatVersion":1,"components":[
                          {"uid":"net.minecraft","version":"1.20.1"},
                          {"uid":"net.minecraftforge","version":"47.4.28"}
                        ]}
                        """,
                "Export/.minecraft/mods/example.jar", "jar-placeholder",
                "Export/.minecraft/config/example.toml", "setting=true"
        );
    }

    @Test
    void importsNativePrismExportAndPersistsMarker() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        Path zip = archive(fullPack());
        String id = service.installModpack(montDino(), zip);
        assertEquals("cookiecore-official-1", id);
        assertEquals(id, service.installedInstanceId(montDino()).orElseThrow());
        Path instance = service.getDataDirectory().resolve("instances").resolve(id);
        assertTrue(Files.isRegularFile(instance.resolve(".minecraft/mods/example.jar")));
        assertTrue(Files.isRegularFile(instance.resolve(".cookiecore-install.properties")));
        assertTrue(Files.exists(zip), "Le ZIP original ne doit pas être supprimé par l'installateur");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), zip),
                "Une nouvelle installation ne doit jamais écraser la précédente");
    }

    @Test
    void rejectsCurseForgeManifestWithoutCreatingInstance() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        Path zip = archive(Map.of("manifest.json", "{}", "overrides/config/example.toml", "x"));
        IOException error = assertThrows(IOException.class, () -> service.installModpack(montDino(), zip));
        assertTrue(error.getMessage().contains("CurseForge"));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsWrongLoaderVersionWithoutCreatingInstance() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/mmc-pack.json", map.get("Export/mmc-pack.json").replace("47.4.28", "47.0.0"));
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsZipSlipWithoutWritingOutsideStaging() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/../../../escape.txt", "malicious");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsPackMissingMods() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.remove("Export/.minecraft/mods/example.jar");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsCorruptedJarWithoutPublishingInstance() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/.minecraft/mods/example.jar", "not-a-valid-jar");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsPlayerSaves() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/.minecraft/saves/world/level.dat", "private save");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }
    @Test
    void importsRealPrismLayoutAndReplacesExportUuid() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.remove("Export/.minecraft/mods/example.jar");
        map.remove("Export/.minecraft/config/example.toml");
        map.put("Export/minecraft/mods/example.jar", "jar-placeholder");
        map.put("Export/minecraft/config/example.toml", "setting=true");
        map.put("Export/instance.cfg", "[General]\nname=MontDino\nuuid=original-export-uuid\n");
        String id = service.installModpack(montDino(), archive(map));
        Path folder = service.getDataDirectory().resolve("instances").resolve(id);
        assertTrue(Files.isRegularFile(folder.resolve("minecraft/mods/example.jar")));
        assertTrue(service.installedInstanceId(montDino()).isPresent());
        String cfg = Files.readString(folder.resolve("instance.cfg"));
        assertFalse(cfg.contains("original-export-uuid"));
        assertTrue(cfg.contains("uuid="));
        assertTrue(Files.readString(folder.resolve(".cookiecore-install.properties"))
                .contains("modJarCount=1"));
    }

    @Test
    void rejectsAmbiguousGameFoldersWithoutCreatingInstance() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/minecraft/mods/another.jar", "jar-placeholder");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void rejectsSensitiveWebConfigWithoutCreatingInstance() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        var map = new java.util.HashMap<>(fullPack());
        map.put("Export/minecraft/config/resourceful-config-web.json", "{\"password\":\"secret\"}");
        assertThrows(IOException.class, () -> service.installModpack(montDino(), archive(map)));
        assertFalse(service.hasManagedInstance(montDino()));
    }

    @Test
    void allowsIntentionalModRemovalWithManagedManifest() throws Exception {
        PrismLauncherService service = new PrismLauncherService(temp.resolve("cookie-core"));
        String id = service.installModpack(montDino(), archive(fullPack()));
        assertTrue(service.installedInstanceId(montDino()).isPresent());
        Files.delete(service.getDataDirectory().resolve("instances").resolve(id)
                .resolve(".minecraft/mods/example.jar"));
        assertTrue(service.installedInstanceId(montDino()).isPresent(), "Le retrait personnel est conservé et ne doit pas bloquer tout lancement");
    }


    @Test void installsNewPackOutsidePrismAndRetainsOldInstance() throws Exception {
        Path root = temp.resolve("cookie-core");
        var service = new PrismLauncherService(root);
        service.installModpack(montDino(), archive(fullPack()));
        Path old=service.getDataDirectory().resolve("instances/cookiecore-official-1");
        Files.writeString(old.resolve("player-save.txt"), "precious");
        Path external=temp.resolve("external packs");
        new SettingsService(root).update(c->c.minecraft(2048,external.toString()));
        var second=new OfficialInstance(2,"1.20.1","1.0.0",ModLoader.FORGE,"47.4.28","Second","","https://example.org/pack.zip","a".repeat(64));
        String id=service.installModpack(second,archive(fullPack()));
        Path actual=external.resolve(id),alias=service.getDataDirectory().resolve("instances").resolve(id);
        assertTrue(Files.isSameFile(actual,alias));assertTrue(service.installedInstanceId(second).isPresent());
        assertEquals("2048",PrismConfig.read(actual.resolve("instance.cfg")).get("MaxMemAlloc"));
        new SettingsService(root).update(c->c.minecraft(3072,""));
        assertTrue(service.installedInstanceId(second).isPresent());
        assertTrue(service.installedInstanceId(montDino()).isPresent());assertEquals("precious",Files.readString(old.resolve("player-save.txt")));
    }
}
