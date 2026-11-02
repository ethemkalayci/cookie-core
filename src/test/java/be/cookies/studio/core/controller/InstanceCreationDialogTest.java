package be.cookies.studio.core.controller;
import be.cookies.studio.core.model.ModLoader;
import be.cookies.studio.core.service.ThemeService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class InstanceCreationDialogTest {
 private <T>T fx(Callable<T> work)throws Exception{var ready=new CountDownLatch(1);try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException e){ready.countDown();}assertTrue(ready.await(10,TimeUnit.SECONDS));var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(work.call());}catch(Throwable e){result.completeExceptionally(e);}});return result.get(15,TimeUnit.SECONDS);}
 @Test void fixedModalMatchesSupportedChoicesAndCreatesValidatedSelection()throws Exception{
  var compatible=new CompletableFuture<Void>();Stage[] owner={null};InstanceCreationDialog[] dialog={null};
  try{
   fx(()->{owner[0]=new Stage();owner[0].setScene(new Scene(new VBox(),1000,760));ThemeService.attach(owner[0].getScene());owner[0].show();dialog[0]=new InstanceCreationDialog(owner[0],List.of("1.20.1"),(mc,loader)->List.of("47.4.28","47.4.27"));dialog[0].show();var pane=dialog[0].getDialogPane();pane.applyCss();pane.layout();
    assertEquals(StageStyle.UNDECORATED,((Stage)pane.getScene().getWindow()).getStyle());assertFalse(dialog[0].isResizable());assertEquals(Modality.WINDOW_MODAL,dialog[0].getModality());assertNotNull(owner[0].getScene().getRoot().getEffect());
    assertEquals(4,pane.lookupAll(".radio-button").size());assertTrue(((RadioButton)pane.lookup("#creationVANILLA")).isSelected());
    Button create=(Button)pane.lookupButton(pane.getButtonTypes().stream().filter(b->b.getText().equals("Créer")).findFirst().orElseThrow());assertTrue(create.isDisabled());((TextField)pane.lookup("#creationName")).setText("Mon aventure");assertFalse(create.isDisabled());
    var version=(ComboBox<?>)pane.lookup("#creationLoaderVersion");version.valueProperty().addListener((o,a,b)->{if("47.4.28".equals(b))compatible.complete(null);});((RadioButton)pane.lookup("#creationFORGE")).fire();return null;});
   compatible.get(10,TimeUnit.SECONDS);
   fx(()->{var pane=dialog[0].getDialogPane();pane.applyCss();pane.layout();var version=(ComboBox<?>)pane.lookup("#creationLoaderVersion");assertEquals(2,version.getItems().size());assertFalse(version.isDisabled());version.getSelectionModel().select(1);ThemeViewsTest.snapshot(pane,"build/ui-create-instance.png");var type=pane.getButtonTypes().stream().filter(b->b.getText().equals("Créer")).findFirst().orElseThrow();((Button)pane.lookupButton(type)).fire();assertEquals(new InstanceCreationDialog.Selection("Mon aventure","1.20.1",ModLoader.FORGE,"47.4.27"),dialog[0].getResult());assertNull(owner[0].getScene().getRoot().getEffect());return null;});
  }finally{fx(()->{if(dialog[0]!=null)dialog[0].close();if(owner[0]!=null)owner[0].close();return null;});}
 }
}
