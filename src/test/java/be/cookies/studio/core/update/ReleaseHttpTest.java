package be.cookies.studio.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseHttpTest {
    @TempDir Path temp;
    final URI uri=URI.create(ReleaseManifest.REPOSITORY+"latest/download/release.json");
    ReleaseHttp response(int code,Map<String,String> headers,byte[] bytes){return new ReleaseHttp(u->new ReleaseHttp.Response(code,headers,new ByteArrayInputStream(bytes)));}
    @Test void successAndProgress() throws Exception {
        var progress=new java.util.concurrent.atomic.AtomicLong();Path file=temp.resolve("download");response(200,Map.of("Content-Length","3"),new byte[]{1,2,3}).download(uri,file,3,progress::set);
        assertEquals(3,progress.get());assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(file));
    }
    @Test void failuresDeletePartialDownloads() {
        for(var client:List.of(response(404,Map.of(),new byte[0]),response(200,Map.of("Content-Length","5"),new byte[2]),response(200,Map.of(),new byte[8]),new ReleaseHttp(u->{throw new java.net.http.HttpTimeoutException("timeout");}),new ReleaseHttp(u->{throw new java.net.UnknownHostException();}))) {
            Path target=temp.resolve(UUID.randomUUID().toString());assertThrows(Exception.class,()->client.download(uri,target,4,n->{}));assertFalse(Files.exists(target));
        }
    }
    @Test void preexistingFileIsNeverDeleted() throws Exception {
        Path file=temp.resolve("keep");Files.writeString(file,"keep");assertThrows(IOException.class,()->response(200,Map.of(),new byte[0]).download(uri,file,4,n->{}));assertEquals("keep",Files.readString(file));
    }
    @Test void redirectRevalidatedBeforeNetworkAndLoopsBounded() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();var client=new ReleaseHttp(u->{calls.incrementAndGet();return new ReleaseHttp.Response(302,Map.of("Location","http://evil.invalid"),InputStream.nullInputStream());});
        assertThrows(SecurityException.class,()->client.bytes(uri,10));assertEquals(1,calls.get());
        assertThrows(IOException.class,()->response(302,Map.of("Location",uri.toString()),new byte[0]).bytes(uri,10));
    }
}
