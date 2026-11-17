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
        pane.setMinWidth(420);dialog.setResizable(true);
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
    public static boolean reducedMotion(){return SettingsService.get().current().reducedMotion();}
    public static boolean reducedTransparency(){return SettingsService.get().current().reducedTransparency();}
    public static Map<ThemeColor,String> renderedColors(Map<ThemeColor,String> saved){
        var result=new EnumMap<ThemeColor,String>(saved);
        for(var key:ThemeColor.values()) {
            String neutral=switch(key){
                case BACKGROUND, OVERLAY -> "#101216";
                case SIDEBAR, CONTENT -> "#191C22";
                case PANEL, CARD, NAV_INACTIVE, BUTTON_DISABLED -> "#23272F";
                case NAV_HOVER -> "#333943";
                case TEXT, ICON, BANNER_TEXT -> "#F4F5F7";
                case TEXT_SECONDARY, TEXT_MUTED -> "#BDC4D0";
                case BORDER, SEPARATOR -> "#454C59";
                case SUCCESS -> "#78D6A3";
                case WARNING -> "#F0C674";
                case ERROR -> "#FF8795";
                default -> null;
            };
            if(neutral!=null)result.put(key,neutral);
        }
        return result;
    }
    public static String foreground(String color){return contrast(color,"#FFFFFF")>=contrast(color,"#000000")?"#FFFFFF":"#000000";}
    private static void apply(Parent root){
        var palette=renderedColors(active);var style=new StringBuilder();for(var key:ThemeColor.values())style.append(key.css()).append(':').append(palette.get(key)).append(';');
        style.append("-cc-play-ink:").append(foreground(active.get(ThemeColor.PLAY))).append(';');
        style.append("-cc-action-ink:").append(foreground(active.get(ThemeColor.BUTTON))).append(';');
        style.append("-cc-selection-ink:").append(foreground(active.get(ThemeColor.SELECTION))).append(';');
        var accent=javafx.scene.paint.Color.web(active.get(ThemeColor.ACCENT));
        int red=(int)(28+accent.getRed()*32),green=(int)(31+accent.getGreen()*32),blue=(int)(39+accent.getBlue()*32);
        style.append(String.format(java.util.Locale.ROOT,"-cc-material: rgba(%d,%d,%d,.78);-cc-material-solid: rgb(%d,%d,%d);-cc-wash: rgba(%d,%d,%d,.52);",red,green,blue,red,green,blue,red,green,blue));
        root.setStyle(style.toString());
        root.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("opaque"),reducedTransparency());
        root.requestLayout();
    }
    public static double contrast(String a,String b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(String value){int rgb=Integer.parseInt(value.substring(1),16);double sum=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double c=((rgb>>(16-i*8))&255)/255.;sum+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
}
