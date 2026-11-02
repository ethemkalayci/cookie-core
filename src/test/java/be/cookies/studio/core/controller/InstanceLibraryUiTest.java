package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class InstanceLibraryUiTest {
 @TempDir Path temp;
 @Test void libraryUsesAllThemesAndFitsDesktopSizes()throws Exception{
  String id="cookiecore-personal-"+UUID.randomUUID();Path folder=temp.resolve("prism-data/instances").resolve(id);java.nio.file.Files.createDirectories(folder.resolve(".minecraft/mods"));
  var record=new LocalInstance(id,UUID.randomUUID().toString(),"Mon aventure · vérification visuelle",folder.toString(),"1.20.1",ModLoader.FABRIC,"0.16.0","",null);
  String json=new com.google.gson.Gson().toJson(record);java.nio.file.Files.createDirectories(temp.resolve("inventory"));java.nio.file.Files.writeString(temp.resolve("inventory").resolve(id+".json"),json);java.nio.file.Files.writeString(folder.resolve(".cookiecore-instance.json"),json);java.nio.file.Files.writeString(folder.resolve("instance.cfg"),"InstanceType=OneSix\nname=Mon aventure\n");java.nio.file.Files.writeString(folder.resolve("mmc-pack.json"),"{\"formatVersion\":1,\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.20.1\"},{\"uid\":\"net.fabricmc.fabric-loader\",\"version\":\"0.16.0\"}]}");
  var loaded=new CompletableFuture<InstanceLibraryController>();

  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}assertTrue(ready.await(10,TimeUnit.SECONDS));
  Platform.runLater(()->{try{var ui=new InstanceLibraryController(new InstanceManager(temp),id,List.of(),null,()->{});var root=ui.scene().getRoot();root.applyCss();root.layout();var detail=(Label)root.lookup("#instanceDetails");detail.textProperty().addListener((o,a,b)->{if(b.contains("Installée"))loaded.complete(ui);});ui.reload(id);}catch(Throwable error){loaded.completeExceptionally(error);}});
  var controller=loaded.get(15,TimeUnit.SECONDS);
  var done=new CompletableFuture<Void>();Platform.runLater(()->{var warnings=new ArrayList<String>();var logger=java.util.logging.Logger.getLogger("javafx.css");var handler=new java.util.logging.Handler(){public void publish(java.util.logging.LogRecord r){if(r.getLevel().intValue()>=900)warnings.add(r.getMessage());}public void flush(){}public void close(){}};logger.addHandler(handler);
   try{
    var ui=controller;var root=(BorderPane)ui.scene().getRoot();root.resize(1050,760);root.applyCss();root.layout();
    for(Theme theme:Theme.values())for(int[] size:new int[][]{{1050,760},{1280,720},{853,540},{780,540}}){ThemeService.preview(theme.colors());root.resize(size[0],size[1]);root.applyCss();root.layout();root.layout();var scroll=(ScrollPane)root.getCenter();assertTrue(scroll.getContent().getLayoutBounds().getWidth()<=scroll.getViewportBounds().getWidth()+1,theme+" "+size[0]);if(theme==Theme.AMETHYSTE&&size[0]==1050)ThemeViewsTest.snapshot(root,"build/ui-instance-library.png");}
    assertTrue(warnings.isEmpty(),String.join("\n",warnings));done.complete(null);
   }catch(Throwable error){done.completeExceptionally(error);}finally{logger.removeHandler(handler);ThemeService.preview(Theme.AMETHYSTE.colors());}
  });done.get(60,TimeUnit.SECONDS);
 }
}
