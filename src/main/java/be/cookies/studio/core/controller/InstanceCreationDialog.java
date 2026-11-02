package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.ModLoader;
import be.cookies.studio.core.service.ThemeService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.util.List;

/** Fixed, owner-modal creation form. Version discovery stays on worker threads. */
final class InstanceCreationDialog extends Dialog<InstanceCreationDialog.Selection> {
    record Selection(String name,String minecraft,ModLoader loader,String version){}
    @FunctionalInterface interface Versions {List<String> load(String minecraft,ModLoader loader)throws Exception;}
    private int generation;
    InstanceCreationDialog(Window owner,List<String> minecraftVersions,Versions versions){
        initOwner(owner);initModality(Modality.WINDOW_MODAL);initStyle(StageStyle.UNDECORATED);setResizable(false);setTitle("Créer une instance");
        var pane=getDialogPane();pane.getStyleClass().add("instance-creation-dialog");
        var createType=new ButtonType("Créer",ButtonBar.ButtonData.OK_DONE);pane.getButtonTypes().addAll(ButtonType.CANCEL,createType);
        ((Button)pane.lookupButton(ButtonType.CANCEL)).setText("Annuler");var create=(Button)pane.lookupButton(createType);create.setDisable(true);
        var title=new Label("Créer une instance");title.getStyleClass().add("section-title");
        var close=new Button("×");close.setAccessibleText("Fermer la création d’instance");close.getStyleClass().add("secondary-button");close.setOnAction(e->close());
        var space=new Region();HBox.setHgrow(space,Priority.ALWAYS);var header=new HBox(12,title,space,close);header.setAlignment(Pos.CENTER_LEFT);
        var artwork=new Image(getClass().getResource("/images/banners/mc.jpg").toExternalForm());var picture=new ImageView(artwork);double edge=Math.min(artwork.getWidth(),artwork.getHeight());picture.setViewport(new javafx.geometry.Rectangle2D((artwork.getWidth()-edge)/2,(artwork.getHeight()-edge)/2,edge,edge));picture.setFitWidth(100);picture.setFitHeight(100);picture.setPreserveRatio(true);
        var name=new TextField();name.setId("creationName");name.setPromptText("Nom de l’instance");name.setAccessibleText("Nom de l’instance");
        var nameLabel=new Label("Nom de l’instance");nameLabel.setLabelFor(name);var identity=new VBox(10,nameLabel,name);identity.setAlignment(Pos.CENTER_LEFT);identity.setMinWidth(0);HBox.setHgrow(identity,Priority.ALWAYS);
        var profile=new HBox(24,picture,identity);profile.setAlignment(Pos.CENTER_LEFT);
        var minecraft=new ComboBox<String>();minecraft.setId("creationMinecraft");minecraft.getItems().setAll(minecraftVersions);minecraft.setMaxWidth(Double.MAX_VALUE);minecraft.setVisibleRowCount(10);minecraft.setAccessibleText("Version Minecraft");if(!minecraftVersions.isEmpty())minecraft.setValue(minecraftVersions.getFirst());
        var loaderVersion=new ComboBox<String>();loaderVersion.setId("creationLoaderVersion");loaderVersion.setPromptText("Choisir une version compatible");loaderVersion.setMaxWidth(Double.MAX_VALUE);loaderVersion.setVisibleRowCount(10);loaderVersion.setAccessibleText("Version du modloader");loaderVersion.getItems().setAll("");loaderVersion.setValue("");
        var loaderVersionBox=new VBox(10,new Label("Version du modloader"),loaderVersion);loaderVersionBox.setVisible(false);loaderVersionBox.setManaged(false);
        var group=new ToggleGroup();var loaders=new FlowPane(16,12);
        for(var loader:new ModLoader[]{ModLoader.FORGE,ModLoader.FABRIC,ModLoader.NEOFORGE,ModLoader.VANILLA}){
            String label=switch(loader){case FORGE->"Forge";case FABRIC->"Fabric";case NEOFORGE->"NeoForge";case VANILLA->"Vanilla";};
            var radio=new RadioButton(label);radio.setId("creation"+loader.name());radio.setToggleGroup(group);radio.setUserData(loader);loaders.getChildren().add(radio);if(loader==ModLoader.VANILLA)radio.setSelected(true);
        }
        var hint=new Label("Vanilla : Minecraft sans modloader. Le jeu sera préparé au premier lancement.");hint.setWrapText(true);hint.getStyleClass().add("muted");
        var retry=new Button("Réessayer");retry.setVisible(false);retry.setManaged(false);
        var content=new VBox(24,header,profile,new VBox(10,new Label("Version Minecraft"),minecraft),new VBox(12,new Label("Modloader"),loaders),loaderVersionBox,hint,retry);content.setPadding(new Insets(24));
        var scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);pane.setContent(scroll);
        Runnable validate=()->create.setDisable(name.getText().strip().isEmpty()||name.getText().strip().length()>100||name.getText().chars().anyMatch(Character::isISOControl)||minecraft.getValue()==null||loaderVersion.getValue()==null);
        Runnable fetch=()->{
            int request=++generation;ModLoader loader=(ModLoader)group.getSelectedToggle().getUserData();String mc=minecraft.getValue();retry.setVisible(false);retry.setManaged(false);loaderVersion.getItems().clear();loaderVersion.setValue(null);loaderVersion.setDisable(true);validate.run();
            boolean vanilla=loader==ModLoader.VANILLA;loaderVersionBox.setVisible(!vanilla);loaderVersionBox.setManaged(!vanilla);
            if(vanilla){loaderVersion.getItems().setAll("");loaderVersion.setValue("");hint.setText("Vanilla : Minecraft sans modloader. Le jeu sera préparé au premier lancement.");validate.run();return;}
            if(mc==null){hint.setText("Aucune version Minecraft disponible.");return;}
            hint.setText("Chargement des versions compatibles…");
            Task<List<String>> task=new Task<>(){protected List<String> call()throws Exception{return versions.load(mc,loader);}};
            task.setOnSucceeded(e->{if(request!=generation)return;loaderVersion.getItems().setAll(task.getValue());loaderVersion.setDisable(task.getValue().isEmpty());if(!task.getValue().isEmpty())loaderVersion.setValue(task.getValue().getFirst());hint.setText(task.getValue().isEmpty()?"Aucune version de "+loader+" pour Minecraft "+mc+". Choisis une autre version Minecraft ou Vanilla.":"Choisis une version compatible. Le jeu sera préparé au premier lancement.");validate.run();});
            task.setOnFailed(e->{if(request!=generation)return;hint.setText("Impossible de récupérer les versions. Vérifie ta connexion et réessaie.");retry.setVisible(true);retry.setManaged(true);validate.run();});
            var thread=new Thread(task,"creation-compatible-versions");thread.setDaemon(true);thread.start();
        };
        name.textProperty().addListener((o,a,b)->validate.run());loaderVersion.setOnAction(e->validate.run());minecraft.setOnAction(e->fetch.run());group.selectedToggleProperty().addListener((o,a,b)->{if(b!=null)fetch.run();});retry.setOnAction(e->fetch.run());
        setResultConverter(button->button==createType?new Selection(name.getText().strip(),minecraft.getValue(),(ModLoader)group.getSelectedToggle().getUserData(),loaderVersion.getValue()):null);
        ThemeService.attach(this);
        var bounds=Screen.getScreensForRectangle(owner.getX(),owner.getY(),Math.max(1,owner.getWidth()),Math.max(1,owner.getHeight())).stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double width=Math.min(560,bounds.getWidth()-32),height=Math.min(650,bounds.getHeight()-48);
        pane.setMinSize(width,height);pane.setPrefSize(width,height);pane.setMaxSize(width,height);
        var ownerRoot=owner.getScene().getRoot();var previousEffect=ownerRoot.getEffect();
        setOnShown(e->{if(pane.lookup(".button-bar") instanceof ButtonBar bar)bar.setButtonOrder("C+O");setX(Math.max(bounds.getMinX(),Math.min(owner.getX()+(owner.getWidth()-getWidth())/2,bounds.getMaxX()-getWidth())));setY(Math.max(bounds.getMinY(),Math.min(owner.getY()+(owner.getHeight()-getHeight())/2,bounds.getMaxY()-getHeight())));ownerRoot.setEffect(new GaussianBlur(5));name.requestFocus();});
        setOnHidden(e->{generation++;ownerRoot.setEffect(previousEffect);});
    }
}
