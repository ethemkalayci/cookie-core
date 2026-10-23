package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.NewsItem;
import be.cookies.studio.core.service.NewsService;
import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;
import org.girod.javafx.svgimage.SVGLoader;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import be.cookies.studio.core.service.NewsLoadResult;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class HomeController {
    @FXML
    private Button newsButton;
    @FXML
    private TilePane newsGrid;
    @FXML
    private Label newsStatusLabel;

    private HostServices hostServices;

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @FXML
    private void initialize() {
        newsButton.setGraphic(loadIcon("notification.svg"));
        loadNews();
        initializeNewsCardClips();
    }


    private void showNewsStatus(String message) {
        newsStatusLabel.setText(message);
        newsStatusLabel.setVisible(true);
        newsStatusLabel.setManaged(true);
    }

    private void hideNewsStatus() {
        newsStatusLabel.setVisible(false);
        newsStatusLabel.setManaged(false);
    }

    private void loadNews() {

        showNewsStatus("Chargement des actualités…");

        NewsService newsService = new NewsService();

        Task<NewsLoadResult> task = new Task<>() {

            @Override
            protected NewsLoadResult call() throws Exception {
                return newsService.loadNews();
            }
        };


        task.setOnSucceeded(event -> {

            NewsLoadResult result = task.getValue();

            displayNews(result.news());

            if (result.news().isEmpty()) {

                showNewsStatus("Aucune actualité disponible.");

            } else if (result.fromCache()) {

                showNewsStatus(
                        "Mode hors ligne : dernières actualités enregistrées."
                );

            } else {

                hideNewsStatus();
            }
        });


        task.setOnFailed(event -> {

            newsGrid.getChildren().clear();

            showNewsStatus(
                    "Impossible de récupérer les actualités."
            );

            System.err.println(
                    "Échec du chargement des actualités :"
            );

            task.getException().printStackTrace();
        });

        Thread thread = new Thread(
                task,
                "cookie-core-news-loader"
        );

        thread.setDaemon(true);
        thread.start();
    }


    private void displayNews(List<NewsItem> news) {

        newsGrid.getChildren().clear();

        URL ressource = getClass().getResource("/view/newsCard.fxml");

        if (ressource == null) {
            System.err.println("newsCard.fxml introuvable !");
            return;
        }

        for (NewsItem item : news) {

            FXMLLoader loader = new FXMLLoader(ressource);

            try {
                VBox card = loader.load();

                NewsCardController controller = loader.getController();

                controller.setHostServices(hostServices);
                controller.setNewsItem(item);

                applyRoundedClip(card);

                newsGrid.getChildren().add(card);
            } catch (IOException e) {
                System.err.println(
                        "Erreur lors de l'affichage : " + item.getTitre()
                );
                e.printStackTrace();
            }
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