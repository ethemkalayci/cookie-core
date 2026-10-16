
package be.cookies.studio.core;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.Objects;

public class CookieCoreApp extends Application {

    @Override
    public void start(Stage stage) {
        var logoUrl = getClass().getResource("/images/logo/logo-core.png");

        if (logoUrl == null) {
            throw new IllegalStateException("Logo introuvable");
        }
        stage.getIcons().add(new Image(logoUrl.toExternalForm()));

        // Récupération de la vidéo d'introduction
        URL videoUrl = getClass().getResource(
                "/videos/CoreIntro.mp4"
        );

        // Si la vidéo est absente, ouvrir directement le launcher
        if (videoUrl == null) {
            afficherInterfacePrincipale(stage);
            return;
        }

        Stage introStage = new Stage();
        introStage.initStyle(StageStyle.UNDECORATED);
        introStage.setResizable(false);
        introStage.getIcons().add(new Image(logoUrl.toExternalForm()));

        // Création du lecteur vidéo
        Media media = new Media(videoUrl.toExternalForm());
        MediaPlayer player = new MediaPlayer(media);
        MediaView mediaView = new MediaView(player);

        // Conteneur de la vidéo
        StackPane root = new StackPane(mediaView);
        root.setStyle("-fx-background-color: #13171F;");

        Scene introScene = new Scene(root, 900, 600);

        // Adaptation de la vidéo aux dimensions de la fenêtre
        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(
                introScene.widthProperty()
        );
        mediaView.fitHeightProperty().bind(
                introScene.heightProperty()
        );

        // Action commune pour quitter l'introduction
        Runnable terminerIntroduction = () -> {
            player.dispose();
            afficherInterfacePrincipale(stage);
            introStage.close();
        };

        // Lorsque la vidéo se termine
        player.setOnEndOfMedia(terminerIntroduction);

        // Si la lecture rencontre une erreur
        player.setOnError(() -> {
            System.err.println(
                    "Erreur vidéo : " + player.getError()
            );
            terminerIntroduction.run();
        });

        // Affichage de l'introduction
        introStage.setScene(introScene);
        introStage.show();

        // Démarrage de la lecture
        player.play();
    }

    private void afficherInterfacePrincipale(Stage stage) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(
                            getClass().getResource(
                                    "/view/shell-view.fxml"
                            ),
                            "Le fichier /view/shell-view.fxml est introuvable"
                    )
            );

            Parent root = loader.load();

            Scene scene = new Scene(root, 900, 600);

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Impossible de charger l'interface principale",
                    e
            );
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
