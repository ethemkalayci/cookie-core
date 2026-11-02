package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import javafx.scene.*;
import javafx.stage.Window;
import javafx.collections.ListChangeListener;
import java.util.*;
public final class ThemeService {
    private static final Map<Parent,Boolean> roots=new WeakHashMap<>();
    private static Map<ThemeColor,String> active=Theme.AMETHYSTE.colors();
    private static boolean watching;
    public static void attach(Scene scene){
        roots.put(scene.getRoot(),true);apply(scene.getRoot());
        if(!scene.getStylesheets().contains(css()))scene.getStylesheets().add(css());
        if(!watching){watching=true;Window.getWindows().addListener((ListChangeListener<Window>)c->{while(c.next())if(c.wasAdded())for(var w:c.getAddedSubList())if(w.getScene()!=null)attach(w.getScene());});}
    }
    /** Initialize dialogs before show() triggers their first CSS sizing pass. */
    public static void attach(javafx.scene.control.Dialog<?> dialog){
        var pane=dialog.getDialogPane();
        roots.put(pane,true);apply(pane);
        if(!pane.getStylesheets().contains(css()))pane.getStylesheets().add(css());
    }
    private static String css(){return Objects.requireNonNull(ThemeService.class.getResource("/css/style.css")).toExternalForm();}
    public static void preview(Map<ThemeColor,String> colors){
        var copy=new EnumMap<ThemeColor,String>(ThemeColor.class);
        for(var key:ThemeColor.values())copy.put(key,SettingsService.hex(colors.get(key)));
        active=Map.copyOf(copy);for(var root:List.copyOf(roots.keySet()))apply(root);
    }
    public static void restore(){preview(SettingsService.get().current().activeColors());}
    private static void apply(Parent root){
        var style=new StringBuilder();for(var key:ThemeColor.values())style.append(key.css()).append(':').append(active.get(key)).append(';');
        root.setStyle(style.toString());
    }
    public static double contrast(String a,String b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(String value){int rgb=Integer.parseInt(value.substring(1),16);double sum=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double c=((rgb>>(16-i*8))&255)/255.;sum+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
}
