package be.cookies.studio.core.controller;

import be.cookies.studio.core.ui.ImmersiveShell;
import be.cookies.studio.core.service.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Captures des vrais FXML, sans réseau, connexion ni création de données joueur. */
class ImmersiveVisualTest {
    private <T>T fx(Callable<T> work)throws Exception{
        var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}
        assertTrue(ready.await(10,TimeUnit.SECONDS));var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(work.call());}catch(Throwable e){result.completeExceptionally(e);}});return result.get(20,TimeUnit.SECONDS);
    }
    @Test void realViewsRenderAfterArtworkLoadAtBothDesktopSizes()throws Exception{
        var loader=new FXMLLoader(getClass().getResource("/view/shell-view.fxml"));loader.setControllerFactory(t->new ShellController(false));
        ImmersiveShell shell=fx(()->loader.load());
        Stage stage=fx(()->{var window=new Stage();window.setScene(new Scene(shell,1280,720));ThemeService.attach(window.getScene());window.show();return window;});
        try {
            for(String id:new String[]{"accueilButton","instancesButton","vanillaButton","downloadsButton","settingsButton","accountButton"}){
                fx(()->{((Button)loader.getNamespace().get(id)).fire();return null;});Thread.sleep(450);
                for(int[] size:new int[][]{{760,480},{1280,720},{1920,1080},{3440,1440}})fx(()->{
                    shell.resize(size[0],size[1]);shell.applyCss();shell.layout();shell.layout();assertNotNull(shell.displayedImage());
                    for(var node:shell.lookupAll(".scroll-pane"))if(node instanceof ScrollPane scroll)scroll.setVvalue(0);shell.layout();
                    var page=(javafx.scene.layout.Region)shell.lookup(".page");
                    assertNotNull(page,id);
                    var host=(javafx.scene.layout.Region)page.getParent();
                    assertEquals((host.getWidth()-page.getWidth())/2,page.getLayoutX(),1,id+" centered");
                    assertTrue(page.getWidth()<=host.getWidth()+1,id+" fits");
                    for(var node:page.lookupAll(".scroll-pane"))if(node instanceof ScrollPane scroll){
                        assertTrue(scroll.getContent().getLayoutBounds().getWidth()<=scroll.getViewportBounds().getWidth()+2,id+" scroll content fits");
                    }
                    var shade=(javafx.scene.layout.Region)shell.lookup(".world-shade");assertEquals(size[0],shade.getWidth(),.5);
                    ThemeViewsTest.snapshot(shell,"build/glass-"+id+"-"+size[0]+".png");return null;
                });
            }
        }finally{fx(()->{stage.close();return null;});}
    }
}
