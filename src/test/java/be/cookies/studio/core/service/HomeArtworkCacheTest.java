package be.cookies.studio.core.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class HomeArtworkCacheTest {
 @TempDir Path root;
 @Test void resolvesLegacyOfficialArtworkWithoutNetworkAndRejectsCorruption() throws Exception {
  String body="""
   [{"id":1,"name":"MontDino","minecraftVersion":"1.20.1","modLoader":"FORGE","loaderVersion":"47.4.28","modpackVersion":"1.0.1","imagePath":"https://ethemkalayci.github.io/cookie-core-data/instances/images/instances1.jpg"}]
   """;
  new OfficialInstanceService(root,()->body).loadCatalog();
  var offline=new OfficialInstanceService(root,()->{throw new AssertionError("Cache lookup must not access network");});
  assertTrue(offline.cachedCatalog().orElseThrow().instances().getFirst().getImagePath().endsWith("instances1.jpg"));
  Files.writeString(root.resolve("official-catalog-cache.json"),"broken");
  assertTrue(offline.cachedCatalog().isEmpty());
 }
}
