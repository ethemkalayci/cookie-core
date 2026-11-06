package be.cookies.studio.core.update;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdateSecurityTest {
    @TempDir Path temp;
    @Test void numericStableVersions() {
        assertTrue(new ReleaseVersion("1.10.0").compareTo(new ReleaseVersion("1.9.9"))>0);
        assertEquals(0,new ReleaseVersion("1.0.0").compareTo(new ReleaseVersion("1.0.0")));
        for(String bad:List.of("1.0-SNAPSHOT","1.0.1-beta","01.0.0","v1.0.0","1.0","-1.0.0"))assertThrows(IllegalArgumentException.class,()->new ReleaseVersion(bad));
    }
    @Test void signatureAndCompatibilityFailClosed() throws Exception {
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var keys=new Properties();keys.setProperty("test",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        byte[] manifest=manifest("1.0.1","windows-x64");var sig=Signature.getInstance("Ed25519");sig.initSign(pair.getPrivate());sig.update(manifest);byte[] signature=Base64.getEncoder().encode(sig.sign());
        assertEquals("1.0.1",ReleaseManifest.verify(manifest,signature,keys).version().value());
        assertThrows(SecurityException.class,()->ReleaseManifest.verify(manifest,signature,new Properties()));
        assertThrows(SecurityException.class,()->ReleaseManifest.verify(manifest("1.0.2","windows-x64"),signature,keys));
        byte[] incompatible=manifest("1.0.1","linux-x64");sig.initSign(pair.getPrivate());sig.update(incompatible);byte[] other=Base64.getEncoder().encode(sig.sign());
        assertThrows(SecurityException.class,()->ReleaseManifest.verify(incompatible,other,keys));
    }
    static byte[] manifest(String version,String platform) {
        return ("{\"schemaVersion\":1,\"bootstrapProtocol\":1,\"keyId\":\"test\",\"channel\":\"stable\",\"platform\":\""+platform+"\",\"version\":\""+version+"\",\"artifact\":\""+ReleaseManifest.REPOSITORY+"download/v"+version+"/CookieCore-"+version+"-windows-x64.zip\",\"size\":1,\"sha256\":\""+"0".repeat(64)+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void dangerousRedirectsRejected() {
        for(String url:List.of("http://github.com/ethemkalayci/cookie-core/releases/latest","https://evil.com/a","https://github.com/other/releases/a","https://user@github.com/ethemkalayci/cookie-core/releases/a","https://github.com:444/ethemkalayci/cookie-core/releases/a"))
            assertThrows(SecurityException.class,()->ReleaseHttp.validate(URI.create(url)));
        ReleaseHttp.validate(URI.create(ReleaseManifest.REPOSITORY+"latest/download/release.json"));
    }
    @Test void archiveTraversalAndWindowsAliasesRejected() throws Exception {
        for(String name:List.of("../escape","/escape","C:/escape","a\\b","CON.txt","x./file","x /file","a/../../b")) {
            Path zip=temp.resolve(UUID.randomUUID()+".zip");
            try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry(name));out.write(1);out.closeEntry();}
            assertThrows(java.io.IOException.class,()->UpdateFiles.extract(zip,temp.resolve(UUID.randomUUID().toString())),name);
        }
        assertFalse(Files.exists(temp.getParent().resolve("escape")));
    }
    @Test void hashAndTruncation() throws Exception {
        Path zip=temp.resolve("archive");Files.write(zip,new byte[]{1});
        var m=new ReleaseManifest(new ReleaseVersion("1.0.1"),URI.create("https://example.invalid"),2,"0".repeat(64));
        assertThrows(SecurityException.class,()->UpdateFiles.hash(zip,m));
        var sameSize=new ReleaseManifest(m.version(),m.artifact(),1,m.sha256());assertThrows(SecurityException.class,()->UpdateFiles.hash(zip,sameSize));
    }
    @Test void crashRecoveryIsIdempotentAndRejectsLoop() throws Exception {
        UpdateFiles.atomic(temp.resolve("current"),"1.0.1");UpdateFiles.atomic(temp.resolve("previous"),"1.0.0");UpdateFiles.atomic(temp.resolve("pending"),"1.0.1");
        Bootstrap.recover(temp);Bootstrap.recover(temp);
        assertEquals("1.0.0",Files.readString(temp.resolve("current")));assertTrue(Files.exists(temp.resolve("rejected-1.0.1")));assertFalse(Files.exists(temp.resolve("pending")));
    }
    @Test void interruptedBeforePointerSwitchStillRestoresOldVersion() throws Exception {
        UpdateFiles.atomic(temp.resolve("current"),"1.0.0");UpdateFiles.atomic(temp.resolve("previous"),"1.0.0");UpdateFiles.atomic(temp.resolve("pending"),"1.0.1");
        Bootstrap.recover(temp);assertEquals("1.0.0",Files.readString(temp.resolve("current")));
    }
    @Test void decompressionLimitAndCaseCollisions() throws Exception {
        Path zip=temp.resolve("bomb.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("large"));out.write(new byte[1024*1024]);out.closeEntry();}
        assertThrows(java.io.IOException.class,()->UpdateFiles.extract(zip,temp.resolve("bomb"),1024));
        Path collision=temp.resolve("collision.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(collision))){for(String name:List.of("A.txt","a.txt")){out.putNextEntry(new ZipEntry(name));out.write(1);out.closeEntry();}}
        assertThrows(java.io.IOException.class,()->UpdateFiles.extract(collision,temp.resolve("collision")));
    }
    @Test void centralDirectorySymlinkRejected() throws Exception {
        Path zip=temp.resolve("link.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry("link"));out.write(1);out.closeEntry();}
        byte[] bytes=Files.readAllBytes(zip);var b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<bytes.length-46;i++)if(b.getInt(i)==0x02014b50){b.putInt(i+38,0xa000<<16);break;}Files.write(zip,bytes);
        assertThrows(java.io.IOException.class,()->UpdateFiles.extract(zip,temp.resolve("link")));
    }
    @Test void criticalWorkersDeferRestart() {
        for(String name:List.of("cookiecore-prism-operation","cookiecore-prism-account","cookiecore-instance-operation","settings-save","personal-instance-create","home-launch"))assertTrue(ShutdownGate.critical(name));
        assertFalse(ShutdownGate.critical("news-image-loader"));
    }}

