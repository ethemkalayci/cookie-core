package be.cookies.studio.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class ReleasePackagingTest {
    @TempDir Path temp;
    Path jdk=Path.of(System.getProperty("java.home"));
    void run(ProcessBuilder builder, String name, boolean success) throws Exception {
        Path log=temp.resolve(name+".log");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process=builder.start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();fail(name+" timed out");}
        // No subprocess output in assertions: signing receives test-only private material.
        if(success)assertEquals(0,process.exitValue(),name+" failed");else assertNotEquals(0,process.exitValue(),name+" unexpectedly accepted invalid artifact");
    }
    @Test void realWindowsPackagerAndSignerProduceAnUpdaterCompatibleRelease() throws Exception {
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String keyId="ephemeral-packaging-test",publicKey=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String trust=keyId+"="+publicKey+"\n";
        Path image=temp.resolve("image with spaces");Files.createDirectories(image.resolve("app"));Files.createDirectories(image.resolve("runtime/bin"));
        Files.writeString(image.resolve("CookieCore.exe"),"fixture");Files.writeString(image.resolve("runtime/bin/java.exe"),"fixture");Files.writeString(image.resolve("app/version.txt"),"1.0.2");Files.writeString(image.resolve("app/.jpackage.xml"),"fixture");
        try(var jar=new JarOutputStream(Files.newOutputStream(image.resolve("app/cookie-core.jar")))) {
            for(var entry:Map.of("version.txt","1.0.2","update-keys.properties",trust).entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));jar.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));jar.closeEntry();
            }
        }
        Path archive=temp.resolve("release.zip");
        run(new ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",Path.of("tools/update/Create-ReleaseArchive.ps1").toAbsolutePath().toString(),"-Source",image.toString(),"-Destination",archive.toString(),"-Jdk",jdk.toString()),"pack",true);
        try(var zip=new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("app/cookie-core.jar"));assertNotNull(zip.getEntry("app/.jpackage.xml"));assertTrue(zip.stream().noneMatch(e->e.getName().contains("\\")));
        }
        var signer=new ProcessBuilder(jdk.resolve("bin/java.exe").toString(),"tools/update/SignRelease.java","1.0.2",archive.toString(),temp.resolve("signed").toString(),keyId);
        signer.environment().put("COOKIE_CORE_SIGNING_KEY",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        signer.environment().put("COOKIE_CORE_UPDATE_KEYS",trust);
        run(signer,"sign",true);
        var keys=new Properties();keys.setProperty(keyId,publicKey);
        var manifest=ReleaseManifest.verify(Files.readAllBytes(temp.resolve("signed/release.json")),Files.readAllBytes(temp.resolve("signed/release.sig")),keys);
        UpdateFiles.hash(archive,manifest);UpdateFiles.extract(archive,temp.resolve("unpacked"));
        assertEquals("1.0.2",manifest.version().value());assertEquals("1.0.2",Files.readString(temp.resolve("unpacked/app/version.txt")));
        // Reproduce legacy .NET ZIP names; signing must reject them explicitly.
        Path invalid=temp.resolve("legacy.zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(invalid))){zip.putNextEntry(new ZipEntry("app\\cookie-core.jar"));Files.copy(image.resolve("app/cookie-core.jar"),zip);zip.closeEntry();}
        signer.command().set(3,invalid.toString());run(signer,"reject-legacy",false);
        assertTrue(Files.readString(temp.resolve("reject-legacy.log")).contains("Non-portable ZIP paths"));
    }
}
