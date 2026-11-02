package be.cookies.studio.core.service;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class PackManifest {
    static final String FILE=".cookiecore-pack-files.json";
    record Data(int version,Map<String,String> files){}
    static boolean personal(String name){
        String n=name.toLowerCase(Locale.ROOT);
        return n.startsWith(".cookiecore")||n.equals("instance.cfg")||n.equals("instance.cfg.before-cookiecore-memory")
            ||n.startsWith("minecraft/saves/")||n.startsWith(".minecraft/saves/")||n.startsWith("minecraft/screenshots/")||n.startsWith(".minecraft/screenshots/")
            ||n.matches("(\\.minecraft|minecraft)/(options.*|servers.dat.*|usercache.json|logs/.*|crash-reports/.*|stats/.*)");
    }
    static Map<String,String> read(Path folder) throws IOException {
        if(!Files.exists(folder.resolve(FILE)))return Map.of();var data=InstanceFiles.readJson(folder.resolve(FILE),Data.class);
        if(data.version()!=1||data.files()==null)throw new IOException("Manifeste de pack illisible");
        for(var e:data.files().entrySet()){InstanceFiles.child(folder,e.getKey());if(!e.getValue().matches("[0-9a-f]{64}"))throw new IOException("Empreinte de manifeste invalide");}
        return data.files();
    }
    static Map<String,String> capture(Path folder,InstanceFiles.Progress progress) throws IOException {
        var files=new TreeMap<>(InstanceFiles.manifest(folder,progress));files.keySet().removeIf(PackManifest::personal);return files;
    }
    static void write(Path folder,Map<String,String> files) throws IOException {InstanceFiles.writeJson(folder.resolve(FILE),new Data(1,files));}
}
