package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class LastInstanceServiceTest {
 @TempDir Path dir;
 OfficialInstance instance(int id,String name){return new OfficialInstance(id,"1.20.1","1.0.0",ModLoader.FORGE,"47.4.28",name,"https://example.org/banner.png","https://example.org/pack.zip","a".repeat(64));}
 @Test void noLaunchHasNoHistory() throws Exception {assertTrue(new LastInstanceService(dir).load().isEmpty());}
 @Test void restoresSnapshotAfterRestart() throws Exception {
  var time=Instant.parse("2026-11-02T15:00:00Z");new LastInstanceService(dir).save(instance(1,"MontDino évasion"),time);
  var entry=new LastInstanceService(dir).load().orElseThrow();
  assertEquals("MontDino évasion",entry.instance().getName());assertEquals(time,entry.launchedAt());
  assertEquals("47.4.28",entry.instance().getLoaderVersion());assertEquals("https://example.org/banner.png",entry.instance().getImagePath());
  assertEquals("a".repeat(64),entry.instance().getSha256());
 }
 @Test void replacesLastInstance() throws Exception {var s=new LastInstanceService(dir);s.save(instance(1,"One"),Instant.now());s.save(instance(2,"Two"),Instant.now());assertEquals(2,s.load().orElseThrow().instance().getId());}
 @Test void invalidHistoryFailsClearly() throws Exception {Files.writeString(dir.resolve("last-instance.properties"),"format=1\nid=oops");assertThrows(java.io.IOException.class,()->new LastInstanceService(dir).load());}
}
