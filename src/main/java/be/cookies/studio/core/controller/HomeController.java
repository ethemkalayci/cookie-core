package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.NewsItem;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import org.girod.javafx.svgimage.SVGLoader;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;


import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;

public class HomeController {
    @FXML
    private Button newsButton;
    @FXML
    private TilePane newsGrid;

    @FXML
    private void initialize() {
        newsButton.setGraphic(loadIcon("notification.svg"));
        loadNews();
        initializeNewsCardClips();
    }


    private void loadNews() {
        NewsItem item = new NewsItem(
                "/images/banners/news/test2.jpg",
                "Annonce",
                "Ajout des annonces dynamique",
                "Les annonces sont créées via une classe métier et ne sont plus créées en dur dans le fichier FXML.",
                LocalDate.now()
        );

        URL ressource = getClass().getResource("/view/newsCard.fxml");

        if (ressource == null) {
            System.err.println("Le fichier cible n'est pas trouvé");
            return;
        }

        FXMLLoader file = new FXMLLoader(ressource);

        try {
            VBox card = file.load();
            NewsCardController newsCardController = file.getController();
            newsCardController.setNewsItem(item);

            applyRoundedClip(card);

            newsGrid.getChildren().add(card);
        }catch (IOException e) {
            e.printStackTrace();
        }
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