package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** A shared local-instance workspace; file work never runs on the FX thread. */
public final class InstanceLibraryController {
    private final InstanceManager manager;
    private final InstanceContentService contents;
    private final Map<Integer,OfficialInstance> catalog=new HashMap<>();
    private final Stage stage=new Stage();
    private final ListView<LocalInstance> instances=new ListView<>();
    private final ListView<InstanceContentService.Item> items=new ListView<>();
    private final ComboBox<InstanceContentService.Kind> kind=new ComboBox<>(FXCollections.observableArrayList(InstanceContentService.Kind.values()));
    private final Label title=new Label("Bibliothèque locale"),details=new Label(),status=new Label(),contentHelp=new Label();
    private final ProgressBar progress=new ProgressBar();
    private final Button cancel=new Button("Annuler à un point sûr");
    private final VBox body=new VBox(16);
    private boolean busy;
    private volatile String selectedId;
    private volatile InstanceContentService.Kind selectedKind=InstanceContentService.Kind.MODS;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final Consumer<Runnable> account;
    private final Runnable changed;
    private final String initialId;
    private final ImageView contentImage=new ImageView();
    private final Label contentDetails=new Label();
    private final Label locationDetails=new Label();
    private final ImageView instanceImage=new ImageView();
    Scene scene(){return stage.getScene();}
    private Button update;
    private InstanceManager.UpdatePlan pending;
    public static void open(Window owner,String selected,List<OfficialInstance> official,Consumer<Runnable> account,Runnable changed,InstanceContentService.Kind kind){
        var ui=new InstanceLibraryController(new InstanceManager(),selected,official,account,changed);
        ui.title.setText(selected!=null&&selected.startsWith("cookiecore-official-")?"Gérer l’instance officielle":"Mes instances");ui.stage.initOwner(owner);ui.kind.setValue(kind==null?InstanceContentService.Kind.MODS:kind);ui.stage.show();ui.reload(selected);
    }
    InstanceLibraryController(InstanceManager manager,String selected,List<OfficialInstance> official,Consumer<Runnable> account,Runnable changed){
        this.manager=manager;contents=new InstanceContentService(manager);this.account=account;this.changed=changed;initialId=selected;
        for(var i:official)catalog.put(i.getId(),i);
        stage.setTitle("Cookie Core — Gestion des instances");stage.setMinWidth(780);stage.setMinHeight(540);
        title.setText(selected!=null&&selected.startsWith("cookiecore-official-")?"Gérer l’instance officielle":"Mes instances");title.getStyleClass().add("page-title");details.setMinWidth(0);HBox.setHgrow(details,Priority.ALWAYS);instances.setId("localInstances");items.setId("localContents");details.setId("instanceDetails");details.setWrapText(true);status.setWrapText(true);contentHelp.setWrapText(true);contentDetails.setWrapText(true);
        instances.setPrefHeight(110);items.setPrefHeight(260);
        be.cookies.studio.core.ui.ViewSupport.rounded(instances,12);be.cookies.studio.core.ui.ViewSupport.rounded(items,12);be.cookies.studio.core.ui.ViewSupport.rounded(progress,8);
        instanceImage.setFitWidth(160);instanceImage.setFitHeight(90);instanceImage.setPreserveRatio(true);kind.setValue(InstanceContentService.Kind.MODS);
        kind.setConverter(new javafx.util.StringConverter<>(){public String toString(InstanceContentService.Kind k){return k==null?"":switch(k){case MODS->"Mods";case RESOURCE_PACKS->"Resource Packs";case SHADERS->"Shaders";};}public InstanceContentService.Kind fromString(String s){return null;}});
        var create=button("Créer une instance",this::create);var importButton=button("Importer un export Prism",this::importPack);var refresh=button("Actualiser",()->reload(id()));
        boolean officialScope=initialId!=null&&initialId.startsWith("cookiecore-official-");
        create.setVisible(!officialScope);create.setManaged(!officialScope);importButton.setVisible(!officialScope);importButton.setManaged(!officialScope);
        var recover=button("Opérations et récupération",this::recovery);
        var launch=button("Jouer",this::launch);
        var open=button("Ouvrir le dossier",()->run((p)->{openFolder(manager.folder(requireId()));return null;},v->{}));
        update=button("Mettre à jour",this::update);var move=button("Déplacer",this::move);var uninstall=button("Désinstaller",this::uninstall);
        var repair=button("Rétablir le lien Prism",()->run(p->{manager.repairLink(requireId());return null;},v->reload(id())));
        launch.getStyleClass().add("instance-play-button");
        var controls=new FlowPane(10,10,launch,update,open);
        var advancedActions=new FlowPane(10,10,move,uninstall,repair);
        advancedActions.disableProperty().bind(instances.getSelectionModel().selectedItemProperty().isNull());
        locationDetails.setWrapText(true);locationDetails.getStyleClass().add("muted");
        var advanced=new TitledPane("Réglages, déplacement et récupération",new VBox(14,locationDetails,advancedActions,recover));advanced.setExpanded(false);advanced.getStyleClass().add("instance-advanced");controls.disableProperty().bind(instances.getSelectionModel().selectedItemProperty().isNull());
        var add=button("Ajouter des fichiers",this::addContent);var toggle=button("Activer / désactiver le mod",this::toggleContent);var remove=button("Retirer",this::removeContent);
        var openContents=button("Ouvrir ce dossier",()->run(p->{Path folder=contents.directory(requireId(),selectedKind);if(!Files.isDirectory(folder))throw new IOException("Ce dossier n'existe pas encore. Ajoute un contenu pour le créer.");openFolder(folder);return null;},v->{}));
        toggle.disableProperty().bind(kind.valueProperty().isNotEqualTo(InstanceContentService.Kind.MODS));
        contentImage.setFitWidth(64);contentImage.setFitHeight(64);contentImage.setPreserveRatio(true);
        body.getChildren().addAll(title,new FlowPane(10,10,create,importButton,refresh),instances,new HBox(14,instanceImage,details),controls,advanced,new Separator(),kind,contentHelp,items,new HBox(12,contentImage,contentDetails),new FlowPane(10,10,add,toggle,remove,openContents));
        if(officialScope){instances.setVisible(false);instances.setManaged(false);}
        body.setPadding(new Insets(24));var scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        progress.setMaxWidth(Double.MAX_VALUE);progress.setVisible(false);progress.setManaged(false);cancel.setVisible(false);cancel.setManaged(false);
        var footer=new VBox(8,status,progress,cancel);footer.setPadding(new Insets(12,24,16,24));
        BorderPane root=new BorderPane(scroll);root.setBottom(footer);var scene=new Scene(root,1050,760);ThemeService.attach(scene);stage.setScene(scene);
        cancel.setOnAction(e->{cancelled.set(true);cancel.setDisable(true);cancel.setText("Annulation demandée…");});
        stage.setOnCloseRequest(e->{if(busy){e.consume();cancel.setText("Attends la fin ou annule à un point sûr");}});
        instances.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{selectedId=b==null?null:b.id();if(b!=null&&!busy)loadDetails();});
        kind.setOnAction(e->{selectedKind=kind.getValue();if(!busy&&id()!=null)loadDetails();});
        items.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            contentImage.setImage(null);contentDetails.setText(b==null?"":b.file()+"\n"+b.origin()+" · "+b.warning());
            if(b!=null&&b.image()!=null)try{contentImage.setImage(new Image(new ByteArrayInputStream(b.image()),64,64,true,true));}catch(Exception ignored){}
        });
    }
    private Button button(String text,Runnable action){var button=new Button(text);button.setWrapText(true);button.setOnAction(e->{try{action.run();}catch(Exception ex){error(ex);}});return button;}
    private String id(){return selectedId;}
    private String requireId() throws IOException {String value=id();if(value==null)throw new IOException("Sélectionne une instance locale.");return value;}
    @FunctionalInterface private interface Job<T>{T call(InstanceFiles.Progress progress) throws Exception;}
    private <T> void run(Job<T> job,Consumer<T> done){
        if(busy)return;busy=true;body.setDisable(true);cancelled.set(false);cancel.setText("Annuler à un point sûr");cancel.setDisable(false);cancel.setVisible(true);cancel.setManaged(true);progress.setVisible(true);progress.setManaged(true);progress.setProgress(-1);
        var selected=instances.getSelectionModel().getSelectedItem();String operationName=selected==null?"Cookie Core":selected.name();
        status.setText(operationName+" — Préparation…");
        Task<T> task=new Task<>(){
            protected T call() throws Exception {
                long[] last={0};
                return job.call((step,value,total)->{
                    if(cancelled.get())throw new InterruptedIOException("Opération annulée avant publication. L'original est conservé.");
                    long now=System.nanoTime();boolean publication=step.startsWith("Publication")||step.startsWith("Retrait");
                    if(now-last[0]>80_000_000||publication){last[0]=now;updateMessage(operationName+" — "+step);updateProgress(total<=0?-1:value,total<=0?1:total);if(publication)Platform.runLater(()->cancel.setDisable(true));}
                });
            }
        };
        status.textProperty().bind(task.messageProperty());progress.progressProperty().bind(task.progressProperty());
        Runnable finish=()->{busy=false;body.setDisable(false);status.textProperty().unbind();progress.progressProperty().unbind();progress.setVisible(false);progress.setManaged(false);cancel.setVisible(false);cancel.setManaged(false);};
        task.setOnSucceeded(e->{finish.run();status.setText("Opération terminée.");done.accept(task.getValue());});
        task.setOnFailed(e->{finish.run();error(task.getException());});
        new Thread(task,"cookiecore-instance-operation").start();
    }
    private void error(Throwable error){status.setText(error.getMessage()==null?"Opération impossible. Consulte les opérations de récupération.":error.getMessage());}
    private boolean inScope(LocalInstance instance){return initialId!=null&&initialId.startsWith("cookiecore-official-")?instance.id().equals(initialId):instance.official()==null;}
    void reload(String selected){run(p->manager.list().stream().filter(this::inScope).toList(),values->{instances.getItems().setAll(values);String wanted=selected==null?initialId:selected;instances.getSelectionModel().select(wanted==null?(values.isEmpty()?null:values.getFirst()):values.stream().filter(i->i.id().equals(wanted)).findFirst().orElse(null));
        if(id()==null){details.setText(wanted==null?"Crée ou importe une instance pour commencer.":"Cette instance n'est pas installée. Installe-la depuis le catalogue, ou sélectionne une autre installation locale.");items.getItems().clear();}
        changed.run();});}
    private record Details(LocalInstance local,InstanceManager.Status state,List<InstanceContentService.Item> items){}
    private void loadDetails(){String id=id();var type=kind.getValue();run(p->{var i=manager.get(id);OfficialInstance target=i.official()==null?null:catalog.get(i.official().getId());var state=manager.status(i,target);return new Details(i,state,state.state()==InstanceManager.State.MISSING||state.state()==InstanceManager.State.INCOMPLETE||state.state()==InstanceManager.State.ERROR?List.of():contents.list(id,type));},result->{
        var i=result.local();var state=result.state();details.setText(i.name()+" · "+(i.official()==null?"Instance personnelle":"Instance officielle")+"\nMinecraft "+i.minecraft()+" · "+i.loader()+"\n"+state.message()+(i.official()==null?"":" · Pack "+state.installed()+" → "+state.target()));locationDetails.setText("Emplacement : "+i.location());update.setVisible(i.official()!=null);update.setManaged(i.official()!=null);
        update.setDisable(state.state()!=InstanceManager.State.UPDATE_AVAILABLE||i.official()==null||catalog.get(i.official().getId())==null||!catalog.get(i.official().getId()).isDownloadAvailable());items.getItems().setAll(result.items());
        instanceImage.setImage(new Image(getClass().getResource("/images/banners/mc.jpg").toExternalForm(),160,90,true,true));
        String artwork=i.image();
        // Locally chosen illustrations win; otherwise use the current official catalog.
        if(i.official()!=null&&(artwork==null||!artwork.startsWith("file:"))){
            var published=catalog.get(i.official().getId());
            if(published!=null&&published.getImagePath()!=null&&!published.getImagePath().isBlank())artwork=published.getImagePath();
            else if((artwork==null||artwork.isBlank())&&i.official().getImagePath()!=null)artwork=i.official().getImagePath();
        }
        final String imageSource=artwork;
        if(imageSource!=null&&!imageSource.isBlank()){
            if(imageSource.startsWith("https://"))new NewsImageCacheService().resolve(imageSource).whenComplete((file,error)->Platform.runLater(()->{if(error==null&&file!=null&&Objects.equals(id(),i.id()))instanceImage.setImage(new Image(file.toUri().toString(),160,90,true,true,true));}));
            else try{instanceImage.setImage(new Image(imageSource,160,90,true,true,true));}catch(IllegalArgumentException ignored){}
        }
        contentHelp.setText(type==InstanceContentService.Kind.MODS?"Les fichiers du pack modifiés deviennent des conflits lors d’une mise à jour. La détection des incompatibilités est partielle ; vérifie aussi les dépendances des mods.":"L'ajout installe l'archive sans l'activer. Active les resource packs dans Minecraft ; les shaders dans les options de ton moteur (Iris, Oculus ou OptiFine selon l'instance).");
    });}
    private boolean confirm(String header,String message){var dialog=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL);dialog.initOwner(stage);dialog.setHeaderText(header);ThemeService.attach(dialog);return dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private Optional<String> input(String title,String value){var dialog=new TextInputDialog(value);dialog.initOwner(stage);dialog.setHeaderText(title);ThemeService.attach(dialog);return dialog.showAndWait();}
    private void rename(){String id=id();if(id==null)return;input("Nom de l'instance (identifiant conservé)",instances.getSelectionModel().getSelectedItem().name()).ifPresent(name->run(p->{manager.rename(id,name);return null;},v->reload(id)));}
    private void illustration(){String id=id();if(id==null)return;var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg"));var file=chooser.showOpenDialog(stage);if(file!=null)run(p->{manager.illustration(id,file.toPath());return null;},v->reload(id));}
    private void launch(){String id=id();if(id==null)return;run(p->{if(new PrismAccountService(new PrismLauncherService().getDataDirectory()).readSelected().isEmpty())return false;manager.launch(id);return true;},ok->{if(ok){status.setText("Lancement demandé à Prism.");changed.run();}else if(account!=null){stage.close();account.accept(()->open(stage.getOwner(),id,new ArrayList<>(catalog.values()),account,changed,null));}else status.setText("Connecte ton compte dans Compte Minecraft.");});}
    private void importPack(){var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Export Prism / MultiMC","*.zip"));var archive=chooser.showOpenDialog(stage);if(archive==null)return;input("Nom de l'instance importée",archive.getName().replaceFirst("\\.zip$","")).ifPresent(name->run(p->manager.importArchive(name,archive.toPath(),p),i->reload(i.id())));}
    private void create(){
        run(p->new PrismVersions().minecraft(),versions->{
            var dialog=new InstanceCreationDialog(stage,versions,new PrismVersions()::loaders);
            dialog.showAndWait().ifPresent(choice->run(p->manager.create(choice.name(),choice.minecraft(),choice.loader(),choice.version(),p),i->reload(i.id())));
        });
    }
    private void update(){String id=id();if(id==null)return;var local=instances.getSelectionModel().getSelectedItem();var target=local.official()==null?null:catalog.get(local.official().getId());if(target==null)return;
        if(!confirm("Préparer la mise à jour",local.name()+" : "+local.official().getModpackVersion()+" → "+target.getModpackVersion()+"\nLe téléchargement et la vérification précèdent toute modification. Ferme Minecraft et Prism."))return;
        run(p->{p.report("Téléchargement et vérification SHA-256",0,-1);Path archive=new ModpackDownloadService().downloadModpack(target,p);try{return manager.prepareUpdate(id,target,archive,p);}finally{Files.deleteIfExists(archive);}},plan->{pending=plan;
            String message=(plan.legacy()?"Ancienne installation sans manifeste : aucun ancien fichier inconnu ne sera supprimé. Il faudra vérifier manuellement les anciens mods.\n\n":"")+"Conflits : "+plan.conflicts().size()+"\n"+String.join("\n",plan.conflicts().stream().limit(30).toList())+"\n\nUne sauvegarde complète et le nouveau paquet seront conservés. Les fichiers personnels supprimés du catalogue mais modifiés localement sont conservés. Le profil de composants Minecraft est remplacé ; les réglages JVM sont conservés.";
            var keep=new ButtonType("Conserver mes fichiers");var pack=new ButtonType("Utiliser les fichiers du pack");var dialog=new Alert(Alert.AlertType.CONFIRMATION,message,keep,pack,ButtonType.CANCEL);dialog.initOwner(stage);dialog.setHeaderText("Politique de conflits — "+local.name());ThemeService.attach(dialog);
            var choice=dialog.showAndWait().orElse(ButtonType.CANCEL);
            if(choice==ButtonType.CANCEL)run(p->{manager.abandon(plan.operation());return null;},v->{pending=null;reload(id);});
            else run(p->{manager.finishUpdate(plan,choice==keep?InstanceManager.ConflictPolicy.KEEP_LOCAL:InstanceManager.ConflictPolicy.USE_PACK,p);return null;},v->{pending=null;reload(id);});
        });
    }
    private void move(){String id=id();if(id==null)return;var chooser=new DirectoryChooser();chooser.setTitle("Dossier de destination de l'instance");var destination=chooser.showDialog(stage);if(destination==null)return;
        if(confirm("Déplacer "+instances.getSelectionModel().getSelectedItem().name(),"Actuel : "+instances.getSelectionModel().getSelectedItem().location()+"\nDestination : "+destination+"\nLa copie sera vérifiée. L'ancien dossier sera conservé comme sauvegarde après la bascule."))run(p->{manager.move(id,destination.toPath(),p);return null;},v->reload(id));
    }
    private void uninstall(){String id=id();if(id==null)return;run(p->{Path folder=manager.folder(id);boolean worlds=Files.isDirectory(folder.resolve(".minecraft/saves"))||Files.isDirectory(folder.resolve("minecraft/saves"));return worlds;},worlds->{
        var backup=new CheckBox("Conserver une sauvegarde complète");backup.setSelected(true);var text=new Label(instances.getSelectionModel().getSelectedItem().name()+"\n"+instances.getSelectionModel().getSelectedItem().location()+"\n"+(worlds?"Un dossier de mondes est présent.":"Aucun dossier de mondes détecté.")+"\nCette action retire tous les mods, réglages, mondes et captures de cette instance. Sans sauvegarde, ces données seront supprimées.");text.setWrapText(true);
        var dialog=new Alert(Alert.AlertType.CONFIRMATION);dialog.initOwner(stage);dialog.setHeaderText("Désinstaller cette instance ?");dialog.getDialogPane().setContent(new VBox(14,text,backup));ThemeService.attach(dialog);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){boolean keep=backup.isSelected();run(p->{manager.uninstall(id,keep,p);return null;},v->reload(null));}
    });}
    private void recovery(){run(p->manager.operations().stream().filter(j->inScope(j.before())).toList(),operations->{
        if(operations.isEmpty()){status.setText("Aucune opération enregistrée.");return;}
        var choices=new ChoiceDialog<InstanceManager.Journal>(operations.getLast(),operations);choices.initOwner(stage);choices.setHeaderText("Choisir une opération (sauvegarde et état)");ThemeService.attach(choices);
        choices.showAndWait().ifPresent(j->{if(confirm("Récupération",j.kind()+" — "+j.before().name()+"\nÉtat : "+j.phase()+"\n"+j.note()+"\n"+(j.phase().equals("PREPARED")?"Annuler la préparation et conserver l'installation actuelle ?":"Restaurer la sauvegarde ? La version remplacée sera conservée séparément.")))run(p->{if(j.phase().equals("PREPARED"))manager.abandon(j.operation());else manager.restore(j.operation());return null;},v->reload(j.before().id()));});
    });}
    private void addContent(){String id=id();if(id==null)return;var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(kind.getValue()==InstanceContentService.Kind.MODS?"Mods JAR":"Archives ZIP",kind.getValue()==InstanceContentService.Kind.MODS?"*.jar":"*.zip"));var files=chooser.showOpenMultipleDialog(stage);if(files!=null)run(p->{contents.add(id,selectedKind,files.stream().map(File::toPath).toList(),p);return null;},v->loadDetails());}
    private void toggleContent(){var item=items.getSelectionModel().getSelectedItem();if(item!=null)run(p->{contents.toggle(requireId(),item.file());return null;},v->loadDetails());}
    private void removeContent(){var item=items.getSelectionModel().getSelectedItem();if(item!=null&&confirm("Retirer "+item.file(),item.origin()+"\nUne copie sera conservée dans .cookiecore-content-backups. Si ce fichier appartient au pack, ce retrait sera traité comme une personnalisation lors des mises à jour."))run(p->{contents.remove(requireId(),selectedKind,item.file());return null;},v->loadDetails());}
    private static void openFolder(Path path) throws IOException {if(!java.awt.Desktop.isDesktopSupported())throw new IOException("Ouverture du dossier indisponible");java.awt.Desktop.getDesktop().open(path.toFile());}
}
