package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.LastInstanceService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class HomeViewTest {
 @Test void displaysSnapshotAndEmptyState() throws Exception {
  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}
  assertTrue(ready.await(10,TimeUnit.SECONDS));var done=new CompletableFuture<Boolean>();
  Platform.runLater(()->{try{
   var loader=new FXMLLoader(getClass().getResource("/view/home-view.fxml"));loader.setControllerFactory(c->new HomeController(false));
   Parent root=loader.load();HomeController controller=loader.getController();
   be.cookies.studio.core.service.ThemeService.attach(new Scene(root,1200,900));root.applyCss();root.layout();
   assertEquals("Choisis ton aventure",((Label)root.lookup("#lastInstanceName")).getText());
   var instance=new OfficialInstance(1,"1.20.1","1.0.0",ModLoader.FORGE,"47.4.28","MontDino","","https://example.org/pack.zip","a".repeat(64));
   controller.showLastInstance(new LastInstanceService.Entry(instance,java.time.Instant.parse("2026-11-02T15:00:00Z")));
   assertEquals("MontDino",((Label)root.lookup("#lastInstanceName")).getText());
   assertTrue(((Label)root.lookup("#lastInstanceVersions")).getText().contains("FORGE 47.4.28"));
   assertTrue(((Label)root.lookup("#lastInstanceStatus")).getText().contains("absente"));
   root.applyCss();root.layout();
   var banner=(javafx.scene.layout.StackPane)root.lookup("#bannerPane");
   var artwork=(javafx.scene.image.ImageView)banner.getChildren().getFirst();
   assertTrue(artwork.viewportProperty().isBound(), "Fallback must also be cropped");
   assertNotNull(artwork.getViewport());
   assertEquals(banner.getWidth()/banner.getHeight(), artwork.getViewport().getWidth()/artwork.getViewport().getHeight(), 0.0001);
   var shot=root.snapshot(null,null);var output=new java.awt.image.BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
   for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
   javax.imageio.ImageIO.write(output,"png",new java.io.File("build/home-preview.png"));
   done.complete(true);
  }catch(Throwable e){done.completeExceptionally(e);}});
  assertTrue(done.get(20,TimeUnit.SECONDS));
 }
}
