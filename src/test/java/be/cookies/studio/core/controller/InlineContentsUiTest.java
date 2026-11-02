package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.*;
import be.cookies.studio.core.ui.InstanceContentsPane;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class InlineContentsUiTest {
 @TempDir Path temp;
 @Test void actualModMetadataRendersInlineWithLogoAndName()throws Exception{
  String id="cookiecore-personal-"+UUID.randomUUID();Path folder=temp.resolve("prism-data/instances").resolve(id);Files.createDirectories(folder.resolve(".minecraft/mods"));
  var record=new LocalInstance(id,UUID.randomUUID().toString(),"UI fixture",folder.toString(),"1.20.1",ModLoader.FABRIC,"0.16.0","",null);String json=new com.google.gson.Gson().toJson(record);Files.createDirectories(temp.resolve("inventory"));Files.writeString(temp.resolve("inventory").resolve(id+".json"),json);Files.writeString(folder.resolve(".cookiecore-instance.json"),json);
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(48,48,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  try(var zip=new ZipOutputStream(Files.newOutputStream(folder.resolve(".minecraft/mods/fixture.jar")))){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write("{\"id\":\"fixture\",\"name\":\"Mod de vérification visuelle\",\"version\":\"1.0\",\"icon\":\"icon.png\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("icon.png"));zip.write(bytes.toByteArray());zip.closeEntry();}
  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}assertTrue(ready.await(10,TimeUnit.SECONDS));var done=new CompletableFuture<Void>();
  Platform.runLater(()->{try{var pane=new InstanceContentsPane(new InstanceManager(temp));ThemeService.attach(new Scene(pane,700,440));pane.resize(700,440);pane.applyCss();pane.layout();ListView<?> list=(ListView<?>)pane.lookup("#inlineContentList");list.getItems().addListener((ListChangeListener<Object>)c->{if(list.getItems().size()==1)Platform.runLater(()->{try{pane.applyCss();pane.layout();assertTrue(pane.lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("Mod de vérification visuelle")));assertFalse(pane.lookupAll(".image-view").isEmpty());ThemeViewsTest.snapshot(pane,"build/ui-inline-contents.png");done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});});pane.show(id,InstanceContentService.Kind.MODS);}catch(Throwable e){done.completeExceptionally(e);}});done.get(20,TimeUnit.SECONDS);
 }

 private <T> T fx(Callable<T> action)throws Exception{
  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}assertTrue(ready.await(10,TimeUnit.SECONDS));
  var future=new CompletableFuture<T>();Platform.runLater(()->{try{future.complete(action.call());}catch(Throwable e){future.completeExceptionally(e);}});return future.get(10,TimeUnit.SECONDS);
 }
 private String fixture(String name)throws Exception{
  String id="cookiecore-personal-"+UUID.randomUUID();Path folder=temp.resolve("prism-data/instances").resolve(id);Files.createDirectories(folder.resolve(".minecraft/mods"));Files.createDirectories(temp.resolve("inventory"));
  var record=new LocalInstance(id,UUID.randomUUID().toString(),name,folder.toString(),"1.20.1",ModLoader.FABRIC,"0.16.0","",null);String json=new com.google.gson.Gson().toJson(record);Files.writeString(temp.resolve("inventory").resolve(id+".json"),json);Files.writeString(folder.resolve(".cookiecore-instance.json"),json);
  try(var zip=new ZipOutputStream(Files.newOutputStream(folder.resolve(".minecraft/mods/"+name+".jar")))){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(("{\"id\":\""+name+"\",\"name\":\""+name+"\",\"version\":\"1.0\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return id;
 }
 @Test void changingInstanceResetsModsClearsSearchAndRejectsStaleContent()throws Exception{
  String first=fixture("First"),second=fixture("Second");var loadedFirst=new CompletableFuture<Void>();var loadedSecond=new CompletableFuture<Void>();
  InstanceContentsPane pane=fx(()->{
   var p=new InstanceContentsPane(new InstanceManager(temp));ThemeService.attach(new Scene(p,700,440));p.resize(700,440);p.applyCss();p.layout();
   var list=(ListView<?>)p.lookup("#inlineContentList");list.getItems().addListener((ListChangeListener<Object>)c->{if(list.getItems().size()==1){var item=(InstanceContentService.Item)list.getItems().getFirst();if(item.name().equals("First"))loadedFirst.complete(null);if(item.name().equals("Second"))loadedSecond.complete(null);}});
   p.show(first,InstanceContentService.Kind.SHADERS);((TextField)p.lookup("#contentSearch")).setText("no match");p.selectInstance(second);assertEquals(InstanceContentService.Kind.MODS,p.selectedKind());return p;
  });
  loadedSecond.get(10,TimeUnit.SECONDS);
  fx(()->{var list=(ListView<?>)pane.lookup("#inlineContentList");assertEquals("Second",((InstanceContentService.Item)list.getItems().getFirst()).name());assertEquals("",((TextField)pane.lookup("#contentSearch")).getText());
   ((TextField)pane.lookup("#contentSearch")).setText("absent");assertTrue(list.getItems().isEmpty());pane.show(second,InstanceContentService.Kind.RESOURCE_PACKS);pane.selectInstance(first);assertEquals(InstanceContentService.Kind.MODS,pane.selectedKind());return null;});
  loadedFirst.get(10,TimeUnit.SECONDS);
  fx(()->{var list=(ListView<?>)pane.lookup("#inlineContentList");assertEquals("First",((InstanceContentService.Item)list.getItems().getFirst()).name());assertTrue(list.getClip() instanceof javafx.scene.shape.Rectangle);assertTrue(((javafx.scene.shape.Rectangle)list.getClip()).getArcWidth()>0);
   var progress=(ProgressBar)pane.lookup(".progress-bar");var clip=progress.getClip();progress.setProgress(-1);progress.setVisible(true);progress.setManaged(true);pane.applyCss();pane.layout();assertTrue(((javafx.scene.shape.Rectangle)clip).getArcWidth()>0);progress.setProgress(.5);assertSame(clip,progress.getClip());
   pane.show(first,InstanceContentService.Kind.SHADERS);pane.selectInstance(first);assertEquals(InstanceContentService.Kind.SHADERS,pane.selectedKind());return null;});
 }
}
