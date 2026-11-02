package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class InstancePageCorrectionsTest {
 @TempDir Path temp;
 @Test void personalSectionExcludesOfficialAndContentTabsStayInsidePage()throws Exception{
  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}assertTrue(ready.await(10,TimeUnit.SECONDS));
  var done=new CompletableFuture<Void>();Platform.runLater(()->{try{
   var loader=new FXMLLoader(getClass().getResource("/view/instance-view.fxml"));loader.setControllerFactory(t->new InstanceController(false));ScrollPane root=loader.load();ThemeService.attach(new Scene(root,1050,760));root.applyCss();root.layout();
   InstanceController controller=loader.getController();var official=new OfficialInstance(1,"1.20.1","alpha",ModLoader.VANILLA,"","Official fixture","","","");
   var personal=new LocalInstance("cookiecore-personal-"+UUID.randomUUID(),UUID.randomUUID().toString(),"Personal fixture",temp.resolve("personal").toString(),"1.20.1",ModLoader.VANILLA,"","",null);
   var managed=new LocalInstance("cookiecore-official-1",UUID.randomUUID().toString(),"Official fixture",temp.resolve("official").toString(),"1.20.1",ModLoader.VANILLA,"","",official);
   controller.renderLocalInstances(List.of(managed,personal));var personals=(TilePane)loader.getNamespace().get("personalInstancesGrid");assertEquals(1,personals.getChildren().size());assertEquals("Personal fixture",((Label)((VBox)personals.getChildren().getFirst()).getChildren().getFirst()).getText());
   int windows=Window.getWindows().size();var host=(VBox)loader.getNamespace().get("inlineContentsHost");
   for(String tab:List.of("Mods","Resource Packs","Shaders")){
    root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(tab)).map(n->(Button)n).findFirst().orElseThrow().fire();
    assertEquals(windows,Window.getWindows().size());assertEquals(switch(tab){case "Mods"->be.cookies.studio.core.service.InstanceContentService.Kind.MODS;case "Shaders"->be.cookies.studio.core.service.InstanceContentService.Kind.SHADERS;default->be.cookies.studio.core.service.InstanceContentService.Kind.RESOURCE_PACKS;},((be.cookies.studio.core.ui.InstanceContentsPane)host.getChildren().getFirst()).selectedKind());
   }
   done.complete(null);
  }catch(Throwable error){done.completeExceptionally(error);}});done.get(20,TimeUnit.SECONDS);
 }
}
