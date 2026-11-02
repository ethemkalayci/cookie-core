package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopUiTest {
 private void fx(Callable<Void> action) throws Exception {
  var ready=new CountDownLatch(1);
  try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}
  assertTrue(ready.await(10,TimeUnit.SECONDS));var done=new CompletableFuture<Void>();
  Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});done.get(90,TimeUnit.SECONDS);
 }
 @Test void actualShellNavigatesAtDesktopAndScaledLogicalSizesWithoutHorizontalOverflow() throws Exception {fx(()->{
  var warnings=new ArrayList<String>();var logger=java.util.logging.Logger.getLogger("javafx.css");
  var handler=new java.util.logging.Handler(){public void publish(java.util.logging.LogRecord r){if(r.getLevel().intValue()>=900)warnings.add(r.getMessage());}public void flush(){}public void close(){}};
  logger.addHandler(handler);
  try {
   var loader=new FXMLLoader(getClass().getResource("/view/shell-view.fxml"));loader.setControllerFactory(c->new ShellController(false));
   BorderPane shell=loader.load();ThemeService.attach(new Scene(shell,1280,720));
   String[] buttons={"accueilButton","instancesButton","vanillaButton","downloadsButton","settingsButton","accountButton"};
   int[][] sizes={{1280,720},{1366,768},{1600,900},{1920,1080},{1024,576},{853,480},{760,480}};
   var host=(BorderPane)loader.getNamespace().get("contentContainer");
   for(String id:buttons){
    var button=(Button)loader.getNamespace().get(id);button.fire();
    assertNotNull(host.getCenter(),id);assertTrue(button.getStyleClass().contains("active"),id);
    for(Theme theme:Theme.values())for(var size:sizes){
     ThemeService.preview(theme.colors());shell.resize(size[0],size[1]);shell.applyCss();shell.layout();shell.layout();
     assertEquals(size[0],shell.getWidth(),.5);
     var accountButton=(Button)loader.getNamespace().get("accountButton");
     var accountBounds=accountButton.localToScene(accountButton.getBoundsInLocal());
     assertTrue(accountBounds.getMaxY()<=size[1], "Account navigation must remain visible at "+size[1]);
     Parent page=(Parent)host.getCenter();
     ScrollPane scroll=page instanceof ScrollPane p?p:(ScrollPane)page.lookup("#homeScrollPane");
     assertNotNull(scroll,id);
     assertTrue(scroll.getViewportBounds().getWidth()>0,id);
     assertTrue(scroll.getContent().getLayoutBounds().getWidth()<=scroll.getViewportBounds().getWidth()+1,
       id+" "+theme+" "+size[0]+" content="+scroll.getContent().getLayoutBounds().getWidth()+" viewport="+scroll.getViewportBounds().getWidth());
     if(theme==Theme.AMETHYSTE && (size[0]==1280||size[0]==853)){
      host.getCenter().setOpacity(1);
      ThemeViewsTest.snapshot(shell,"build/ui-"+id+"-"+size[0]+".png");
     }
    }
   }
   assertTrue(warnings.isEmpty(),String.join("\n",warnings));
  }finally{logger.removeHandler(handler);ThemeService.preview(Theme.AMETHYSTE.colors());}
  return null;
 });}
 @Test void homeActionsAndLightCustomPaletteRemainUsable() throws Exception {fx(()->{
  var loader=new FXMLLoader(getClass().getResource("/view/home-view.fxml"));loader.setControllerFactory(c->new HomeController(false));
  Parent page=loader.load();ThemeService.attach(new Scene(page,800,500));HomeController controller=loader.getController();
  var clicks=new java.util.concurrent.atomic.AtomicInteger();controller.setInstanceActions(clicks::incrementAndGet,null);
  var colors=new EnumMap<ThemeColor,String>(Theme.AMETHYSTE.colors());
  colors.put(ThemeColor.BACKGROUND,"#F7F5FA");colors.put(ThemeColor.CONTENT,"#FFFFFF");colors.put(ThemeColor.CARD,"#EEEAF4");
  colors.put(ThemeColor.TEXT,"#171322");colors.put(ThemeColor.TEXT_MUTED,"#3A3448");colors.put(ThemeColor.BANNER_TEXT,"#171322");
  ThemeService.preview(colors);page.applyCss();page.layout();
  ((Button)loader.getNamespace().get("lastInstancePlay")).fire();assertEquals(1,clicks.get());
  ((Button)loader.getNamespace().get("newsButton")).fire();
  assertTrue(((ScrollPane)loader.getNamespace().get("homeScrollPane")).getVvalue()>0);
  ((ScrollPane)loader.getNamespace().get("homeScrollPane")).setVvalue(0);page.layout();
  ThemeViewsTest.snapshot(page,"build/ui-light-custom.png");
  ThemeService.preview(Theme.AMETHYSTE.colors());return null;
 });}
 @Test void longHomeTitleAndNewsCardsFitNarrowLayout() throws Exception {fx(()->{
  var loader=new FXMLLoader(getClass().getResource("/view/home-view.fxml"));loader.setControllerFactory(c->new HomeController(false));
  Parent page=loader.load();ThemeService.attach(new Scene(page,528,600));
  ((Label)loader.getNamespace().get("lastInstanceName")).setText("Une très longue aventure Minecraft à explorer ensemble dans de nouveaux mondes");
  var grid=(TilePane)loader.getNamespace().get("newsGrid");
  for(int i=0;i<3;i++){
   var cardLoader=new FXMLLoader(getClass().getResource("/view/newsCard.fxml"));VBox card=cardLoader.load();card.prefWidthProperty().bind(grid.prefTileWidthProperty());
   ((Label)cardLoader.getNamespace().get("titreLabel")).setText("Un titre d’actualité long qui doit rester entièrement lisible");
   assertNotNull(((Button)cardLoader.getNamespace().get("viewNewsButton")).getOnAction());
   grid.getChildren().add(card);
  }
  page.applyCss();page.layout();page.layout();
  var title=(Label)loader.getNamespace().get("lastInstanceName");
  var play=(Button)loader.getNamespace().get("lastInstancePlay");
  var banner=(StackPane)loader.getNamespace().get("bannerPane");
  assertTrue(title.localToScene(title.getBoundsInLocal()).getMaxY()<=play.localToScene(play.getBoundsInLocal()).getMinY());
  assertTrue(play.localToScene(play.getBoundsInLocal()).getMaxY()<=banner.localToScene(banner.getBoundsInLocal()).getMaxY());
  for(Node card:grid.getChildren())assertTrue(card.getBoundsInParent().getMaxX()<=grid.getWidth()+1);
  var scroll=(ScrollPane)loader.getNamespace().get("homeScrollPane");scroll.setVvalue(1);page.layout();
  ThemeViewsTest.snapshot(page,"build/ui-news-narrow.png");return null;
 });}
 @Test void nativeWindowScaleAndRevealAnimationFinishCleanly() throws Exception {
  var complete=new CompletableFuture<Void>();var window=new javafx.stage.Stage[1];
  try {
   fx(()->{
    window[0]=new javafx.stage.Stage();var content=new VBox(new Label("Cookie Core"));
    var scene=new Scene(content,400,240);ThemeService.attach(scene);window[0].setScene(scene);window[0].show();
    String expected=System.getProperty("cookiecore.expectedScale");
    if(expected!=null)assertEquals(Double.parseDouble(expected),window[0].getOutputScaleX(),.01);
    var transition=be.cookies.studio.core.ui.ViewSupport.reveal(content);
    transition.setOnFinished(event->{try{assertEquals(1,content.getOpacity(),.001);complete.complete(null);}catch(Throwable e){complete.completeExceptionally(e);}});
    return null;
   });
   complete.get(5,TimeUnit.SECONDS);
  } finally {fx(()->{if(window[0]!=null)window[0].close();return null;});}
 }
}
