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
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import be.cookies.studio.core.service.NewsLoadResult;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class HomeController {
    private final boolean backgroundLoading;
    public HomeController() { this(true); }
    HomeController(boolean backgroundLoading) { this.backgroundLoading = backgroundLoading; }
    @FXML private Label welcomeLabel;
    @FXML private javafx.scene.layout.StackPane bannerPane;
    @FXML private Label lastInstanceSubtitle, lastInstanceName, lastInstanceVersions, lastInstanceDate, lastInstanceStatus;
    @FXML private Button lastInstancePlay;
    private be.cookies.studio.core.service.LastInstanceService.Entry lastEntry;
    private Runnable browseInstances = () -> {};
    private java.util.function.Consumer<Runnable> accountRequest;
    private boolean lastInstalled, launching;
    private be.cookies.studio.core.model.LocalInstance localLast;
    private java.time.Instant localLastAt;
    private javafx.scene.image.ImageView lastImage;
    public void setInstanceActions(Runnable browse, java.util.function.Consumer<Runnable> account) {
        browseInstances = browse; accountRequest = account;
    }
    private void loadLastInstance() {
        lastImage = new javafx.scene.image.ImageView();
        lastImage.setManaged(false); lastImage.setMouseTransparent(true);
        lastImage.fitWidthProperty().bind(bannerPane.widthProperty());
        lastImage.fitHeightProperty().bind(bannerPane.heightProperty());
        bannerPane.getChildren().addFirst(lastImage);
        var clip = new Rectangle(); clip.widthProperty().bind(bannerPane.widthProperty());
        clip.heightProperty().bind(bannerPane.heightProperty()); lastImage.setClip(clip);
        if (!backgroundLoading) { showLastInstance(null); return; }
        lastInstancePlay.setDisable(true);
        Task<be.cookies.studio.core.service.LastInstanceService.Entry> task = new Task<>() {
            @Override protected be.cookies.studio.core.service.LastInstanceService.Entry call() throws Exception {
                var manager=new be.cookies.studio.core.service.InstanceManager();
                var saved=manager.last();
                if(saved.isPresent()){
                    localLast=manager.get(saved.get().id());localLastAt=saved.get().at();
                    lastInstalled=manager.status(localLast,null).state()==be.cookies.studio.core.service.InstanceManager.State.INSTALLED;
                    return null;
                }
                var entry = new be.cookies.studio.core.service.LastInstanceService().load().orElse(null);
                lastInstalled = entry != null && new be.cookies.studio.core.service.PrismLauncherService()
                        .installedInstanceId(entry.instance()).isPresent();
                return entry;
            }
        };
        task.setOnSucceeded(e -> {if(localLast!=null)showLocalLast();else showLastInstance(task.getValue()); lastInstancePlay.setDisable(false); });
        task.setOnFailed(e -> {
            showLastInstance(null); lastInstanceStatus.setText("Historique indisponible. Lance une instance pour le recréer.");
            lastInstancePlay.setDisable(false);
        });
        startWorker(task, "home-last-instance");
    }
    void showLastInstance(be.cookies.studio.core.service.LastInstanceService.Entry entry) {
        lastEntry = entry;
        if (entry == null) {
            lastInstanceSubtitle.setText("TON AVENTURE COMMENCE ICI");
            lastInstanceName.setText("Choisis ton aventure");
            lastInstanceVersions.setText("Retrouve ici ta dernière instance après son premier lancement.");
            lastInstanceDate.setText(""); lastInstanceStatus.setText("");
            lastInstancePlay.setText("DÉCOUVRIR LES INSTANCES"); return;
        }
        var i = entry.instance();
        lastInstanceSubtitle.setText("DERNIÈRE INSTANCE LANCÉE"); lastInstanceName.setText(i.getName());
        String loader = i.getModLoader().name() + (i.getLoaderVersion() == null ? "" : " " + i.getLoaderVersion());
        lastInstanceVersions.setText("Minecraft " + i.getMinecraftVersion() + "  •  " + loader + "  •  Pack " + i.getModpackVersion());
        lastInstanceDate.setText("Dernier lancement demandé : " + java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy à HH:mm").withZone(java.time.ZoneId.systemDefault()).format(entry.launchedAt()));
        lastInstanceStatus.setText(lastInstalled ? "Prête à jouer" : "Instance absente ou modifiée — retrouve-la dans Instances.");
        lastInstancePlay.setText(lastInstalled ? "REJOUER" : "VOIR LES INSTANCES");
        showInstanceImage(i.getImagePath(), i);
    }
    private void showLocalLast(){
        lastInstanceSubtitle.setText("DERNIÈRE INSTANCE LANCÉE");lastInstanceName.setText(localLast.name());
        lastInstanceVersions.setText("Minecraft "+localLast.minecraft()+" · "+localLast.loader()+" "+java.util.Objects.toString(localLast.loaderVersion(),"")+" · "+(localLast.official()==null?"Personnelle":"Pack "+localLast.official().getModpackVersion()));
        lastInstanceDate.setText("Dernier lancement demandé : "+java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm").withZone(java.time.ZoneId.systemDefault()).format(localLastAt));
        lastInstanceStatus.setText(lastInstalled?"Prête à jouer":"Emplacement indisponible ou récupération nécessaire — ouvre la bibliothèque locale.");
        lastInstancePlay.setText(lastInstalled?"REJOUER":"VOIR LES INSTANCES");showInstanceImage(localLast.image(), localLast.official());
    }
    private long artworkRequest;
    private void showInstanceImage(String path, be.cookies.studio.core.model.OfficialInstance official) {
        long request = ++artworkRequest;
        showLastImage(path);
        // Personal illustrations stay personal; old official inventory snapshots may have no image.
        if (!backgroundLoading || official == null || (path != null && path.startsWith("file:"))) return;
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                var service = new be.cookies.studio.core.service.OfficialInstanceService();
                var cached = service.cachedCatalog();
                var catalog = cached.isPresent() ? cached.get() : service.loadCatalog();
                return catalog.instances().stream().filter(i -> i.getId() == official.getId())
                        .map(i -> i.getImagePath()).filter(p -> p != null && !p.isBlank())
                        .findFirst().orElse(official.getImagePath());
            }
        };
        task.setOnSucceeded(e -> {
            if (request == artworkRequest && task.getValue() != null && !task.getValue().isBlank())
                showLastImage(task.getValue());
        });
        // Keep the local illustration or fallback if the catalog is unavailable.
        startWorker(task, "home-instance-artwork");
    }
    private long imageRequest;
    private void showLastImage(String path) {
        long request = ++imageRequest;
        var fallback = new javafx.scene.image.Image(getClass().getResource("/images/banners/mc.jpg").toExternalForm());
        displayCroppedImage(fallback);
        if (path == null || path.isBlank()) return;
        if (path.startsWith("https://")) {
            new be.cookies.studio.core.service.NewsImageCacheService().resolve(path).whenComplete((file,error) ->
                javafx.application.Platform.runLater(() -> {
                    if (error == null && file != null && request == imageRequest)
                        loadLocalImage(file.toUri().toString(), request);
                }));
        } else loadLocalImage(path, request);
    }
    private void loadLocalImage(String path, long request) {
        try {
            var picture = new javafx.scene.image.Image(path, true);
            Runnable ready = () -> {
                if (request == imageRequest && !picture.isError() && picture.getProgress() >= 1)
                    displayCroppedImage(picture);
            };
            picture.progressProperty().addListener((o, before, after) -> ready.run());
            ready.run();
        } catch (IllegalArgumentException ignored) { /* Retain the cropped fallback. */ }
    }
    private void displayCroppedImage(javafx.scene.image.Image picture) {
        lastImage.viewportProperty().unbind();
        lastImage.setImage(picture);
        lastImage.viewportProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() -> {
            double w=picture.getWidth(), h=picture.getHeight(), bw=bannerPane.getWidth(), bh=bannerPane.getHeight();
            if(w<=0 || h<=0 || bw<=0 || bh<=0) return null;
            double ratio=bw/bh;
            return w/h>ratio ? new javafx.geometry.Rectangle2D((w-h*ratio)/2,0,h*ratio,h)
                    : new javafx.geometry.Rectangle2D(0,(h-w/ratio)/2,w,w/ratio);
        }, picture.widthProperty(), picture.heightProperty(), bannerPane.widthProperty(), bannerPane.heightProperty()));
    }
    @FXML private void playLastInstance() {
        if (launching) return;
        if ((lastEntry == null && localLast == null) || !lastInstalled) { browseInstances.run(); return; }
        var instance = lastEntry==null?null:lastEntry.instance();
        launching = true; lastInstancePlay.setDisable(true); lastInstanceStatus.setText("Préparation du lancement…");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception {
                var prism = new be.cookies.studio.core.service.PrismLauncherService();
                if (new be.cookies.studio.core.service.PrismAccountService(prism.getDataDirectory()).readSelected().isEmpty()) return false;
                if(localLast!=null)new be.cookies.studio.core.service.InstanceManager().launch(localLast.id());else prism.launchInstance(instance); return true;
            }
        };
        task.setOnSucceeded(e -> {
            launching = false; lastInstancePlay.setDisable(false);
            if (!task.getValue()) {
                lastInstanceStatus.setText("Connecte ton compte Minecraft pour jouer.");
                if (accountRequest != null) accountRequest.accept(this::playLastInstance);
            } else {
                if(localLast!=null){localLastAt=java.time.Instant.now();showLocalLast();}else{lastEntry = new be.cookies.studio.core.service.LastInstanceService.Entry(instance, java.time.Instant.now());showLastInstance(lastEntry);} lastInstanceStatus.setText("Lancement demandé à Prism.");
            }
        });
        task.setOnFailed(e -> {
            launching=false; lastInstancePlay.setDisable(false);
            lastInstanceStatus.setText("Lancement impossible : " + task.getException().getMessage());
        });
        startWorker(task, "home-launch");
    }
    private static void startWorker(Task<?> task, String name) {
        Thread worker = new Thread(task, name); worker.setDaemon(true); worker.start();
    }
    @FXML
    private Button newsButton;
    @FXML private javafx.scene.control.ScrollPane homeScrollPane;
    @FXML private VBox newsSection;
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
        be.cookies.studio.core.ui.ViewSupport.hero(bannerPane);
        be.cookies.studio.core.ui.ViewSupport.cardHover(lastInstancePlay);
        be.cookies.studio.core.ui.ViewSupport.cardHover(newsButton);
        be.cookies.studio.core.ui.ViewSupport.collapseWhenEmpty(lastInstanceDate);
        be.cookies.studio.core.ui.ViewSupport.collapseWhenEmpty(lastInstanceStatus);
        newsButton.setGraphic(loadIcon("notification.svg"));
        newsButton.setOnAction(event -> {
            double available = ((Region) homeScrollPane.getContent()).getHeight() - homeScrollPane.getViewportBounds().getHeight();
            if (available > 0) homeScrollPane.setVvalue(Math.min(1, newsSection.getBoundsInParent().getMinY() / available));
        });
        lastInstanceName.maxWidthProperty().bind(javafx.beans.binding.Bindings.min(552, bannerPane.widthProperty().subtract(96)));
        lastInstanceVersions.maxWidthProperty().bind(javafx.beans.binding.Bindings.min(552, bannerPane.widthProperty().subtract(96)));
        lastInstanceStatus.maxWidthProperty().bind(javafx.beans.binding.Bindings.min(552, bannerPane.widthProperty().subtract(96)));
        Task<String> accountTask = new Task<>() {
            @Override protected String call() throws Exception {
                var prism = new be.cookies.studio.core.service.PrismLauncherService();
                return new be.cookies.studio.core.service.PrismAccountService(prism.getDataDirectory())
                        .readSelected().map(p -> "Bienvenue, " + p.name() + " !").orElse("Bienvenue ! Connecte ton compte Minecraft.");
            }
        };
        welcomeLabel.setText("Bienvenue !");
        accountTask.setOnSucceeded(e -> welcomeLabel.setText(accountTask.getValue()));
        accountTask.setOnFailed(e -> welcomeLabel.setText("Bienvenue ! Vérifie ton compte dans Prism."));
        Thread accountWorker = new Thread(accountTask, "home-account");
        accountWorker.setDaemon(true); if (backgroundLoading) accountWorker.start();
        loadLastInstance();
        if (backgroundLoading) loadNews();
        newsGrid.prefTileWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            double width = Math.max(280, newsGrid.getWidth());
            int columns = Math.max(1, (int) ((width + newsGrid.getHgap()) / (280 + newsGrid.getHgap())));
            return (width - (columns - 1) * newsGrid.getHgap()) / columns;
        }, newsGrid.widthProperty(), newsGrid.hgapProperty()));
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

        // Les publications sont ajoutées en fin de JSON : inverser aussi les
        // ex aequo, puis trier par date (tri stable).
        List<NewsItem> orderedNews = new ArrayList<>(news);
        Collections.reverse(orderedNews);
        orderedNews.sort(Comparator.comparing(NewsItem::getPublicationDate).reversed());

        for (NewsItem item : orderedNews) {

            FXMLLoader loader = new FXMLLoader(ressource);

            try {
                VBox card = loader.load();

                NewsCardController controller = loader.getController();

                controller.setHostServices(hostServices);
                controller.setNewsItem(item);

                card.prefWidthProperty().bind(newsGrid.prefTileWidthProperty());

                newsGrid.getChildren().add(card);
            } catch (IOException e) {
                System.err.println(
                        "Erreur lors de l'affichage : " + item.getTitre()
                );
                e.printStackTrace();
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

            Group icon = new Group(svg); icon.getStyleClass().add("theme-icon"); return icon;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de charger " + fileName,
                    exception
            );
        }
    }
}


