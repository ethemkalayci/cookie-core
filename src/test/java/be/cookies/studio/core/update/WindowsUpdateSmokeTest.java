package be.cookies.studio.core.update;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Windows app-images and JavaFX shell, isolated LOCALAPPDATA, ephemeral test trust only. */
@EnabledIfEnvironmentVariable(named="COOKIE_CORE_WINDOWS_SMOKE",matches="true")
class WindowsUpdateSmokeTest {
    @TempDir Path temp;
    static void copy(Path source,Path destination) throws Exception {
        try(var paths=Files.walk(source)){for(Path p:paths.toList()){Path out=destination.resolve(source.relativize(p));if(Files.isDirectory(p))Files.createDirectories(out);else Files.copy(p,out);}}
    }
    static void patchTrust(Path jar,byte[] trust) throws Exception {
        Path output=jar.resolveSibling("test.jar");
        try(var in=new ZipInputStream(Files.newInputStream(jar));var out=new ZipOutputStream(Files.newOutputStream(output))) {
            for(ZipEntry e;(e=in.getNextEntry())!=null;) {out.putNextEntry(new ZipEntry(e.getName()));if(e.getName().equals("update-keys.properties"))out.write(trust);else in.transferTo(out);out.closeEntry();}
        }
        Files.move(output,jar,StandardCopyOption.REPLACE_EXISTING);
    }
    static void waitFor(BooleanSupplier condition,int seconds,String description) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(seconds).toNanos();while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(250);assertTrue(condition.getAsBoolean(),description);
    }
    static boolean equals(Path path,String expected){try{return Files.readString(path).trim().equals(expected);}catch(Exception e){return false;}}
    @Test void packagedOneToTwoAndBrokenCandidateRollback() throws Exception {
        Path build=Path.of("build").toAbsolutePath(),root=temp.resolve("installation"),sandbox=temp.resolve("userdata");Files.createDirectories(root);Files.createDirectories(sandbox);
        Path one=root.resolve("versions/1.0.0");copy(build.resolve("windows-1.0.0/image/CookieCore"),one);
        Path two=temp.resolve("candidate");copy(build.resolve("windows-1.0.1/image/CookieCore"),two);
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var keys=new Properties();String publicKey=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());keys.setProperty("ephemeral-test",publicKey);
        byte[] trust=("ephemeral-test="+publicKey+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        patchTrust(one.resolve("app/cookie-core.jar"),trust);patchTrust(two.resolve("app/cookie-core.jar"),trust);
        copy(one.resolve("runtime"),root.resolve("bootstrap/runtime"));copy(one.resolve("app"),root.resolve("bootstrap/app"));
        Path zip=temp.resolve("candidate.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip));var paths=Files.walk(two)){
            for(Path p:paths.filter(Files::isRegularFile).toList()) {out.putNextEntry(new ZipEntry(two.relativize(p).toString().replace('\\','/')));Files.copy(p,out);out.closeEntry();}
        }
        var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(zip)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}
        String json=new String(UpdateSecurityTest.manifest("1.0.1","windows-x64"),java.nio.charset.StandardCharsets.UTF_8).replace("\"test\"","\"ephemeral-test\"").replace("\"size\":1,","\"size\":"+Files.size(zip)+",").replace("0".repeat(64),HexFormat.of().formatHex(digest.digest()));
        byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);var signer=Signature.getInstance("Ed25519");signer.initSign(pair.getPrivate());signer.update(bytes);byte[] signature=Base64.getEncoder().encode(signer.sign());
        UpdateFiles.atomic(root.resolve("current"),"1.0.0");
                var transport=new ReleaseHttp(uri->{
            if(uri.getPath().endsWith("release.json"))return new ReleaseHttp.Response(200,Map.of(),new java.io.ByteArrayInputStream(bytes));
            if(uri.getPath().endsWith("release.sig"))return new ReleaseHttp.Response(200,Map.of(),new java.io.ByteArrayInputStream(signature));
            return new ReleaseHttp.Response(200,Map.of("Content-Length",Long.toString(Files.size(zip))),Files.newInputStream(zip));
        });
        var states=new ArrayList<String>();var ready=new java.util.concurrent.atomic.AtomicBoolean();
        new UpdateService(root,transport,keys).check(new ReleaseVersion("1.0.0"),states::add,()->ready.set(true));
        assertTrue(ready.get());assertTrue(states.stream().anyMatch(s->s.contains("100 %")));assertTrue(states.stream().anyMatch(s->s.contains("Vérification")));
        Path data=sandbox.resolve("CookiesStudio/CookieCore");Files.createDirectories(data.resolve("prism-data"));Files.writeString(data.resolve("sentinel.txt"),"keep");        var preserved=List.of("settings.json","settings.previous.json","prism-data/accounts.json","inventory/sentinel","operations/sentinel","prism-runtime-11.1.1/sentinel","prism-data/instances/test/.minecraft/saves/world/sentinel");
        for(String name:preserved){Path p=data.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,"unchanged");}
        var builder=new ProcessBuilder(root.resolve("bootstrap/runtime/bin/java.exe").toString(),"-cp",root.resolve("bootstrap/app").toString()+"/*",Bootstrap.class.getName(),root.toString());
        builder.environment().put("LOCALAPPDATA",sandbox.toString());builder.redirectErrorStream(true).redirectOutput(temp.resolve("bootstrap.log").toFile());
        Process process=builder.start();        
        try {
            waitFor(()->equals(root.resolve("confirmed"),"1.0.0"),120,"Initial real JavaFX shell must be healthy; see "+temp);
            Process competing=builder.start();assertTrue(competing.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,competing.exitValue(),"Second bootstrap must not launch another app");
            waitFor(()->equals(root.resolve("confirmed"),"1.0.1"),150,"Automatic close, switch and healthy new JavaFX shell; see "+temp);
            assertFalse(Files.exists(root.resolve("pending")));assertEquals("keep",Files.readString(data.resolve("sentinel.txt"))); for(String name:preserved)assertEquals("unchanged",Files.readString(data.resolve(name)),name);
            Path evidence=build.resolve("windows-smoke-evidence.txt");Files.writeString(evidence,"Real packaged JavaFX 1.0.0 -> 1.0.1: healthy, automatic restart, isolated LOCALAPPDATA sentinel unchanged.\n");
        } finally {process.descendants().forEach(ProcessHandle::destroy);process.destroy();process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}
        // Recovery from an interrupted activation transaction.
        Files.deleteIfExists(root.resolve("ready"));Files.deleteIfExists(root.resolve("confirmed"));
        UpdateFiles.atomic(root.resolve("previous"),"1.0.0");UpdateFiles.atomic(root.resolve("pending"),"1.0.1");UpdateFiles.atomic(root.resolve("current"),"1.0.1");
        Process recovery=builder.start();
        try {
            waitFor(()->equals(root.resolve("confirmed"),"1.0.0"),120,"Recovery must run the previous real JavaFX shell");
            assertTrue(Files.exists(root.resolve("rejected-1.0.1")));assertEquals("keep",Files.readString(data.resolve("sentinel.txt"))); for(String name:preserved)assertEquals("unchanged",Files.readString(data.resolve(name)),name);
            Files.writeString(build.resolve("windows-smoke-evidence.txt"),"Recovery after pending transaction: real 1.0.0 shell healthy, rejected version blocked.\n",StandardOpenOption.APPEND);
        } finally {recovery.descendants().forEach(ProcessHandle::destroy);recovery.destroy();recovery.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}
        // Candidate executable failure after controlled exit must rollback without a restart loop.
        Files.delete(root.resolve("rejected-1.0.1"));Files.deleteIfExists(root.resolve("confirmed"));
        Files.move(root.resolve("versions/1.0.1/CookieCore.exe"),root.resolve("versions/1.0.1/CookieCore.exe.disabled"));
        UpdateFiles.atomic(root.resolve("ready"),"1.0.1");
        Process broken=builder.start();
        try {
            waitFor(()->Files.exists(root.resolve("rejected-1.0.1")),150,"Broken executable must trigger rollback");
            waitFor(()->equals(root.resolve("confirmed"),"1.0.0"),120,"Rollback must display the old shell");
            assertEquals("1.0.0",Files.readString(root.resolve("current")));
            Files.writeString(build.resolve("windows-smoke-evidence.txt"),"Broken candidate executable: automatic rollback to healthy 1.0.0; concurrent bootstrap excluded.\n",StandardOpenOption.APPEND);
        } finally {broken.descendants().forEach(ProcessHandle::destroy);broken.destroy();broken.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}    }
}




