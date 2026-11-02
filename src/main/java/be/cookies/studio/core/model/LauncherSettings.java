package be.cookies.studio.core.model;
import java.util.*;
public record LauncherSettings(String activeTheme, Map<String,CustomTheme> customThemes, int memoryMiB, String installDirectory) {
    public record CustomTheme(String name, Theme base, Map<ThemeColor,String> colors) {
        public CustomTheme { colors=Map.copyOf(colors); }
    }
    public LauncherSettings { customThemes=Map.copyOf(customThemes); }
    public static LauncherSettings defaults(){return new LauncherSettings("AMETHYSTE",Map.of(),0,"");}
    public LauncherSettings appearance(String active,Map<String,CustomTheme> custom){return new LauncherSettings(active,custom,memoryMiB,installDirectory);}
    public LauncherSettings minecraft(int ram,String path){return new LauncherSettings(activeTheme,customThemes,ram,path);}
    public Map<ThemeColor,String> activeColors(){
        if(customThemes.containsKey(activeTheme))return customThemes.get(activeTheme).colors();
        try{return Theme.valueOf(activeTheme).colors();}catch(Exception e){return Theme.AMETHYSTE.colors();}
    }
}
