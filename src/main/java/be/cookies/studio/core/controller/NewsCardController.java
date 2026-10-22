
package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.NewsItem;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.time.format.DateTimeFormatter;

public class NewsCardController {

    private static final double IMAGE_WIDTH = 280;
    private static final double IMAGE_HEIGHT = 145;

    @FXML
    private ImageView newsImage;

    @FXML
    private Label categorieLabel;

    @FXML
    private Label titreLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label dateLabel;

    public void setNewsItem(NewsItem news) {

        // Mise à jour des textes
        categorieLabel.setText(news.getCategorie());
        titreLabel.setText(news.getTitre());
        descriptionLabel.setText(news.getDescription());

        // Mise à jour de la date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dateLabel.setText(news.getPublicationDate().format(formatter));

        // Récupération de l'adresse de l'image
        String imagePath = news.getImagePath();
        String imageUrl;

        if (imagePath.startsWith("https://")
                || imagePath.startsWith("http://")) {

            imageUrl = imagePath;

        } else {

            URL resource = getClass().getResource(imagePath);

            if (resource == null) {
                throw new IllegalArgumentException(
                        "Image introuvable : " + imagePath
                );
            }

            imageUrl = resource.toExternalForm();
        }

        // Configuration de la zone d'affichage
        newsImage.setFitWidth(IMAGE_WIDTH);
        newsImage.setFitHeight(IMAGE_HEIGHT);
        newsImage.setPreserveRatio(true);
        newsImage.setSmooth(true);
        newsImage.setViewport(null);

        // Chargement de l'image en arrière-plan
        Image image = new Image(imageUrl, true);
        newsImage.setImage(image);

        // Attendre la fin du chargement
        image.progressProperty().addListener(
                (observable, oldValue, newValue) -> {

                    if (newValue.doubleValue() >= 1.0
                            && !image.isError()) {

                        applyImageCrop(image);
                    }
                }
        );

        // Cas où l'image est déjà chargée
        if (image.getProgress() >= 1.0 && !image.isError()) {
            applyImageCrop(image);
        }
    }

    private void applyImageCrop(Image image) {

        // Éviter de recadrer une ancienne image
        if (newsImage.getImage() != image) {
            return;
        }

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        double targetRatio = IMAGE_WIDTH / IMAGE_HEIGHT;
        double imageRatio = imageWidth / imageHeight;

        double cropWidth;
        double cropHeight;

        if (imageRatio > targetRatio) {

            // Image trop large : recadrer les côtés
            cropHeight = imageHeight;
            cropWidth = imageHeight * targetRatio;

        } else {

            // Image trop haute : recadrer le haut et le bas
            cropWidth = imageWidth;
            cropHeight = imageWidth / targetRatio;
        }

        // Centrer le recadrage
        double cropX = (imageWidth - cropWidth) / 2;
        double cropY = (imageHeight - cropHeight) / 2;

        Rectangle2D viewport = new Rectangle2D(
                cropX,
                cropY,
                cropWidth,
                cropHeight
        );

        newsImage.setViewport(viewport);
    }
}
