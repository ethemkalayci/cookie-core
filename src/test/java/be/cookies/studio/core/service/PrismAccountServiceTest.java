package be.cookies.studio.core.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PrismAccountServiceTest {
 @TempDir Path dir;
 private void save(String accounts) throws Exception { Files.writeString(dir.resolve("accounts.json"), "{\"formatVersion\":3,\"accounts\":"+accounts+"}"); }
 private String account(String name, String type, boolean active, boolean playable) {
  return "{\"type\":\""+type+"\",\"active\":"+active+",\"profile\":{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\""+name+"\"},\"entitlement\":{\"canPlayMinecraft\":"+playable+"}}";
 }
 @Test void absent() throws Exception { assertTrue(new PrismAccountService(dir).readSelected().isEmpty()); }
 @Test void singleMicrosoft() throws Exception { save("["+account("Cookie","MSA",false,true)+"]"); assertEquals("Cookie",new PrismAccountService(dir).readSelected().orElseThrow().name()); }
 @Test void selectsActive() throws Exception { save("["+account("One","MSA",false,true)+","+account("Two","MSA",true,true)+"]"); assertEquals("Two",new PrismAccountService(dir).readSelected().orElseThrow().name()); }
 @Test void ambiguous() throws Exception { save("["+account("One","MSA",false,true)+","+account("Two","MSA",false,true)+"]"); assertTrue(new PrismAccountService(dir).readSelected().isEmpty()); }
 @Test void rejectsOfflineAndUnlicensed() throws Exception { save("["+account("One","Offline",true,true)+","+account("Two","MSA",false,false)+"]"); assertTrue(new PrismAccountService(dir).readSelected().isEmpty()); }
 @Test void doesNotFallbackFromActiveOffline() throws Exception { save("["+account("One","Offline",true,true)+","+account("Two","MSA",false,true)+"]"); assertTrue(new PrismAccountService(dir).readSelected().isEmpty()); }
 @Test void malformedDoesNotLeak() throws Exception { Files.writeString(dir.resolve("accounts.json"),"SECRET_TOKEN"); var e=assertThrows(java.io.IOException.class,()->new PrismAccountService(dir).readSelected()); assertFalse(e.toString().contains("SECRET_TOKEN")); assertNull(e.getCause()); }
 @Test void unknownVersion() throws Exception { Files.writeString(dir.resolve("accounts.json"),"{\"formatVersion\":4}"); assertThrows(java.io.IOException.class,()->new PrismAccountService(dir).readSelected()); }
}
