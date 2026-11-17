package be.cookies.studio.core.ui;

import be.cookies.studio.core.service.*;
import be.cookies.studio.core.model.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ImmersiveShellTest {
    @TempDir Path temp;
    private <T>T fx(Callable<T> action)throws Exception{
        var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}
        assertTrue(ready.await(10,TimeUnit.SECONDS));var future=new CompletableFuture<T>();
        Platform.runLater(()->{try{future.complete(action.call());}catch(Throwable e){future.completeExceptionally(e);}});return future.get(10,TimeUnit.SECONDS);
    }
    @Test void latestSelectionWinsAndErrorsFallBackWithoutBlocking()throws Exception{
        var pending=new HashMap<String,CompletableFuture<Image>>();var fallback=new WritableImage(160,90);var latest=new WritableImage(320,180);
        ImmersiveShell shell=fx(()->new ImmersiveShell(path->path==null?CompletableFuture.completedFuture(fallback):pending.computeIfAbsent(path,k->new CompletableFuture<>())));
        fx(()->{shell.showArtwork("old");shell.showArtwork("latest");return null;});
        pending.get("latest").complete(latest);pending.get("old").complete(new WritableImage(50,50));
        Thread.sleep(350);
        fx(()->{assertSame(latest,shell.displayedImage());shell.showArtwork("broken");return null;});
        pending.get("broken").complete(null);Thread.sleep(350);
        fx(()->{assertSame(fallback,shell.displayedImage());return null;});
    }
    @Test void coverCropAndDecorativeLayersRemainSized()throws Exception{
        fx(()->{var image=new WritableImage(1920,1080);var shell=new ImmersiveShell(path->CompletableFuture.completedFuture(image));
            ThemeService.attach(new Scene(shell,1280,720));shell.resize(1280,720);shell.applyCss();shell.layout();
            var shade=(javafx.scene.layout.Region)shell.lookup(".world-shade");assertEquals(1280,shade.getWidth());assertEquals(720,shade.getHeight());assertFalse(shade.isManaged());
            assertFalse(shell.lookup(".glass-tint").getParent().isVisible(),"Pas de verre sans navigation latérale");
            for(double[] size:List.of(new double[]{1280,720},new double[]{1920,1080},new double[]{760,480})){
                var crop=ImmersiveShell.crop(image,size[0],size[1]);assertEquals(size[0]/size[1],crop.getWidth()/crop.getHeight(),.0001);assertTrue(crop.getMinX()>=0&&crop.getMaxX()<=1920);assertTrue(crop.getMinY()>=0&&crop.getMaxY()<=1080);
            }return null;});
    }
    @Test void preferencesPreserveCustomColorsAndAccessibilityAcrossOtherEdits()throws Exception{
        var service=new SettingsService(temp);service.update(c->c.accessibility(true,true));service.update(c->c.appearance("NEON",c.customThemes()));service.update(c->c.minecraft(0,""));
        var reloaded=new SettingsService(temp).current();assertTrue(reloaded.reducedMotion());assertTrue(reloaded.reducedTransparency());assertEquals("NEON",reloaded.activeTheme());
        for(var theme:Theme.values()){
            var saved=theme.colors();var rendered=ThemeService.renderedColors(saved);assertEquals("#101216",rendered.get(ThemeColor.BACKGROUND));assertEquals(saved.get(ThemeColor.PLAY),rendered.get(ThemeColor.PLAY));
            assertTrue(ThemeService.contrast(ThemeService.foreground(saved.get(ThemeColor.PLAY)),saved.get(ThemeColor.PLAY))>=4.5);
        }
        for(String color:List.of("#FFFFFF","#000000","#FFFF00","#FF00FF","#777777"))assertTrue(ThemeService.contrast(color,ThemeService.foreground(color))>=4.5);
    }
    @Test void decoderBoundsImagesAndReusesSuccessfulLoads()throws Exception{
        var image=ArtworkService.load(null).get(10,TimeUnit.SECONDS);assertNotNull(image);assertTrue(image.getWidth()<=1920 && image.getHeight()<=1080);assertSame(image,ArtworkService.load(null).get());
        assertNull(ArtworkService.load(temp.resolve("missing.png").toUri().toString()).get(10,TimeUnit.SECONDS));
    }
}
