package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.OfficialInstance;
import be.cookies.studio.core.service.OfficialInstanceService;
import be.cookies.studio.core.service.ModpackDownloadService;
import be.cookies.studio.core.service.PrismLauncherService;

import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class InstanceController {
    private final boolean backgroundLoading;
    public InstanceController() { this(true); }
    InstanceController(boolean enabled) { backgroundLoading = enabled; }
    private Runnable onLaunched = () -> {};
    public void setOnLaunched(Runnable action) { onLaunched = action; }
    private java.util.function.Consumer<Runnable> accountRequest;
    public void setAccountRequest(java.util.function.Consumer<Runnable> request) { accountRequest = request; }

    private static final Duration SLIDE_DURATION = Duration.millis(280);
    private static final Duration IMAGE_TIMEOUT = Duration.seconds(15);

    private static final Object IMAGE_REQUEST_KEY = new Object();

    private final OfficialInstanceService officialInstanceService =
            new OfficialInstanceService();

    private final ModpackDownloadService modpackDownloadService =
            new ModpackDownloadService();

    private final PrismLauncherService prismLauncherService = new PrismLauncherService();

    private boolean downloadInProgress = false;
    private final be.cookies.studio.core.service.InstanceManager manager=new be.cookies.studio.core.service.InstanceManager();
    private final Map<Integer,be.cookies.studio.core.service.InstanceManager.Status> localStates=new HashMap<>();
    @FXML private Label catalogStatus,localStatus;
    @FXML private javafx.scene.layout.TilePane personalInstancesGrid;
    @FXML private VBox inlineContentsHost;
    private be.cookies.studio.core.ui.InstanceContentsPane inlineContents;
    @FXML private Button retryCatalogButton;
    @FXML private javafx.scene.layout.FlowPane catalogNotice;
    @FXML private javafx.scene.control.ProgressBar instanceProgress;
    @FXML private Label operationStatus;
    @FXML private void openLibrary(){openLibrary(null,null);}
    @FXML private void manageSelected(){openLibrary(selectedId(),null);}
    @FXML private void manageMods(){showContents(be.cookies.studio.core.service.InstanceContentService.Kind.MODS);}
    @FXML private void manageResourcePacks(){showContents(be.cookies.studio.core.service.InstanceContentService.Kind.RESOURCE_PACKS);}
    @FXML private void manageShaders(){showContents(be.cookies.studio.core.service.InstanceContentService.Kind.SHADERS);}
    private void showContents(be.cookies.studio.core.service.InstanceContentService.Kind kind){inlineContents.show(selectedId(),kind);markContentTab();}
    private void markContentTab(){
        var active=inlineContents.selectedKind();
        for(var node:bannerPane.getScene()==null?java.util.Set.<javafx.scene.Node>of():bannerPane.getScene().getRoot().lookupAll(".content-tabs .button")){
            if(node instanceof Button button){String label=active==null?"":switch(active){case MODS->"Mods";case RESOURCE_PACKS->"Resource Packs";case SHADERS->"Shaders";};button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),label.equals(button.getText()));}
        }
    }
    private String selectedId(){return officialInstances.isEmpty()?null:"cookiecore-official-"+officialInstances.get(selectedInstanceIndex).getId();}
    private void openLibrary(String id,be.cookies.studio.core.service.InstanceContentService.Kind kind){
        InstanceLibraryController.open(bannerPane.getScene().getWindow(),id,List.copyOf(officialInstances),accountRequest,this::refreshLocal,kind);
    }
    private void refreshLocal(){
        if(!backgroundLoading)return;
        List<OfficialInstance> targets=List.copyOf(officialInstances);
        Task<List<be.cookies.studio.core.model.LocalInstance>> task=new Task<>(){
            protected List<be.cookies.studio.core.model.LocalInstance> call() throws Exception{return manager.list();}
        };
        task.setOnSucceeded(e->{
            var values=task.getValue();renderLocalInstances(values);
            Task<Map<Integer,be.cookies.studio.core.service.InstanceManager.Status>> states=new Task<>(){
                protected Map<Integer,be.cookies.studio.core.service.InstanceManager.Status> call(){var result=new HashMap<Integer,be.cookies.studio.core.service.InstanceManager.Status>();for(var local:values)if(local.official()!=null)result.put(local.official().getId(),manager.status(local,targets.stream().filter(t->t.getId()==local.official().getId()).findFirst().orElse(null)));return result;}
            };
            states.setOnSucceeded(ev->{localStates.clear();localStates.putAll(states.getValue());updateDownloadButton();inlineContents.selectInstance(selectedId());markContentTab();});new Thread(states,"local-instance-states").start();
        });
        task.setOnFailed(e->{if(!localStatus.textProperty().isBound())localStatus.setText("Inventaire indisponible : "+task.getException().getMessage());});new Thread(task,"local-instance-list").start();
    }

    void renderLocalInstances(List<be.cookies.studio.core.model.LocalInstance> values){
        personalInstancesGrid.getChildren().clear();
        long count=values.stream().filter(i->i.official()==null).count();
        if(!localStatus.textProperty().isBound())localStatus.setText(count==0?"Aucune instance personnelle. Crée une instance ou importe un export Prism.":count+" instance(s) personnelle(s)");
        for(var local:values){
            if(local.official()!=null)continue;
            var name=new Label(local.name());name.getStyleClass().add("card-title");name.setWrapText(true);
            var version=new Label("Minecraft "+local.minecraft()+" · "+local.loader());version.setWrapText(true);
            var manage=new Button("Gérer l’instance");manage.setOnAction(ev->openLibrary(local.id(),null));
            var card=new VBox(12,name,version,manage);card.setPrefWidth(280);card.getStyleClass().add("surface-card");
            personalInstancesGrid.getChildren().add(card);
        }
    }

    private void createPersonalInstance(){
        if(addInstanceButton.isDisabled())return;
        addInstanceButton.setDisable(true);localStatus.setText("Chargement des versions Minecraft…");
        Task<List<String>> versions=new Task<>(){protected List<String> call()throws Exception{return new be.cookies.studio.core.service.PrismVersions().minecraft();}};
        versions.setOnFailed(e->{addInstanceButton.setDisable(false);localStatus.setText("Versions indisponibles : "+versions.getException().getMessage());});
        versions.setOnSucceeded(e->{
            var dialog=new InstanceCreationDialog(bannerPane.getScene().getWindow(),versions.getValue(),new be.cookies.studio.core.service.PrismVersions()::loaders);
            var choice=dialog.showAndWait();
            if(choice.isEmpty()){addInstanceButton.setDisable(false);refreshLocal();return;}
            var selected=choice.get();
            Task<be.cookies.studio.core.model.LocalInstance> creation=new Task<>(){
                protected be.cookies.studio.core.model.LocalInstance call()throws Exception{
                    updateMessage("Création de "+selected.name()+"…");
                    return manager.create(selected.name(),selected.minecraft(),selected.loader(),selected.version(),(step,done,total)->updateMessage(selected.name()+" — "+step));
                }
            };
            localStatus.textProperty().bind(creation.messageProperty());
            creation.setOnSucceeded(done->{localStatus.textProperty().unbind();addInstanceButton.setDisable(false);refreshLocal();});
            creation.setOnFailed(failed->{localStatus.textProperty().unbind();addInstanceButton.setDisable(false);localStatus.setText("Création impossible : "+creation.getException().getMessage());});
            new Thread(creation,"personal-instance-create").start();
        });
        new Thread(versions,"personal-instance-versions").start();
    }

    private final List<OfficialInstance> officialInstances =
            new ArrayList<>();

    // Une image partagée par URL.
    private final Map<String, Image> imageCache = new HashMap<>();

    // Un chargement partagé par URL.
    // Un résultat null représente une image indisponible.
    private final Map<String, CompletableFuture<Image>> imageLoads =
            new HashMap<>();

    private int selectedInstanceIndex = 0;
    private boolean animating = false;
    private boolean waitingForImage = false;

    @FXML
    private StackPane bannerPane;

    @FXML
    private Pane slidesContainer;

    @FXML
    private StackPane currentSlide;

    @FXML
    private ImageView instanceBannerImage;

    @FXML
    private Label instanceNameLabel;

    @FXML
    private Label instanceVersionLabel;

    @FXML
    private Button previousInstanceButton;

    @FXML
    private Button nextInstanceButton;

    @FXML
    private Button instancePlayButton;

    @FXML
    private Button addInstanceButton;

    @FXML
    private void initialize() {
        inlineContents=new be.cookies.studio.core.ui.InstanceContentsPane(manager);inlineContentsHost.getChildren().setAll(inlineContents);be.cookies.studio.core.ui.ViewSupport.rounded(instanceProgress,8);
        be.cookies.studio.core.ui.ViewSupport.hero(bannerPane);
        be.cookies.studio.core.ui.ViewSupport.cardHover(instancePlayButton);
        be.cookies.studio.core.ui.ViewSupport.cardHover(previousInstanceButton);
        be.cookies.studio.core.ui.ViewSupport.cardHover(nextInstanceButton);
        slidesContainer.prefWidthProperty()
                .bind(bannerPane.widthProperty());

        slidesContainer.prefHeightProperty()
                .bind(bannerPane.heightProperty());

        // Masquer les parties des diapositives situées hors du carrousel.
        Rectangle clip = new Rectangle();

        clip.widthProperty().bind(slidesContainer.widthProperty());
        clip.heightProperty().bind(slidesContainer.heightProperty());

        slidesContainer.setClip(clip);

        configureSlide(currentSlide, instanceBannerImage);

        previousInstanceButton.setOnAction(event -> navigate(-1));
        nextInstanceButton.setOnAction(event -> navigate(1));
        instancePlayButton.setOnAction(event -> handleInstanceAction());

        // Fonctionnalités métier non encore développées.
        instancePlayButton.setDisable(true);
        addInstanceButton.setOnAction(e->createPersonalInstance());
        retryCatalogButton.setOnAction(e->loadOfficialInstances());

        instanceNameLabel.setText("Chargement...");
        instanceVersionLabel.setText("");
        clearImage(instanceBannerImage);

        updateNavigationButtons();
        if (backgroundLoading) {loadOfficialInstances();refreshLocal();}
    }

    /** Le clic installe une nouvelle instance, ou lance une instance déjà importée. */
    private void handleInstanceAction() {
        if (downloadInProgress || officialInstances.isEmpty() || animating || waitingForImage) {
            return;
        }
        OfficialInstance instance = officialInstances.get(selectedInstanceIndex);
        var local=localStates.get(instance.getId());
        if(local!=null&&local.state()!=be.cookies.studio.core.service.InstanceManager.State.INSTALLED){openLibrary(selectedId(),null);return;}
        downloadInProgress = true;
        updateDownloadButton();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (prismLauncherService.installedInstanceId(instance).isPresent()) {
                    if (new be.cookies.studio.core.service.PrismAccountService(prismLauncherService.getDataDirectory())
                            .readSelected().isEmpty()) return "CONNECT";
                    prismLauncherService.launchInstance(instance);
                    return "LAUNCHED";
                }
                if (!instance.isDownloadAvailable()) {
                    throw new java.io.IOException("Ce modpack n'est pas disponible.");
                }
                updateMessage("Préparation du moteur Prism…");
                prismLauncherService.prepareRuntime();
                java.nio.file.Path archive = modpackDownloadService.downloadModpack(instance,(step,done,total)->{updateMessage(step);updateProgress(total>0?done:-1,total>0?total:1);});
                updateMessage("Installation et vérification de "+instance.getName()+"…");updateProgress(-1,1);
                try {
                    return prismLauncherService.installModpack(instance, archive);
                } finally {
                    try {
                        java.nio.file.Files.deleteIfExists(archive);
                    } catch (java.io.IOException cleanupError) {
                        System.err.println("Archive temporaire à nettoyer : " + archive);
                    }
                }
            }
        };

        instanceProgress.setVisible(true);instanceProgress.setManaged(true);instanceProgress.progressProperty().bind(task.progressProperty());operationStatus.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            instanceProgress.progressProperty().unbind();instanceProgress.setVisible(false);instanceProgress.setManaged(false);operationStatus.textProperty().unbind();operationStatus.setText("Opération terminée.");
            downloadInProgress = false;
            refreshLocal();updateDownloadButton();
            if ("CONNECT".equals(task.getValue())) {
                if (accountRequest != null) accountRequest.accept(this::handleInstanceAction);
                return;
            }
            Alert message = new Alert(Alert.AlertType.INFORMATION);
            if ("LAUNCHED".equals(task.getValue())) {
                onLaunched.run();
                return; // Prism renouvelle la session et affiche les erreurs.
            } else {
                message.setTitle("Instance installée");
                message.setHeaderText(instance.getName());
                message.setContentText("Instance Prism : " + task.getValue()
                        + "\nLe paquet complet a été installé sans assistant graphique. "
                        + "Au premier lancement, Prism pourra télécharger Minecraft/Forge "
                        + "et demander la connexion à ton compte Microsoft.");
            }
            be.cookies.studio.core.service.ThemeService.attach(message);
            message.show();
        });
        task.setOnFailed(event -> {
            instanceProgress.progressProperty().unbind();instanceProgress.setVisible(false);instanceProgress.setManaged(false);operationStatus.textProperty().unbind();operationStatus.setText("Opération interrompue : consulte le message d’erreur.");
            downloadInProgress = false;
            refreshLocal();updateDownloadButton();
            Throwable cause = task.getException();
            if (cause != null) cause.printStackTrace();
            Alert message = new Alert(Alert.AlertType.ERROR);
            message.setTitle("Opération Prism impossible");
            message.setHeaderText(instance.getName());
            message.setContentText(cause == null ? "Erreur inconnue" : cause.getMessage());
            be.cookies.studio.core.service.ThemeService.attach(message);
            message.show();
        });
        Thread worker = new Thread(task, "cookiecore-prism-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void updateDownloadButton() {
        if (downloadInProgress) {
            instancePlayButton.setText("Opération en cours...");
            instancePlayButton.setDisable(true);
            return;
        }
        if (officialInstances.isEmpty()) {
            instancePlayButton.setText("Indisponible");
            instancePlayButton.setDisable(true);
            return;
        }
        OfficialInstance instance = officialInstances.get(selectedInstanceIndex);
        var state=localStates.get(instance.getId());
        instancePlayButton.setText(state==null?(instance.isDownloadAvailable()?"Installer":"Indisponible"):switch(state.state()){case INSTALLED->"Jouer";case UPDATE_AVAILABLE->"Mettre à jour";case INCOMPLETE,MISSING,ERROR->"Récupérer / Gérer";});
        instancePlayButton.setDisable((state==null&&!instance.isDownloadAvailable())||animating||waitingForImage);

    }

    private void configureSlide(StackPane slide, ImageView imageView) {
        slide.prefWidthProperty().bind(slidesContainer.widthProperty());
        slide.prefHeightProperty().bind(slidesContainer.heightProperty());

        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        imageView.fitWidthProperty().bind(slide.widthProperty());
        imageView.fitHeightProperty().bind(slide.heightProperty());
    }

    private void loadOfficialInstances() {
        retryCatalogButton.setDisable(true);
        Task<OfficialInstanceService.Catalog> task = new Task<>() {
            @Override
            protected OfficialInstanceService.Catalog call() throws Exception {
                return officialInstanceService.loadCatalog();
            }
        };

        task.setOnSucceeded(event -> {
            officialInstances.clear();
            officialInstances.addAll(task.getValue().instances());
            catalogStatus.setText(task.getValue().message());catalogNotice.setVisible(task.getValue().offline());catalogNotice.setManaged(task.getValue().offline());retryCatalogButton.setDisable(false);refreshLocal();

            if (officialInstances.isEmpty()) {
                showEmptyCatalog();
                return;
            }

            // Instance ayant l'identifiant le plus élevé en premier.
            officialInstances.sort(
                    Comparator.comparingInt(OfficialInstance::getId)
                            .reversed()
            );

            selectedInstanceIndex = 0;

            preloadImages();
            showSelectedInstance();
        });

        task.setOnFailed(event -> {
            System.err.println(
                    "Impossible de charger les instances officielles."
            );

            if (task.getException() != null) {
                task.getException().printStackTrace();
            }

            retryCatalogButton.setDisable(false);catalogNotice.setVisible(true);catalogNotice.setManaged(true);catalogStatus.setText(task.getException().getMessage());
            instanceNameLabel.setText("Catalogue indisponible");

            instanceVersionLabel.setText(
                    "Impossible de récupérer les instances officielles."
            );

            clearImage(instanceBannerImage);
            instancePlayButton.setDisable(true);

            updateNavigationButtons();
        });

        Thread thread = new Thread(task, "official-instances-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void preloadImages() {
        for (OfficialInstance instance : officialInstances) {
            requestImage(instance.getImagePath());
        }
    }

    /**
     * Cette méthode et les accès aux caches s'effectuent sur le thread JavaFX.
     * Le téléchargement lui-même est réalisé en arrière-plan par Image.
     */
    private CompletableFuture<Image> requestImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Image> existing = imageLoads.get(imagePath);

        if (existing != null) {
            return existing;
        }

        CompletableFuture<Image> result = new CompletableFuture<>();
        imageLoads.put(imagePath, result);

        if(imagePath.startsWith("https://")){
            new be.cookies.studio.core.service.NewsImageCacheService().resolve(imagePath).whenComplete((path,error)->Platform.runLater(()->{
                if(error!=null||path==null)requestImage(getClass().getResource("/images/banners/mc.jpg").toExternalForm()).thenAccept(result::complete);else requestImage(path.toUri().toString()).thenAccept(result::complete);
            }));return result;
        }
        final Image image;

        try {
            image = new Image(imagePath, true);
        } catch (IllegalArgumentException exception) {
            System.err.println("URL d'image invalide : " + imagePath);
            exception.printStackTrace();

            result.complete(null);
            return result;
        }

        imageCache.put(imagePath, image);

        PauseTransition timeout = new PauseTransition(IMAGE_TIMEOUT);

        class LoadObserver implements InvalidationListener {

            @Override
            public void invalidated(javafx.beans.Observable observable) {
                check();
            }

            private void check() {
                if (result.isDone()) {
                    return;
                }

                if (image.isError()) {
                    fail(
                            "Erreur de chargement de l'image : " + imagePath,
                            image.getException()
                    );
                    return;
                }

                if (image.getProgress() >= 1.0) {
                    if (image.getWidth() > 0 && image.getHeight() > 0) {
                        detach();
                        result.complete(image);
                    } else {
                        fail(
                                "Image sans dimensions valides : " + imagePath,
                                null
                        );
                    }
                }
            }

            private void fail(String message, Throwable exception) {
                if (result.isDone()) {
                    return;
                }

                detach();
                imageCache.remove(imagePath, image);

                System.err.println(message);

                if (exception != null) {
                    exception.printStackTrace();
                }

                // Mémoriser l'échec et débloquer les éventuelles attentes.
                result.complete(null);
                image.cancel();
            }

            private void detach() {
                image.progressProperty().removeListener(this);
                image.errorProperty().removeListener(this);

                timeout.stop();
                timeout.setOnFinished(null);
            }
        }

        LoadObserver observer = new LoadObserver();

        image.progressProperty().addListener(observer);
        image.errorProperty().addListener(observer);

        timeout.setOnFinished(event ->
                observer.fail(
                        "Délai de chargement dépassé : " + imagePath,
                        null
                )
        );

        timeout.play();

        // Vérifier également un chargement déjà terminé.
        observer.check();

        return result;
    }

    private void showSelectedInstance() {
        if (officialInstances.isEmpty()) {
            showEmptyCatalog();
            return;
        }

        OfficialInstance instance =
                officialInstances.get(selectedInstanceIndex);

        instanceNameLabel.setText(instance.getName());
        inlineContents.selectInstance(selectedId());markContentTab();
        instanceVersionLabel.setText(formatVersion(instance));

        loadImage(
                instanceBannerImage,
                currentSlide,
                instance.getImagePath()
        );

        updateDownloadButton();

        updateNavigationButtons();
    }

    private String formatVersion(OfficialInstance instance) {
        String version =
                "Minecraft " + instance.getMinecraftVersion()
                        + " • " + instance.getModLoader();

        String loaderVersion = instance.getLoaderVersion();

        if (loaderVersion != null && !loaderVersion.isBlank()) {
            version += " " + loaderVersion;
        }

        return version;
    }

    private SlideContent createSlide(OfficialInstance instance) {
        StackPane slide = new StackPane();

        ImageView imageView = new ImageView();
        imageView.setMouseTransparent(true);

        Region gradient = new Region();
        gradient.getStyleClass().add("banner-gradient");
        gradient.setMouseTransparent(true);

        Region bottomFade = new Region();
        bottomFade.getStyleClass().add("banner-bottom-fade");
        bottomFade.setPrefHeight(200);
        bottomFade.setMaxHeight(200);
        bottomFade.setMouseTransparent(true);

        StackPane.setAlignment(bottomFade, Pos.BOTTOM_CENTER);

        Label nameLabel = new Label(instance.getName());
        nameLabel.getStyleClass().add("instance-title");
        nameLabel.setWrapText(true); nameLabel.setMinHeight(Region.USE_PREF_SIZE);

        Label versionLabel = new Label(formatVersion(instance));
        versionLabel.getStyleClass().add("instance-version");
        versionLabel.setWrapText(true); versionLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox information = new VBox(12);
        information.setAlignment(Pos.CENTER_LEFT);
        information.setMouseTransparent(true);
        information.getStyleClass().add("hero-copy");
        information.setMaxWidth(600); information.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setMargin(information, new Insets(24, 24, 64, 24));

        information.getChildren().addAll(nameLabel, versionLabel);

        StackPane.setAlignment(information, Pos.CENTER_LEFT);

        slide.getChildren().addAll(
                imageView,
                gradient,
                bottomFade,
                information
        );

        configureSlide(slide, imageView);
        loadImage(imageView, slide, instance.getImagePath());

        return new SlideContent(
                slide,
                imageView,
                nameLabel,
                versionLabel
        );
    }

    // -1 : instance précédente ; +1 : instance suivante.
    private void navigate(int direction) {
        if (animating || waitingForImage || officialInstances.isEmpty()) {
            return;
        }

        if (direction != -1 && direction != 1) {
            return;
        }

        int targetIndex = selectedInstanceIndex + direction;

        if (targetIndex < 0 || targetIndex >= officialInstances.size()) {
            return;
        }

        if (slidesContainer.getWidth() <= 0) {
            return;
        }

        OfficialInstance targetInstance =
                officialInstances.get(targetIndex);

        waitingForImage = true;
        updateNavigationButtons();
        updateDownloadButton();

        requestImage(targetInstance.getImagePath()).thenAccept(image ->
                Platform.runLater(() -> {
                    waitingForImage = false;

                    // Le résultat est disponible : image prête ou secours null.
                    startSlideTransition(targetIndex, direction);
                })
        );
    }

    private void startSlideTransition(int targetIndex, int direction) {
        // Reprendre la largeur actuelle après l'attente du téléchargement.
        double width = slidesContainer.getWidth();

        if (width <= 0) {
            updateNavigationButtons();
            updateDownloadButton();
            return;
        }

        OfficialInstance targetInstance =
                officialInstances.get(targetIndex);

        SlideContent nextContent = createSlide(targetInstance);
        StackPane nextSlide = nextContent.root();

        animating = true;
        updateNavigationButtons();
        updateDownloadButton();

        // Position de départ hors écran.
        nextSlide.setLayoutX(direction * width);
        slidesContainer.getChildren().add(nextSlide);

        TranslateTransition outgoing =
                new TranslateTransition(SLIDE_DURATION, currentSlide);

        outgoing.setFromX(0);
        outgoing.setToX(-direction * width);
        outgoing.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition incoming =
                new TranslateTransition(SLIDE_DURATION, nextSlide);

        incoming.setFromX(0);
        incoming.setToX(-direction * width);
        incoming.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition transition =
                new ParallelTransition(outgoing, incoming);

        transition.setOnFinished(event -> {
            StackPane oldSlide = currentSlide;
            ImageView oldImageView = instanceBannerImage;

            slidesContainer.getChildren().remove(oldSlide);

            // Libérer les liaisons de l'ancienne diapositive.
            oldSlide.prefWidthProperty().unbind();
            oldSlide.prefHeightProperty().unbind();

            oldImageView.fitWidthProperty().unbind();
            oldImageView.fitHeightProperty().unbind();

            clearImage(oldImageView);

            nextSlide.setTranslateX(0);
            nextSlide.setLayoutX(0);

            currentSlide = nextSlide;
            instanceBannerImage = nextContent.imageView();
            instanceNameLabel = nextContent.nameLabel();
            instanceVersionLabel = nextContent.versionLabel();

            selectedInstanceIndex = targetIndex;
            inlineContents.selectInstance(selectedId());markContentTab();

            animating = false;
            updateNavigationButtons();
            updateDownloadButton();
        });

        transition.play();
    }

    private void loadImage(
            ImageView imageView,
            StackPane slide,
            String imagePath
    ) {
        CompletableFuture<Image> request = requestImage(imagePath);

        clearImage(imageView);
        imageView.getProperties().put(IMAGE_REQUEST_KEY, request);

        if (request.isDone()) {
            // Application immédiate avant le début de l'animation.
            applyImage(imageView, slide, request.getNow(null));
            return;
        }

        request.thenAccept(image ->
                Platform.runLater(() -> {
                    // Une réponse ancienne ne doit pas remplacer
                    // une image demandée plus récemment.
                    if (imageView.getProperties().get(IMAGE_REQUEST_KEY)
                            != request) {
                        return;
                    }

                    applyImage(imageView, slide, image);
                })
        );
    }

    private void clearImage(ImageView imageView) {
        imageView.getProperties().remove(IMAGE_REQUEST_KEY);
        imageView.viewportProperty().unbind();
        imageView.setImage(null);
        imageView.setViewport(null);
    }

    private void applyImage(
            ImageView imageView,
            StackPane slide,
            Image image
    ) {
        imageView.viewportProperty().unbind();
        imageView.setImage(null);
        imageView.setViewport(null);

        if (image == null) {
            // Les dégradés, le nom et la version restent visibles.
            return;
        }

        imageView.setImage(image);

        imageView.viewportProperty().bind(
                Bindings.createObjectBinding(
                        () -> calculateViewport(
                                image,
                                slide.getWidth(),
                                slide.getHeight()
                        ),
                        image.widthProperty(),
                        image.heightProperty(),
                        slide.widthProperty(),
                        slide.heightProperty()
                )
        );
    }

    private Rectangle2D calculateViewport(
            Image image,
            double paneWidth,
            double paneHeight
    ) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0
                || paneWidth <= 0 || paneHeight <= 0) {
            return null;
        }

        double imageRatio = imageWidth / imageHeight;
        double paneRatio = paneWidth / paneHeight;

        if (imageRatio > paneRatio) {
            // Image plus large : recadrage horizontal centré.
            double cropWidth = imageHeight * paneRatio;

            return new Rectangle2D(
                    (imageWidth - cropWidth) / 2,
                    0,
                    cropWidth,
                    imageHeight
            );
        }

        // Image plus haute : recadrage vertical centré.
        double cropHeight = imageWidth / paneRatio;

        return new Rectangle2D(
                0,
                (imageHeight - cropHeight) / 2,
                imageWidth,
                cropHeight
        );
    }

    private void updateNavigationButtons() {
        boolean canNavigate =
                !animating
                        && !waitingForImage
                        && !officialInstances.isEmpty();

        previousInstanceButton.setDisable(
                !canNavigate || selectedInstanceIndex == 0
        );

        nextInstanceButton.setDisable(
                !canNavigate
                        || selectedInstanceIndex
                        >= officialInstances.size() - 1
        );
    }

    private void showEmptyCatalog() {
        instanceNameLabel.setText("Aucune instance officielle");

        instanceVersionLabel.setText(
                "Aucune instance disponible pour le moment."
        );

        clearImage(instanceBannerImage);
        instancePlayButton.setDisable(true);

        updateNavigationButtons();
    }

    private record SlideContent(
            StackPane root,
            ImageView imageView,
            Label nameLabel,
            Label versionLabel
    ) {
    }
}




