package be.cookies.studio.core.controller;
public class VanillaController {
    private java.util.function.Consumer<Runnable> accountRequest;
    public void setAccountRequest(java.util.function.Consumer<Runnable> request){accountRequest=request;}
    @javafx.fxml.FXML private javafx.scene.control.Button libraryButton;
    @javafx.fxml.FXML private void openLibrary(){InstanceLibraryController.open(libraryButton.getScene().getWindow(),null,java.util.List.of(),accountRequest,()->{},null);}
}
