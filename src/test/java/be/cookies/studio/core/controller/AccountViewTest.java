package be.cookies.studio.core.controller;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class AccountViewTest {
 @Test void loadsAccountPageAndBindsControls() throws Exception {
  var ready=new CountDownLatch(1);
  try {Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}
  catch(IllegalStateException e){ready.countDown();}
  assertTrue(ready.await(10,TimeUnit.SECONDS));
  var result=new CompletableFuture<Boolean>();
  Platform.runLater(()->{
   try {
    var loader=new FXMLLoader(getClass().getResource("/view/account-view.fxml"));
    loader.setControllerFactory(c -> new AccountController(false));
    Parent root=loader.load(); be.cookies.studio.core.service.ThemeService.attach(new javafx.scene.Scene(root,1100,850));
    root.applyCss();root.layout();
    assertInstanceOf(AccountController.class,loader.getController());
    assertNotNull(root.lookup("#statusLabel"));
    assertFalse(root.lookup("#connectButton").isDisabled());
    assertTrue(root.lookup("#finishButton").isDisabled());
    assertFalse(root.lookup("#finishButton").isVisible());
    AccountController controller=loader.getController();
    controller.displayProfile(java.util.Optional.of(new be.cookies.studio.core.service.PrismAccountService.Profile("0123456789abcdef0123456789abcdef","CookiePlayer")));
    assertEquals("CookiePlayer",((javafx.scene.control.Label)root.lookup("#profileName")).getText());
    assertEquals("Gérer mon compte dans Prism",((javafx.scene.control.Button)root.lookup("#connectButton")).getText());
    root.applyCss();root.layout();
    var shot=root.snapshot(null,null);
    var output=new java.awt.image.BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
    javax.imageio.ImageIO.write(output,"png",new java.io.File("build/account-preview.png"));
    controller.displayProfile(java.util.Optional.empty());
    assertEquals("Se connecter avec Microsoft",((javafx.scene.control.Button)root.lookup("#connectButton")).getText());
    result.complete(true);
   }catch(Throwable e){result.completeExceptionally(e);}
  });
  assertTrue(result.get(15,TimeUnit.SECONDS));
 }
}



