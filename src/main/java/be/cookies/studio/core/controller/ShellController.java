package be.cookies.studio.core.controller;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import javafx.scene.Group;
import org.girod.javafx.svgimage.SVGLoader;

import javax.swing.text.AbstractDocument;

import static javafx.fxml.FXMLLoader.load;

public class ShellController {

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

    private Button activeButton;

    @FXML
    private void initialize() {
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
        accountButton.setOnAction(event -> {
            selectButton(accountButton);
            loadView("account-view.fxml");
        });

        selectButton(accueilButton);
    }

    private void loadView(String fileName){
        URL ressource = getClass().getResource("/view/"+fileName);
        if (ressource == null){
            IO.println("le fichier cible n'est pas trouvé");
            return;
        }
        FXMLLoader file = new FXMLLoader(ressource);
        try{
            Parent page = file.load();
            contentContainer.setCenter(page);
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

            return new Group(svg);
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
            animateButton(activeButton, 1.0);
        }

        selectedButton.getStyleClass().add("active");
        animateButton(selectedButton, 1.05);

        activeButton = selectedButton;
    }

    private void animateButton(Button btn, double target) {

        Duration duration = Duration.millis(300);

        // 1. Animation de l'échelle
        ScaleTransition scaleAnimation =
                new ScaleTransition(duration, btn);

        scaleAnimation.setToX(target);
        scaleAnimation.setToY(target);
        scaleAnimation.setInterpolator(Interpolator.EASE_BOTH);


        // 2. Détermination des couleurs
        boolean activating = target > 1.0;

        Color transparent = Color.web("#7040B6", 0.0);
        Color purple = Color.web("#7040B6", 1.0);

        Color startColor = activating ? transparent : purple;
        Color endColor = activating ? purple : transparent;


        // 3. Progression de l'animation
        DoubleProperty progress = new SimpleDoubleProperty(0);

        progress.addListener((observable, oldValue, newValue) -> {

            Color color = startColor.interpolate(
                    endColor,
                    newValue.doubleValue()
            );

            // Reconstruction du fond avec la nouvelle couleur
            BackgroundFill fill = new BackgroundFill(
                    color,
                    new CornerRadii(btn.getFont().getSize() * 0.7),
                    Insets.EMPTY
            );

            Background background = new Background(fill);

            btn.setBackground(background);
        });


        // 4. Animation de la progression de 0 à 1
        Timeline colorAnimation = new Timeline(
                new KeyFrame(
                        duration,
                        new KeyValue(
                                progress,
                                1.0,
                                Interpolator.EASE_BOTH
                        )
                )
        );


        // 5. Lancement simultané des deux animations
        ParallelTransition transition = new ParallelTransition(
                scaleAnimation,
                colorAnimation
        );

        transition.play();
    }
}