package be.cookies.studio.core.model;
import java.util.*;
import static be.cookies.studio.core.model.ThemeColor.*;
public enum Theme {
    AMETHYSTE("Améthyste", "#0E0B1D", "#2A1E4D", "#3A3A52", "#5A3B8C", "#8B7CC7", "#F5F2FA", "#C9C2E3"),
    RUBIS("Rubis", "#120C10", "#28131B", "#5E2430", "#BE3046", "#EC6175", "#F7E9EC", "#EC6175"),
    NEON("Néon", "#0D0414", "#000000", "#000000", "#FF00FF", "#00FFFF", "#F7F7F7", "#BAFF00"),
    AMBRE("Ambre", "#11110D", "#252018", "#66421E", "#F4CE58", "#F28C28", "#F7F3E6", "#F28C28"),
    OCEAN("Océan", "#0B132B", "#1C2541", "#3A506B", "#5BC0DE", "#F4D6B0", "#F8F6F1", "#D97706"),
    FORET("Forêt", "#0F1F17", "#1E3A2E", "#3E5A3F", "#6B7F67", "#C7CBB8", "#F3F5EE", "#3D4337"),
    ECLIPSE("Éclipse", "#0D1526", "#16233D", "#284B7A", "#4DA3FF", "#F4C542", "#F5F7FA", "#F4C542"),
    ROSE_POUDRE("Rose poudré", "#140F14", "#211822", "#3A2A3B", "#F39EC5", "#C97AA6", "#F7EEF4", "#C97AA6");
    public final String label;
    private final Map<ThemeColor,String> colors;
    Theme(String label,String bg,String side,String panel,String accent,String second,String text,String third) {
        this.label=label;
        var c=new EnumMap<ThemeColor,String>(ThemeColor.class);
        for(var key:valuesOfColors()) c.put(key,text);
        c.put(BACKGROUND,bg);c.put(SIDEBAR,side);c.put(PANEL,panel);c.put(CARD,side);c.put(CONTENT,bg);
        c.put(NAV_ACTIVE,accent);c.put(NAV_INACTIVE,side);c.put(NAV_HOVER,panel);c.put(SELECTION,second);
        c.put(BUTTON,accent);c.put(BUTTON_TEXT,contrast(accent,bg)>=contrast(accent,text)?bg:text);c.put(BUTTON_HOVER,second);c.put(BUTTON_DISABLED,panel);c.put(PLAY,accent);
        c.put(TEXT,text);c.put(TEXT_SECONDARY,second);c.put(TEXT_MUTED,text);c.put(BORDER,panel);c.put(SEPARATOR,third);c.put(ICON,text);
        c.put(ACCENT,accent);c.put(ACCENT_SECONDARY,second);c.put(ACCENT_TERTIARY,third);c.put(PROGRESS,accent);
        c.put(SUCCESS,"#7AD69C");c.put(ERROR,"#FF7185");c.put(WARNING,"#F4CE58");c.put(BANNER_TEXT,text);c.put(OVERLAY,bg);
        colors=Map.copyOf(c);
    }
    private static ThemeColor[] valuesOfColors(){return ThemeColor.values();}
    private static double contrast(String a,String b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(String value){int rgb=Integer.parseInt(value.substring(1),16);double sum=0;double[] w={.2126,.7152,.0722};for(int i=0;i<3;i++){double c=((rgb>>(16-i*8))&255)/255.;sum+=w[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
    public Map<ThemeColor,String> colors(){return colors;}
    @Override public String toString(){return label;}
}
