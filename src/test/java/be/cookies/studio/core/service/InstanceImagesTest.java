package be.cookies.studio.core.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class InstanceImagesTest {
 @TempDir Path temp;
 @Test void acceptsBothOfficialImageDirectoriesAndRejectsOtherSources(){
  assertTrue(NewsImageCacheService.allowed(URI.create("https://ethemkalayci.github.io/cookie-core-data/instances/images/instances1.jpg")));
  assertTrue(NewsImageCacheService.allowed(URI.create("https://ethemkalayci.github.io/cookie-core-data/news/images/news1.jpg")));
  assertFalse(NewsImageCacheService.allowed(URI.create("https://example.org/cookie-core-data/instances/images/x.jpg")));
  assertFalse(NewsImageCacheService.allowed(URI.create("https://ethemkalayci.github.io/cookie-core-data/instances/images/../../other.jpg")));
 }
 @Test @EnabledIfEnvironmentVariable(named="COOKIECORE_IMAGE_SMOKE",matches="true")
 void actualCatalogBannersDownloadDecodeAndRemainCached()throws Exception{
  var service=new NewsImageCacheService(temp);
  for(int id:new int[]{1,2}){
   String url="https://ethemkalayci.github.io/cookie-core-data/instances/images/instances"+id+".jpg";
   Path file=service.resolve(url).get(30,TimeUnit.SECONDS);var image=javax.imageio.ImageIO.read(file.toFile());assertNotNull(image);assertTrue(image.getWidth()>0);assertTrue(image.getHeight()>0);
   assertEquals(file,service.resolve(url).get(5,TimeUnit.SECONDS));
  }
 }
}
