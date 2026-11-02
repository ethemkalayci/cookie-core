package be.cookies.studio.core.controller;

import be.cookies.studio.core.model.*;
import be.cookies.studio.core.service.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.concurrent.Task;
import java.util.*;
import java.util.concurrent.Callable;

public class SettingsController {
    private final SettingsService settings;
    public SettingsController() { this(SettingsService.get()); }
    SettingsController(SettingsService settings) { this.settings = settings; }
    record Choice(String id,String name){@Override public String toString(){return name;}}
    @FXML private ComboBox<Choice> themes;
    @FXML private ComboBox<Theme> baseTheme;
    @FXML private VBox colorRows;
    @FXML private TextField themeName, installPath;
    @FXML private Label status, contrastWarning, memoryInfo, ramValue, defaultPath;
    @FXML private Slider ramSlider;
    @FXML private CheckBox useMemory;
    @FXML private ComboBox<MemorySettings.Target> memoryTarget;
    @FXML private Button deleteTheme;
    @FXML private VBox settingsContent;
    private final EnumMap<ThemeColor,String> draft=new EnumMap<>(ThemeColor.class);
    private boolean loading, editing, disposed;
    private String editingId;
    private Theme base=Theme.AMETHYSTE;
    @FXML private void initialize(){
        be.cookies.studio.core.ui.ViewSupport.collapseWhenEmpty(status);
        loading=true;
        baseTheme.getItems().setAll(Theme.values());
        ramSlider.setMin(1);ramSlider.setMax(MemorySettings.maxMiB()/1024.0);ramSlider.setBlockIncrement(.25);
        ramSlider.setMajorTickUnit(1);ramSlider.setMinorTickCount(3);ramSlider.setSnapToTicks(true);
        var current=settings.current();
        useMemory.setSelected(current.memoryMiB()!=0);ramSlider.setValue(current.memoryMiB()==0?Math.min(4,ramSlider.getMax()):current.memoryMiB()/1024.0);
        ramSlider.disableProperty().bind(useMemory.selectedProperty().not());
        installPath.setText(current.installDirectory());
        defaultPath.setText("Par défaut : "+settings.root().resolve("prism-data").resolve("instances"));
        memoryInfo.setText(String.format(Locale.FRANCE,"RAM totale : %.1f Go • disponible actuellement : %.1f Go • plafond conseillé : %.1f Go",MemorySettings.totalMiB()/1024.,MemorySettings.freeMiB()/1024.,ramSlider.getMax()));
        ramSlider.valueProperty().addListener((o,a,b)->updateRam());updateRam();
        refreshChoices();loadDraft(current.activeTheme());
        themes.setOnAction(e->{if(!loading&&themes.getValue()!=null)selectTheme();});
        baseTheme.setOnAction(e->{if(!loading){base=baseTheme.getValue();draft.clear();draft.putAll(base.colors());editingId=null;deleteTheme.setDisable(true);themeName.clear();editing=true;buildRows();preview();}});
        status.setText(settings.warning());loading=false;loadMemoryTargets();
    }
    private void updateRam(){ramValue.setText(String.format(Locale.FRANCE,"%.2f Go",ramSlider.getValue())+(ramSlider.getValue()*1024>MemorySettings.freeMiB()?" — mémoire libre actuellement inférieure à ce choix":""));}
    private void refreshChoices(){
        boolean was=loading;loading=true;var c=settings.current();
        themes.getItems().clear();for(var theme:Theme.values())themes.getItems().add(new Choice(theme.name(),theme.label));
        c.customThemes().forEach((id,t)->themes.getItems().add(new Choice(id,t.name()+" (personnel)")));
        themes.getSelectionModel().select(themes.getItems().stream().filter(t->t.id().equals(c.activeTheme())).findFirst().orElse(themes.getItems().getFirst()));
        loading=was;
    }
    private void loadDraft(String id){
        boolean was=loading;loading=true;var custom=settings.current().customThemes().get(id);
        editingId=custom==null?null:id;base=custom==null?Theme.valueOf(id):custom.base();
        draft.clear();draft.putAll(custom==null?base.colors():custom.colors());
        baseTheme.setValue(base);themeName.setText(custom==null?"":custom.name());deleteTheme.setDisable(custom==null);
        editing=false;buildRows();loading=was;
    }
    private void selectTheme(){
        Choice selected=themes.getValue();loadDraft(selected.id());ThemeService.preview(draft);
        work(()->settings.update(c->c.appearance(selected.id(),c.customThemes())),()->{editing=false;status.setText("Thème appliqué et enregistré.");},true);
    }
    private void buildRows(){
        colorRows.getChildren().clear();String group="";
        for(var key:ThemeColor.values()){
            if(!key.group.equals(group)){group=key.group;Label title=new Label(group);title.getStyleClass().addAll("settings-heading", "color-group");colorRows.getChildren().add(title);}
            Label label=new Label(key.label);label.setMinWidth(190);label.setPrefWidth(220);
            ColorPicker picker=new ColorPicker(Color.web(draft.get(key)));TextField hex=new TextField(draft.get(key));hex.setPrefColumnCount(8);hex.setMaxWidth(110);
            Button reset=new Button("Réinitialiser");reset.getStyleClass().add("secondary-button");
            label.setLabelFor(picker);picker.setAccessibleText(key.label);hex.setAccessibleText(key.label+" en hexadécimal");reset.setAccessibleText("Réinitialiser "+key.label);
            picker.setOnAction(e->{String value=String.format("#%02X%02X%02X",Math.round(picker.getValue().getRed()*255),Math.round(picker.getValue().getGreen()*255),Math.round(picker.getValue().getBlue()*255));hex.setText(value);});
            hex.textProperty().addListener((o,a,b)->{
                try{String valid=SettingsService.hex(b);draft.put(key,valid);picker.setValue(Color.web(valid));hex.setStyle("");editing=true;preview();}
                catch(IllegalArgumentException invalid){hex.setStyle("-fx-border-color: -cc-error;");}
            });
            reset.setOnAction(e->hex.setText(base.colors().get(key)));
            FlowPane row=new FlowPane(12,8,label,picker,hex,reset);row.getStyleClass().add("color-row");colorRows.getChildren().add(row);
        }
        updateContrast();
    }
    private void preview(){ThemeService.preview(draft);updateContrast();status.setText("Aperçu non enregistré. Enregistre ton thème ou annule les modifications.");}
    private void updateContrast(){
        double text=ThemeService.contrast(draft.get(ThemeColor.TEXT),draft.get(ThemeColor.BACKGROUND));
        double buttons=ThemeService.contrast(draft.get(ThemeColor.BUTTON_TEXT),draft.get(ThemeColor.BUTTON));
        double play=ThemeService.contrast(draft.get(ThemeColor.BUTTON_TEXT),draft.get(ThemeColor.PLAY));
        double card=ThemeService.contrast(draft.get(ThemeColor.TEXT),draft.get(ThemeColor.CARD));
        boolean lowContrast = Math.min(Math.min(text,buttons),Math.min(play,card))<4.5;
        contrastWarning.getStyleClass().removeAll("warning", "muted");
        contrastWarning.getStyleClass().add(lowContrast ? "warning" : "muted");
        contrastWarning.setText(lowContrast
                ?String.format(Locale.FRANCE,"Contraste à vérifier (objectif 4,5:1) — texte %.1f, cartes %.1f, boutons %.1f, Jouer %.1f.",text,card,buttons,play)
                :"Contraste des textes principaux et boutons : satisfaisant (au moins 4,5:1).");
    }
    @FXML private void resetAll(){draft.clear();draft.putAll(base.colors());editing=true;buildRows();preview();}
    @FXML private void cancelPreview(){ThemeService.preview(settings.current().activeColors());loadDraft(settings.current().activeTheme());refreshChoices();status.setText("Aperçu annulé.");}
    @FXML private void saveTheme(){
        String name=themeName.getText().trim();if(name.isEmpty()||name.length()>60){status.setText("Donne un nom au thème (1 à 60 caractères).");return;}
        for(var node:colorRows.getChildren())if(node instanceof FlowPane row)for(var field:row.getChildren())if(field instanceof TextField text)try{SettingsService.hex(text.getText());}catch(Exception e){status.setText("Corrige les couleurs invalides avant d'enregistrer.");return;}
        String id=editingId==null?"custom-"+UUID.randomUUID():editingId;
        var theme=new LauncherSettings.CustomTheme(name,base,new EnumMap<>(draft));
        work(()->settings.update(c->{var all=new LinkedHashMap<>(c.customThemes());all.put(id,theme);return c.appearance(id,all);}),()->{
            ThemeService.preview(settings.current().activeColors());refreshChoices();loadDraft(id);status.setText("Thème personnel enregistré.");
        },true);
    }
    @FXML private void deleteTheme(){
        if(editingId==null)return;String id=editingId;
        var question=new Alert(Alert.AlertType.CONFIRMATION,"Supprimer ce thème personnel ?",ButtonType.YES,ButtonType.NO);
        ThemeService.attach(question);question.initOwner(themes.getScene().getWindow());if(question.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        work(()->settings.update(c->{var all=new LinkedHashMap<>(c.customThemes());all.remove(id);return c.appearance(c.activeTheme().equals(id)?"AMETHYSTE":c.activeTheme(),all);}),()->{ThemeService.preview(settings.current().activeColors());refreshChoices();loadDraft(settings.current().activeTheme());status.setText("Thème personnel supprimé.");},true);
    }
    @FXML private void chooseDirectory(){
        DirectoryChooser chooser=new DirectoryChooser();chooser.setTitle("Dossier des nouvelles instances");
        var chosen=chooser.showDialog(installPath.getScene().getWindow());if(chosen!=null)installPath.setText(chosen.getAbsolutePath());
    }
    @FXML private void defaultDirectory(){installPath.clear();}
    @FXML private void saveMinecraft(){
        int ram=useMemory.isSelected()?(int)Math.round(ramSlider.getValue()*4)*256:0;String input=installPath.getText().trim();
        work(()->{
            String path=input.isBlank()?"":InstanceStorage.validate(input).toString();
            return settings.update(c->c.minecraft(ram,path));
        },()->status.setText("Préférences enregistrées. Le dossier s'applique uniquement aux nouvelles installations."),false);
    }

    private void loadMemoryTargets(){
        Task<java.util.List<MemorySettings.Target>> task=new Task<>(){
            @Override protected java.util.List<MemorySettings.Target> call()throws Exception{return MemorySettings.targets(settings.root().resolve("prism-data"));}
        };
        task.setOnSucceeded(e->memoryTarget.getItems().setAll(task.getValue()));
        task.setOnFailed(e->memoryTarget.setPromptText("Liste indisponible"));
        Thread worker=new Thread(task,"settings-instances");worker.setDaemon(true);worker.start();
    }
    @FXML private void adoptMemory(){
        var target=memoryTarget.getValue();if(target==null){status.setText("Choisis une instance dans la liste.");return;}
        var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Remplacer le réglage mémoire de cette instance par le défaut Cookie Core enregistré ? Une copie du réglage initial sera conservée.",ButtonType.YES,ButtonType.NO);
        ThemeService.attach(confirm);confirm.initOwner(memoryTarget.getScene().getWindow());if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        work(()->{MemorySettings.adoptDefault(target,settings.current().memoryMiB(),settings.root().resolve("prism-data"));return null;},()->{status.setText("Cette instance utilise maintenant la mémoire par défaut Cookie Core.");loadMemoryTargets();},false);
    }
    private void work(Callable<?> operation,Runnable done,boolean restoreOnFailure){
        status.getStyleClass().remove("error");
        settingsContent.setDisable(true);
        Task<Object> task=new Task<>(){@Override protected Object call()throws Exception{return operation.call();}};
        task.setOnSucceeded(e->{settingsContent.setDisable(false);if(!disposed)done.run();else if(restoreOnFailure)ThemeService.preview(settings.current().activeColors());});
        task.setOnFailed(e->{settingsContent.setDisable(false);if(restoreOnFailure)ThemeService.preview(settings.current().activeColors());status.getStyleClass().add("error");status.setText("Enregistrement impossible : "+task.getException().getMessage());});
        Thread worker=new Thread(task,"settings-save");worker.setDaemon(false);worker.start();
    }
    public void dispose(){disposed=true;if(editing)ThemeService.preview(settings.current().activeColors());}
}
