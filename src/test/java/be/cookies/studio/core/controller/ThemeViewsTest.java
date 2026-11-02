package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ThemeViewsTest {
 @TempDir Path root;
 private void fx(Callable<Void> action) throws Exception {
  var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}
  assertTrue(ready.await(10,TimeUnit.SECONDS));var done=new CompletableFuture<Void>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});done.get(40,TimeUnit.SECONDS);
 }
 private FXMLLoader loader(String view){var l=new FXMLLoader(getClass().getResource("/view/"+view+"-view.fxml"));l.setControllerFactory(c->{
  if(c==HomeController.class)return new HomeController(false);if(c==AccountController.class)return new AccountController(false);if(c==InstanceController.class)return new InstanceController(false);if(c==SettingsController.class)return new SettingsController(new SettingsService(root));
  try{return c.getConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}
 });return l;}
 @Test void allExistingViewsInheritEveryPaletteWithoutRecreation() throws Exception {fx(()->{
  for(String name:List.of("home","instance","account","settings","vanilla","downloads")){
   var l=loader(name);Parent view=l.load();Scene scene=new Scene(view,1200,900);ThemeService.attach(scene);
   for(var theme:Theme.values()){
    ThemeService.preview(theme.colors());view.applyCss();view.layout();
    assertEquals(Color.web(theme.colors().get(ThemeColor.BACKGROUND)),((Region)view).getBackground().getFills().getFirst().getFill(),name+" "+theme);
    Node viewport=view.lookup(".viewport");if(viewport instanceof Region r)assertEquals(Color.web(theme.colors().get(ThemeColor.BACKGROUND)),r.getBackground().getFills().getFirst().getFill());
   }
  }ThemeService.preview(Theme.AMETHYSTE.colors());return null;
 });}
 @Test void editorHasEveryVariableAndPreviewsCancelsAndResets() throws Exception {fx(()->{
  var l=loader("settings");Parent view=l.load();Scene scene=new Scene(view,1200,900);ThemeService.attach(scene);ThemeService.preview(Theme.AMETHYSTE.colors());view.applyCss();view.layout();
  VBox rows=(VBox)view.lookup("#colorRows");assertEquals(ThemeColor.values().length,rows.getChildren().stream().filter(n->n instanceof FlowPane).count());
  FlowPane first=(FlowPane)rows.getChildren().stream().filter(n->n instanceof FlowPane).findFirst().orElseThrow();
  TextField hex=(TextField)first.getChildren().stream().filter(n->n instanceof TextField).findFirst().orElseThrow();
  hex.setText("#123456");view.applyCss();assertEquals(Color.web("#123456"),((Region)view).getBackground().getFills().getFirst().getFill());
  assertEquals("#0E0B1D",Theme.AMETHYSTE.colors().get(ThemeColor.BACKGROUND));
  Button reset=(Button)first.getChildren().stream().filter(n->n instanceof Button).findFirst().orElseThrow();reset.fire();view.applyCss();assertEquals(Color.web("#0E0B1D"),((Region)view).getBackground().getFills().getFirst().getFill());
  hex.setText("#654321");((SettingsController)l.getController()).dispose();view.applyCss();assertEquals(Color.web("#0E0B1D"),((Region)view).getBackground().getFills().getFirst().getFill());
  ThemeService.preview(Theme.AMETHYSTE.colors());view.applyCss();view.layout();snapshot(view,"build/settings-amethyste.png");
  ThemeService.preview(Theme.NEON.colors());view.applyCss();view.layout();snapshot(view,"build/settings-neon.png");
  ThemeService.preview(Theme.AMETHYSTE.colors());return null;
 });}

 @Test void editorSavesCustomThemeThroughItsRealAction() throws Exception {
  final Parent[] view=new Parent[1];
  fx(()->{
   var l=loader("settings");view[0]=l.load();ThemeService.attach(new Scene(view[0],1200,900));view[0].applyCss();view[0].layout();
   ((TextField)view[0].lookup("#themeName")).setText("Mon test");
   VBox rows=(VBox)view[0].lookup("#colorRows");FlowPane row=(FlowPane)rows.getChildren().stream().filter(n->n instanceof FlowPane).findFirst().orElseThrow();
   ((TextField)row.getChildren().stream().filter(n->n instanceof TextField).findFirst().orElseThrow()).setText("#123456");
   Button save=(Button)view[0].lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Enregistrer le thème")).findFirst().orElseThrow();save.fire();return null;
  });
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
  while(!java.nio.file.Files.exists(root.resolve("settings.json"))&&System.nanoTime()<deadline)Thread.sleep(50);
  var loaded=new SettingsService(root).current();assertTrue(loaded.activeTheme().startsWith("custom-"));assertEquals("#123456",loaded.activeColors().get(ThemeColor.BACKGROUND));
  fx(()->{ThemeService.preview(Theme.AMETHYSTE.colors());return null;});
 }

 @Test void persistedPalettesReloadInSettingsAndMinecraftTabRenders() throws Exception {
  for(var theme:Theme.values()){
   new SettingsService(root).update(c->c.appearance(theme.name(),c.customThemes()));
   fx(()->{
    var l=loader("settings");Parent view=l.load();ThemeService.attach(new Scene(view,1200,900));ThemeService.preview(new SettingsService(root).current().activeColors());view.applyCss();view.layout();
    assertEquals(theme.label,((ComboBox<?>)view.lookup("#themes")).getValue().toString());
    snapshot(view,"build/settings-"+theme.name().toLowerCase(java.util.Locale.ROOT)+".png");
    if(theme==Theme.AMETHYSTE){((TabPane)view.lookup(".tab-pane")).getSelectionModel().select(1);view.applyCss();view.layout();snapshot(view,"build/settings-minecraft.png");}
    return null;
   });
  }
  fx(()->{ThemeService.preview(Theme.AMETHYSTE.colors());return null;});
 }

 @Test void minecraftTabContainsActualControlsAndRenders() throws Exception {
  Parent[] page=new Parent[1];
  fx(()->{var l=loader("settings");page[0]=l.load();ThemeService.attach(new Scene(page[0],1200,900));ThemeService.preview(Theme.AMETHYSTE.colors());page[0].applyCss();page[0].layout();((TabPane)page[0].lookup(".tab-pane")).getSelectionModel().select(1);return null;});
  Thread.sleep(300);
  fx(()->{page[0].applyCss();page[0].layout();assertEquals(1,((TabPane)page[0].lookup(".tab-pane")).getSelectionModel().getSelectedIndex());assertNotNull(page[0].lookup("#ramSlider"));assertNotNull(page[0].lookup("#installPath"));snapshot(page[0],"build/minecraft-storage-verified.png");return null;});
 }
 @Test void dialogsAndColorPopupResolveColorsBeforeAndDuringDisplay() throws Exception {fx(()->{
  var messages=new java.util.ArrayList<String>();
  var logger=java.util.logging.Logger.getLogger("javafx.css");
  var handler=new java.util.logging.Handler(){
   public void publish(java.util.logging.LogRecord r){if(r.getLevel().intValue()>=java.util.logging.Level.WARNING.intValue())messages.add(r.getMessage());}
   public void flush(){} public void close(){}
  };
  logger.addHandler(handler);
  var owner=new javafx.stage.Stage();
  var picker=new ColorPicker();var content=new VBox(picker);
  var scene=new Scene(content,300,200);ThemeService.attach(scene);ThemeService.preview(Theme.NEON.colors());owner.setScene(scene);
  Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"Test",ButtonType.OK);
  try {
   owner.show();ThemeService.attach(alert);alert.initOwner(owner);alert.setHeaderText("Confirmation");
   // Dialog layout happens before the window enters Window.getWindows().
   alert.getDialogPane().getScene().getStylesheets().addAll(scene.getStylesheets());
   alert.getDialogPane().applyCss();
   alert.show();alert.getDialogPane().applyCss();
   assertEquals(Color.web(Theme.NEON.colors().get(ThemeColor.TEXT)),((Label)alert.getDialogPane().lookup(".label")).getTextFill());
   picker.show();
   for(var window:javafx.stage.Window.getWindows())window.getScene().getRoot().applyCss();
   ThemeService.preview(Theme.AMBRE.colors());
   for(var window:javafx.stage.Window.getWindows())window.getScene().getRoot().applyCss();
   assertTrue(messages.isEmpty(),String.join("\n",messages));
  } finally {picker.hide();alert.close();owner.close();logger.removeHandler(handler);ThemeService.preview(Theme.AMETHYSTE.colors());}
  return null;
 });}

 @Test void fallbackPaletteMatchesAmethyste() throws Exception {
  String css;
  try(var stream=getClass().getResourceAsStream("/css/style.css")){css=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  for(var key:ThemeColor.values())assertTrue(css.contains(key.css()+": "+Theme.AMETHYSTE.colors().get(key)+";"),key.name());
 }

 static void snapshot(Parent view,String path)throws Exception{var shot=view.snapshot(null,null);var out=new java.awt.image.BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,shot.getPixelReader().getArgb(x,y));javax.imageio.ImageIO.write(out,"png",new java.io.File(path));}
}

