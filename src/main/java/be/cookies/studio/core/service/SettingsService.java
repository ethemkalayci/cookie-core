package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.UnaryOperator;

public final class SettingsService {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static class Holder { static final SettingsService INSTANCE=new SettingsService(new PrismLauncherService().getDataDirectory().getParent()); }
    public static SettingsService get(){return Holder.INSTANCE;}
    private final Path file;
    private LauncherSettings current=LauncherSettings.defaults();
    private String warning="";
    public SettingsService(Path root){file=root.resolve("settings.json");reload();}
    public Path root(){return file.getParent();}
    public synchronized LauncherSettings current(){return current;}
    public synchronized String warning(){return warning;}
    public synchronized void reload(){
        current=LauncherSettings.defaults();warning="";
        if(!Files.exists(file))return;
        try {
            if(Files.size(file)>2_000_000)throw new IOException();
            var j=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(j.get("version").getAsInt()!=1)throw new IOException();
            var custom=new LinkedHashMap<String,LauncherSettings.CustomTheme>();
            for(var entry:j.getAsJsonObject("customThemes").entrySet()) {
                try {
                    if(!entry.getKey().startsWith("custom-"))continue;
                    var t=entry.getValue().getAsJsonObject();
                    var colors=new EnumMap<ThemeColor,String>(ThemeColor.class);
                    for(var key:ThemeColor.values())colors.put(key,hex(t.getAsJsonObject("colors").get(key.name()).getAsString()));
                    String name=t.get("name").getAsString().trim();if(name.isEmpty()||name.length()>60)continue;
                    custom.put(entry.getKey(),new LauncherSettings.CustomTheme(name,readTheme(t.get("base").getAsString()),colors));
                } catch(Exception ignored){warning="Un thème personnel invalide a été ignoré.";}
            }
            String active=j.get("activeTheme").getAsString();
            if(!custom.containsKey(active))try{active=readTheme(active).name();}catch(Exception e){active="AMETHYSTE";}
            int ram=j.get("memoryMiB").getAsInt();if(ram!=0&&(ram<1024||ram>MemorySettings.maxMiB()))ram=0;
            String path=j.get("installDirectory").getAsString();
            if(!path.isBlank()&&!Path.of(path).isAbsolute())path="";
            current=new LauncherSettings(active,custom,ram,path);
        } catch(Exception e){current=LauncherSettings.defaults();warning="Préférences illisibles : valeurs sûres restaurées. Le fichier d'origine est conservé.";}
    }
    public synchronized LauncherSettings update(UnaryOperator<LauncherSettings> change) throws IOException {
        LauncherSettings next=change.apply(current);
        if(!next.customThemes().containsKey(next.activeTheme()))try{Theme.valueOf(next.activeTheme());}catch(Exception e){throw new IOException("Thème actif inconnu.");}
        for(var entry:next.customThemes().entrySet())if(!entry.getKey().startsWith("custom-")||entry.getValue().name().isBlank()||entry.getValue().name().length()>60)throw new IOException("Thème personnel invalide.");
        if(!next.installDirectory().isBlank()&&!Path.of(next.installDirectory()).isAbsolute())throw new IOException("Le dossier doit être absolu.");
        for(var theme:next.customThemes().values())for(var key:ThemeColor.values())hex(theme.colors().get(key));
        if(next.memoryMiB()!=0&&(next.memoryMiB()<1024||next.memoryMiB()>MemorySettings.maxMiB()))throw new IOException("Allocation mémoire hors limites.");
        var json=JSON.toJsonTree(next).getAsJsonObject();json.addProperty("version",1);
        Files.createDirectories(file.getParent());
        if(Files.exists(file))Files.copy(file,file.resolveSibling("settings.previous.json"),StandardCopyOption.REPLACE_EXISTING);
        Path tmp=Files.createTempFile(file.getParent(),".settings-",".tmp");
        try {Files.writeString(tmp,JSON.toJson(json));CacheFiles.publish(tmp,file,true);current=next;warning="";return next;}
        finally{CacheFiles.cleanup(tmp);}
    }
    // Legacy IDs are accepted only when loading existing preferences.
    // Subsequent saves use the new enum names for both active and custom themes.
    private static Theme readTheme(String id) {
        return switch(id) {
            case "CORBAX" -> Theme.AMETHYSTE;
            case "REDX" -> Theme.RUBIS;
            case "NAGIPI" -> Theme.NEON;
            case "ARTH" -> Theme.AMBRE;
            case "SPEED" -> Theme.OCEAN;
            case "CORBEAU" -> Theme.FORET;
            case "CHRIS" -> Theme.ECLIPSE;
            case "YASMINE" -> Theme.ROSE_POUDRE;
            default -> Theme.valueOf(id);
        };
    }
    public static String hex(String value){if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Couleur attendue : #RRGGBB");return value.toUpperCase(Locale.ROOT);}
}
