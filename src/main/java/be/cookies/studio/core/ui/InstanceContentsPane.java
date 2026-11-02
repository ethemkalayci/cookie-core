package be.cookies.studio.core.ui;

import be.cookies.studio.core.service.*;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/** Embedded content browser; all archive and file operations run outside the FX thread. */
public final class InstanceContentsPane extends VBox {
    private final InstanceContentService service;
    private final Label status=new Label("Choisis Mods, Resource Packs ou Shaders pour afficher les contenus ici.");
    private final ListView<InstanceContentService.Item> list=new ListView<>();
    private final Button add=new Button("Ajouter"),refresh=new Button("Actualiser"),folder=new Button("Ouvrir le dossier");
    private final ProgressBar progress=new ProgressBar();
    private final TextField search=new TextField();
    private final ComboBox<String> filter=new ComboBox<>();
    private final Label counter=new Label();
    private final javafx.collections.ObservableList<InstanceContentService.Item> all=javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<InstanceContentService.Item> filtered=new javafx.collections.transformation.FilteredList<>(all);
    private String instanceId;
    private InstanceContentService.Kind kind;
    private int generation;
    private boolean mutating;

    public InstanceContentsPane(InstanceManager manager){
        service=new InstanceContentService(manager);setSpacing(12);setMinWidth(0);
        status.setWrapText(true);status.setMinWidth(0);
        search.setId("contentSearch");search.setPromptText("Rechercher un nom, une version ou un fichier…");search.setAccessibleText("Rechercher dans les contenus");search.setMinWidth(0);HBox.setHgrow(search,Priority.ALWAYS);
        filter.getItems().setAll("Tous","Actifs","Désactivés");filter.setValue("Tous");filter.setAccessibleText("Filtrer les mods par état");
        search.textProperty().addListener((o,a,b)->applyFilter());filter.setOnAction(e->applyFilter());
        counter.getStyleClass().add("muted");
        list.setItems(filtered);list.getStyleClass().add("content-list");ViewSupport.rounded(list,12);ViewSupport.rounded(progress,8);
        list.setId("inlineContentList");list.setMinWidth(0);list.setPrefHeight(140);list.setPlaceholder(new Label("Aucun contenu installé dans cette catégorie."));
        list.setCellFactory(view->new ListCell<>(){
            {prefWidthProperty().bind(view.widthProperty().subtract(20));setMaxWidth(Region.USE_PREF_SIZE);}
            @Override protected void updateItem(InstanceContentService.Item item,boolean empty){super.updateItem(item,empty);setText(null);setGraphic(empty||item==null?null:row(item));}
        });
        var actions=new FlowPane(10,8,add,refresh,folder);progress.setMaxWidth(Double.MAX_VALUE);progress.setVisible(false);progress.setManaged(false);
        getChildren().addAll(new HBox(10,search,filter),actions,counter,status,progress,list);controls(false);
        add.setOnAction(e->addFiles());refresh.setOnAction(e->load());folder.setOnAction(e->{String id=instanceId;var type=kind;mutate(p->{Path path=service.directory(id,type);if(!Files.isDirectory(path))throw new IOException("Ce dossier sera créé lors de l’ajout d’un contenu.");java.awt.Desktop.getDesktop().open(path.toFile());});});
    }
    public void show(String id,InstanceContentService.Kind type){kind=type;instanceId=id;load();}
    public InstanceContentService.Kind selectedKind(){return kind;}
    public void selectInstance(String id){
        if(!java.util.Objects.equals(instanceId,id)||kind==null){kind=InstanceContentService.Kind.MODS;search.clear();filter.setValue("Tous");}
        instanceId=id;load();
    }
    private void applyFilter(){
        String query=search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        filtered.setPredicate(item->(query.isEmpty()||(item.name()+" "+item.file()+" "+item.version()).toLowerCase(java.util.Locale.ROOT).contains(query))
            && (kind!=InstanceContentService.Kind.MODS||"Tous".equals(filter.getValue())||("Désactivés".equals(filter.getValue())==item.disabled())));
        counter.setText(filtered.size()+" / "+all.size()+" contenu(s)");
    }
    private void controls(boolean enabled){add.setDisable(!enabled||mutating);refresh.setDisable(kind==null||mutating);folder.setDisable(!enabled||mutating);list.setDisable(mutating);}
    private void load(){
        int request=++generation;String id=instanceId;var type=kind;all.clear();applyFilter();controls(false);
        if(type==null)return;
        if(id==null){status.setText("Sélectionne une instance officielle dans le carrousel.");return;}
        filter.setVisible(type==InstanceContentService.Kind.MODS);filter.setManaged(type==InstanceContentService.Kind.MODS);
        status.setText("Chargement des contenus…");list.setPlaceholder(new Label("Chargement…"));
        Task<List<InstanceContentService.Item>> task=new Task<>(){protected List<InstanceContentService.Item> call()throws Exception{return service.list(id,type);}};
        task.setOnSucceeded(e->{if(request!=generation)return;all.setAll(task.getValue());applyFilter();list.setPlaceholder(new Label("Aucun contenu ne correspond à cette sélection."));list.setPrefHeight(Math.max(140,Math.min(500,task.getValue().size()*112+8)));controls(true);status.setText((type==InstanceContentService.Kind.MODS?"La compatibilité détectée reste partielle.":"À activer ensuite dans les options de Minecraft."));});
        task.setOnFailed(e->{if(request!=generation)return;list.setPlaceholder(new Label("Contenus indisponibles"));status.setText(task.getException() instanceof NoSuchFileException?"Installe d’abord cette instance pour accéder à ses contenus.":"Contenus indisponibles : "+task.getException().getMessage());});
        worker(task,"instance-content-list");
    }
    private HBox row(InstanceContentService.Item item){
        var badge=new Label(kind==InstanceContentService.Kind.MODS?"M":kind==InstanceContentService.Kind.SHADERS?"S":"R");badge.getStyleClass().add("card-title");
        var icon=new StackPane(badge);icon.setMinSize(48,48);icon.setPrefSize(48,48);icon.setMaxSize(48,48);icon.getStyleClass().add("content-icon");
        if(item.image()!=null)try{var image=new Image(new ByteArrayInputStream(item.image()),48,48,true,true);if(!image.isError()){var view=new ImageView(image);view.setFitWidth(48);view.setFitHeight(48);view.setPreserveRatio(true);icon.getChildren().setAll(view);}}catch(RuntimeException ignored){}
        var name=new Label(item.name()+(item.disabled()?" · désactivé":""));name.getStyleClass().add("card-title");name.setWrapText(true);name.setMinWidth(0);
        var info=new Label(item.version()+" · "+item.origin());info.getStyleClass().add("muted");info.setWrapText(true);
        var warning=new Label(item.warning());warning.setWrapText(true);warning.setVisible(!item.warning().isBlank());warning.setManaged(!item.warning().isBlank());
        var remove=new Button("Retirer");String id=instanceId;var type=kind;
        remove.setOnAction(e->{var dialog=new Alert(Alert.AlertType.CONFIRMATION,"Retirer « "+item.name()+" » ? Une copie sera conservée dans les sauvegardes de contenus.",ButtonType.OK,ButtonType.CANCEL);dialog.initOwner(getScene().getWindow());ThemeService.attach(dialog);if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)mutate(p->service.remove(id,type,item.file()));});
        remove.getStyleClass().add("secondary-button");
        var actions=new FlowPane(8,6);if(type==InstanceContentService.Kind.MODS){var toggle=new Button(item.disabled()?"Activer":"Désactiver");toggle.setOnAction(e->mutate(p->service.toggle(id,item.file())));actions.getChildren().add(toggle);}actions.getChildren().add(remove);
        var text=new VBox(5,name,info,warning,actions);text.setMinWidth(0);HBox.setHgrow(text,Priority.ALWAYS);
        var row=new HBox(14,icon,text);row.setAlignment(Pos.TOP_LEFT);row.setMinWidth(0);return row;
    }
    private void addFiles(){
        String id=instanceId;var type=kind;if(id==null||type==null)return;
        var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(type==InstanceContentService.Kind.MODS?"Mods JAR":"Archives ZIP",type==InstanceContentService.Kind.MODS?"*.jar":"*.zip"));
        var files=chooser.showOpenMultipleDialog(getScene().getWindow());if(files!=null)mutate(p->service.add(id,type,files.stream().map(File::toPath).toList(),p));
    }
    @FunctionalInterface private interface Operation{void run(InstanceFiles.Progress progress)throws Exception;}
    private void mutate(Operation operation){
        if(mutating)return;mutating=true;status.setText("Modification des contenus en cours…");controls(false);progress.setVisible(true);progress.setManaged(true);
        Task<Void> task=new Task<>(){protected Void call()throws Exception{operation.run((step,done,total)->{updateMessage(step);updateProgress(total>0?done:-1,total>0?total:1);});return null;}};
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e->{finish();load();});task.setOnFailed(e->{finish();status.setText("Opération impossible : "+task.getException().getMessage());});worker(task,"instance-content-change");
    }
    private void finish(){mutating=false;progress.progressProperty().unbind();progress.setVisible(false);progress.setManaged(false);controls(instanceId!=null);}
    private static void worker(Task<?> task,String name){new Thread(task,name).start();}
}
