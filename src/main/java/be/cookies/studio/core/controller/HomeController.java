package be.cookies.studio.core.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import org.girod.javafx.svgimage.SVGLoader;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;


import java.net.URL;

public class HomeController {
    @FXML
    private Button newsButton;

    @FXML
    private TilePane newsGrid;

    @FXML
    private void initialize() {
        newsButton.setGraphic(loadIcon("notification.svg"));
        initializeNewsCardClips();
    }


    private void applyRoundedClip(VBox card) {
        Rectangle clip = new Rectangle();

        // Rayon des coins : 14 pixels
        clip.setArcWidth(28);
        clip.setArcHeight(28);

        // Adapter le masque aux dimensions de la carte
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());

        // Appliquer le masque
        card.setClip(clip);
    }


    private void initializeNewsCardClips() {
        for (Node node : newsGrid.getChildren()) {
            if (node instanceof VBox card) {
                applyRoundedClip(card);
            }
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
}