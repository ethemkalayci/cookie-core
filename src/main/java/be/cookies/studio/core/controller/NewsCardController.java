
package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.NewsItem;
import be.cookies.studio.core.service.NewsImageCacheService;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

public class NewsCardController {

    private static final double IMAGE_WIDTH = 280;
    @FXML private javafx.scene.layout.VBox newsCardRoot;

    @FXML private void initialize() {
        // Keep the image inside the border, whose width is included in the VBox insets.
        newsImage.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(0, newsCardRoot.getWidth() - newsCardRoot.getInsets().getLeft() - newsCardRoot.getInsets().getRight()),
                newsCardRoot.widthProperty(), newsCardRoot.insetsProperty()));
        newsImage.fitHeightProperty().bind(newsImage.fitWidthProperty().multiply(IMAGE_HEIGHT / IMAGE_WIDTH));
        newsCardRoot.widthProperty().addListener((o, a, b) -> { if (newsImage.getImage() != null) applyImageCrop(newsImage.getImage()); });
        var clip = new javafx.scene.shape.Rectangle();
        // Round only the image's upper corners. Extending the clip below the image
        // keeps its lower edge straight. Never clip the card's outer border.
        clip.setArcWidth(30); clip.setArcHeight(30);
        clip.widthProperty().bind(newsImage.fitWidthProperty());
        clip.heightProperty().bind(newsImage.fitHeightProperty().add(15));
        newsImage.setClip(clip);
        be.cookies.studio.core.ui.ViewSupport.cardHover(newsCardRoot);
    }
    private static final double IMAGE_HEIGHT = 145;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final NewsImageCacheService IMAGE_CACHE =
            new NewsImageCacheService();

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

    private NewsItem item;
    private HostServices hostServices;

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void setNewsItem(NewsItem news) {

        this.item = news;

        categorieLabel.setText(news.getCategorie());
        titreLabel.setText(news.getTitre());
        descriptionLabel.setText(news.getDescription());

        dateLabel.setText(
                news.getPublicationDate().format(DATE_FORMATTER)
        );

        newsImage.setPreserveRatio(true);
        newsImage.setSmooth(true);
        newsImage.setViewport(null);

        // Affichage immédiat de l'image de secours.
        showPlaceholder();

        String imageUrl = news.getImageUrl();

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        // Les ressources internes ne nécessitent pas de cache.
        if (imageUrl.startsWith("/")) {

            URL resource = getClass().getResource(imageUrl);

            if (resource != null) {
                displayImage(resource.toExternalForm());
            }

            return;
        }

        // Résolution de l'image en arrière-plan.
        IMAGE_CACHE.resolve(imageUrl).whenComplete((path, error) -> {

            Platform.runLater(() -> {

                // Ignorer le résultat si la carte a changé.
                if (this.item != news) {
                    return;
                }

                if (error != null) {
                    System.err.println(
                            "Image indisponible pour : "
                                    + news.getTitre()
                                    + " (" + error.getMessage() + ")"
                    );
                    return;
                }

                displayImage(path.toUri().toString());
            });
        });
    }

    private void showPlaceholder() {

        URL resource = getClass().getResource(
                "/images/logo/logo-core.png"
        );

        if (resource == null) {
            System.err.println("Logo de secours introuvable !");
            newsImage.setImage(null);
            return;
        }

        displayImage(resource.toExternalForm(), false);
    }

    private void displayImage(String url) {
        displayImage(url, true);
    }

    private void displayImage(String url, boolean fallbackOnError) {

        final Image image;

        try {
            image = new Image(url, true);
        } catch (IllegalArgumentException e) {
            if (fallbackOnError) {
                showPlaceholder();
            }
            return;
        }

        newsImage.setViewport(null);
        newsImage.setImage(image);

        image.errorProperty().addListener(
                (observable, oldValue, hasError) -> {

                    if (hasError && fallbackOnError
                            && newsImage.getImage() == image) {
                        showPlaceholder();
                    }
                }
        );

        image.progressProperty().addListener(
                (observable, oldValue, newValue) -> {

                    if (newValue.doubleValue() >= 1.0
                            && !image.isError()) {
                        applyImageCrop(image);
                    }
                }
        );

        if (image.isError() && fallbackOnError) {
            showPlaceholder();
        } else if (image.getProgress() >= 1.0
                && !image.isError()) {
            applyImageCrop(image);
        }
    }

    private void applyImageCrop(Image image) {

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

            cropHeight = imageHeight;
            cropWidth = imageHeight * targetRatio;

        } else {

            cropWidth = imageWidth;
            cropHeight = imageWidth / targetRatio;
        }

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

    @FXML
    private void openNews() {

        if (item == null || hostServices == null) {
            System.err.println(
                    "Actualité ou HostServices manquants."
            );
            return;
        }

        try {
            URI uri = URI.create(item.getUrl());

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null) {

                System.err.println("URL non autorisée : " + uri);
                return;
            }

            hostServices.showDocument(uri.toString());

        } catch (IllegalArgumentException e) {
            System.err.println(
                    "URL d'actualité invalide : " + e.getMessage()
            );
        }
    }
}
