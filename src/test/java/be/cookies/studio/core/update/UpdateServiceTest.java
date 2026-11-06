package be.cookies.studio.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.channels.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {
    @TempDir Path root;
    UpdateService service(String version,java.util.concurrent.atomic.AtomicInteger requests) throws Exception {
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var keys=new Properties();keys.setProperty("test",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        byte[] manifest=UpdateSecurityTest.manifest(version,"windows-x64");var signer=Signature.getInstance("Ed25519");signer.initSign(pair.getPrivate());signer.update(manifest);byte[] sig=Base64.getEncoder().encode(signer.sign());
        var http=new ReleaseHttp(uri->{requests.incrementAndGet();byte[] body=uri.getPath().endsWith("release.json")?manifest:sig;return new ReleaseHttp.Response(200,Map.of(),new java.io.ByteArrayInputStream(body));});
        return new UpdateService(root,http,keys);
    }
    @Test void equalAndOlderReleasesNeverDownloadOrRestart() throws Exception {
        for(String remote:List.of("1.0.0","0.9.9")) {
            var requests=new java.util.concurrent.atomic.AtomicInteger();var states=new ArrayList<String>();
            service(remote,requests).check(new ReleaseVersion("1.0.0"),states::add,()->fail("Unexpected restart"));
            assertEquals(2,requests.get());assertTrue(states.getLast().contains("à jour"));assertFalse(Files.exists(root.resolve("ready")));
        }
    }
    @Test void concurrentUpdateCannotWriteOrUseNetwork() throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();var service=service("1.0.1",requests);
        try(var channel=FileChannel.open(root.resolve("update.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertThrows(OverlappingFileLockException.class,()->service.check(new ReleaseVersion("1.0.0"),s->{},()->fail("restart")));assertEquals(0,requests.get());
        }
    }
    @Test void unavailableNetworkPreservesLocalState() throws Exception {
        Files.writeString(root.resolve("current"),"1.0.0");
        var service=new UpdateService(root,new ReleaseHttp(uri->{throw new java.net.UnknownHostException("offline");}),new Properties());
        assertThrows(java.net.UnknownHostException.class,()->service.check(new ReleaseVersion("1.0.0"),s->{},()->fail("restart")));
        assertEquals("1.0.0",Files.readString(root.resolve("current")));assertFalse(Files.exists(root.resolve("ready")));
    }
}
