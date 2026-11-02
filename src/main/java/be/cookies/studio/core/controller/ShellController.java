package be.cookies.studio.core.controller;

import javafx.application.HostServices;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.net.URL;
import org.girod.javafx.svgimage.SVGLoader;



public class ShellController {
    private final boolean backgroundLoading;
    public ShellController() { this(true); }
    ShellController(boolean backgroundLoading) { this.backgroundLoading = backgroundLoading; }
    @FXML private BorderPane shellRoot;
    @FXML private javafx.scene.control.ScrollPane sidebarScroll;
    @FXML private javafx.scene.layout.VBox sidebarContent;
    private FadeTransition pageTransition;
    private void configureLoader(FXMLLoader loader) {
        if (backgroundLoading) return;
        loader.setControllerFactory(type -> {
            if (type == HomeController.class) return new HomeController(false);
            if (type == InstanceController.class) return new InstanceController(false);
            if (type == AccountController.class) return new AccountController(false);
            try { return type.getConstructor().newInstance(); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        });
    }
    private void present(Parent page) {
        if (pageTransition != null) { pageTransition.stop(); pageTransition.getNode().setOpacity(1); }
        contentContainer.setCenter(page);
        pageTransition = be.cookies.studio.core.ui.ViewSupport.reveal(page);
    }


    @FXML
    private Button accueilButton;
    @FXML
    private Button instancesButton;
    @FXML
    private Button vanillaButton;
    @FXML
    private Button downloadsButton;
    @FXML
    private Button settingsButton;
    @FXML
    private Button accountButton;

    @FXML
    private BorderPane contentContainer;

    private SettingsController settingsController;
    private Button activeButton;
    private HostServices hostServices;

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;

        // Actualiser l'accueil après réception des HostServices
        loadView("home-view.fxml");
    }

    @FXML
    private void initialize() {
        var compact = javafx.css.PseudoClass.getPseudoClass("compact");
        shellRoot.widthProperty().addListener((observable, before, after) -> shellRoot.pseudoClassStateChanged(compact, after.doubleValue() < 1150));
        shellRoot.heightProperty().addListener((observable, before, after) -> {
            boolean shortWindow = after.doubleValue() < 620;
            shellRoot.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("short"), shortWindow);
            for (var caption : shellRoot.lookupAll(".nav-caption")) { caption.setVisible(!shortWindow); caption.setManaged(!shortWindow); }
        });
        sidebarScroll.viewportBoundsProperty().addListener((observable, before, after) -> sidebarContent.setMinHeight(after.getHeight()));
        loadView("home-view.fxml");

        accueilButton.setGraphic(loadIcon("home.svg"));
        instancesButton.setGraphic(loadIcon("layers.svg"));
        vanillaButton.setGraphic(loadIcon("cube.svg"));
        downloadsButton.setGraphic(loadIcon("download.svg"));
        settingsButton.setGraphic(loadIcon("settings.svg"));
        accountButton.setGraphic(loadIcon("user.svg"));

        accueilButton.setOnAction(event -> {
            selectButton(accueilButton);
            loadView("home-view.fxml");
        });
        instancesButton.setOnAction(event -> {
            selectButton(instancesButton);
            loadView("instance-view.fxml");
        });
        vanillaButton.setOnAction(event -> {
            selectButton(vanillaButton);
            loadView("vanilla-view.fxml");
        });
        downloadsButton.setOnAction(event -> {
            selectButton(downloadsButton);
            loadView("downloads-view.fxml");
        });
        settingsButton.setOnAction(event -> {
            selectButton(settingsButton);
            loadView("settings-view.fxml");
        });
        accountButton.setOnAction(event -> showAccount(null));

        selectButton(accueilButton);
    }

    private void showAccount(Runnable afterConnection) {
        if (settingsController != null) { settingsController.dispose(); settingsController = null; }
        selectButton(accountButton);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/account-view.fxml"));
            configureLoader(loader);
            Parent page = loader.load();
            AccountController controller = loader.getController();
            Runnable connected = () -> {
                selectButton(accueilButton); loadView("home-view.fxml");
                if (afterConnection != null) afterConnection.run();
            };
            controller.setOnConnected(connected);
            present(page);
            if (afterConnection != null) controller.begin(connected);
        } catch (IOException e) {
            var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Impossible d'ouvrir la page de connexion.");
            be.cookies.studio.core.service.ThemeService.attach(alert);
            alert.show();
        }
    }

    private void loadView(String fileName){
        if (settingsController != null) { settingsController.dispose(); settingsController = null; }
        URL ressource = getClass().getResource("/view/"+fileName);
        if (ressource == null){
            IO.println("le fichier cible n'est pas trouvé");
            return;
        }
        FXMLLoader file = new FXMLLoader(ressource);
        try{
            configureLoader(file);
            Parent page = file.load();

            if (fileName.equals("home-view.fxml")) {
                HomeController homeController = file.getController();
                homeController.setHostServices(hostServices);
                homeController.setInstanceActions(() -> { selectButton(instancesButton); loadView("instance-view.fxml"); }, this::showAccount);
            }

            if (file.getController() instanceof InstanceController controller) {
                controller.setAccountRequest(this::showAccount);
                controller.setOnLaunched(() -> { selectButton(accueilButton); loadView("home-view.fxml"); });
            }
            if (file.getController() instanceof VanillaController controller) controller.setAccountRequest(this::showAccount);
            if (file.getController() instanceof SettingsController controller) settingsController = controller;
            present(page);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private Group loadIcon(String fileName) {
        URL resource = getClass().getResource("/images/icones/" + fileName);

        if (resource == null) {
            throw new IllegalStateException("Icône introuvable : " + fileName);
        }

        try {
            var svg = SVGLoader.load(resource);

            var bounds = svg.getLayoutBounds();
            double scale = 20.0 / Math.max(
                    bounds.getWidth(),
                    bounds.getHeight()
            );

            svg.setScaleX(scale);
            svg.setScaleY(scale);

            Group icon = new Group(svg); icon.getStyleClass().add("theme-icon"); return icon;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de charger " + fileName,
                    exception
            );
        }
    }

    private void selectButton(Button selectedButton) {

        if (activeButton == selectedButton) {
            return;
        }

        if (activeButton != null) {
            activeButton.getStyleClass().remove("active");

        }

        selectedButton.getStyleClass().add("active");


        activeButton = selectedButton;
    }

}
