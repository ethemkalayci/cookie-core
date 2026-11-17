package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.OfficialInstance;
import be.cookies.studio.core.service.OfficialInstanceService;
import be.cookies.studio.core.service.ModpackDownloadService;
import be.cookies.studio.core.service.PrismLauncherService;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceController {
    private final boolean backgroundLoading;
    public InstanceController() { this(true); }
    InstanceController(boolean enabled) { backgroundLoading = enabled; }
    private Runnable onLaunched = () -> {};
    public void setOnLaunched(Runnable action) { onLaunched = action; }
    private java.util.function.Consumer<Runnable> accountRequest;
    public void setAccountRequest(java.util.function.Consumer<Runnable> request) { accountRequest = request; }



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
    @FXML private void openPersonalLibrary(){openLibrary(null,null);}
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
            var manage=new Button("Gérer l’instance");manage.setOnAction(ev->{artworkConsumer.accept(local.image());openLibrary(local.id(),null);});
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

    private int selectedInstanceIndex = 0;

    @FXML
    private StackPane bannerPane;




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
        instanceSelector.setConverter(new javafx.util.StringConverter<>(){
            public String toString(OfficialInstance value){return value==null?"Choisir une instance":value.getName();}
            public OfficialInstance fromString(String text){return null;}
        });
        instanceSelector.setOnAction(e->{int index=instanceSelector.getSelectionModel().getSelectedIndex();if(index>=0 && index!=selectedInstanceIndex){selectedInstanceIndex=index;showSelectedInstance();}});

        previousInstanceButton.setOnAction(event -> navigate(-1));
        nextInstanceButton.setOnAction(event -> navigate(1));
        instancePlayButton.setOnAction(event -> handleInstanceAction());

        // Actions indisponibles jusqu’à la réception du catalogue.
        instancePlayButton.setDisable(true);
        addInstanceButton.setOnAction(e->createPersonalInstance());
        retryCatalogButton.setOnAction(e->loadOfficialInstances());

        instanceNameLabel.setText("Chargement...");
        instanceVersionLabel.setText("");


        updateNavigationButtons();
        if (backgroundLoading) {loadOfficialInstances();refreshLocal();}
    }

    /** Le clic installe une nouvelle instance, ou lance une instance déjà importée. */
    private void handleInstanceAction() {
        if (downloadInProgress || officialInstances.isEmpty()) {
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
        instancePlayButton.setDisable((state==null&&!instance.isDownloadAvailable()));

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

            instanceSelector.getItems().setAll(officialInstances);
            instanceSelector.getSelectionModel().selectFirst();
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


            instancePlayButton.setDisable(true);

            updateNavigationButtons();
        });

        Thread thread = new Thread(task, "official-instances-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private String artworkPath;
    private java.util.function.Consumer<String> artworkConsumer=path -> {};
    @FXML private javafx.scene.control.ComboBox<OfficialInstance> instanceSelector;
    public void setArtworkConsumer(java.util.function.Consumer<String> consumer){artworkConsumer=consumer;consumer.accept(artworkPath);}
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

        artworkPath=instance.getImagePath();artworkConsumer.accept(artworkPath);
        if(instanceSelector.getSelectionModel().getSelectedIndex()!=selectedInstanceIndex)instanceSelector.getSelectionModel().select(selectedInstanceIndex);

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

    private void navigate(int direction) {
        int target=selectedInstanceIndex+direction;
        if(target<0 || target>=officialInstances.size())return;
        selectedInstanceIndex=target;showSelectedInstance();
    }

    private void updateNavigationButtons() {
        boolean canNavigate =
                !officialInstances.isEmpty();

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


        instancePlayButton.setDisable(true);

        updateNavigationButtons();
    }

}
