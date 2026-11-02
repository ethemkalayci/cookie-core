package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class PrismArchive {
    record Components(String minecraft,ModLoader loader,String loaderVersion){}
    static Components describe(Path folder) throws IOException {
        try{
            var cfg=PrismConfig.read(folder.resolve("instance.cfg"));
            if(!"OneSix".equals(cfg.get("InstanceType")))throw new IOException("Seules les instances Minecraft modernes OneSix sont prises en charge.");
            var path=folder.resolve("mmc-pack.json");if(Files.size(path)>262144)throw new IOException("Composants trop volumineux");
            var j=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String mc=null,version="";ModLoader loader=ModLoader.VANILLA;var ids=new HashSet<String>();
            for(var value:j.getAsJsonArray("components")){
                var c=value.getAsJsonObject();String uid=c.get("uid").getAsString();if(!ids.add(uid))throw new IOException("Composant dupliqué");
                if(uid.equals("net.minecraft"))mc=c.get("version").getAsString();
                if(uid.contains("quilt")||uid.contains("liteloader"))throw new IOException("Ce modloader n’est pas pris en charge par Cookie Core : "+uid);
                ModLoader found=switch(uid){case "net.minecraftforge"->ModLoader.FORGE;case "net.neoforged"->ModLoader.NEOFORGE;case "net.fabricmc.fabric-loader"->ModLoader.FABRIC;default->null;};
                if(found!=null){if(loader!=ModLoader.VANILLA)throw new IOException("Plusieurs modloaders incompatibles");loader=found;version=c.get("version").getAsString();}
            }
            if(mc==null||mc.isBlank())throw new IOException("Version Minecraft absente");return new Components(mc,loader,version);
        }catch(RuntimeException e){throw new IOException("Export Prism/MultiMC incomplet ou invalide",e);}
    }
    static void extract(Path archive,Path target,OfficialInstance official,InstanceFiles.Progress progress) throws IOException {
        if(official!=null&&!InstanceFiles.hash(archive).equalsIgnoreCase(official.getSha256()))throw new IOException("Empreinte SHA-256 du paquet incorrecte.");
        Files.createDirectories(target);
        try(var zip=new ZipFile(archive.toFile())){
            var entries=Collections.list(zip.entries());if(entries.size()>30000)throw new IOException("Trop de fichiers dans l'archive");
            var names=new HashSet<String>();
            for(var e:entries){PrismLauncherService.validateEntryName(e.getName());if(!names.add(e.getName().toLowerCase(Locale.ROOT)))throw new IOException("Chemin ZIP dupliqué");}
            String prefix=PrismLauncherService.detectPrismExportPrefix(names,entries);if(prefix==null)throw new IOException("Un export Prism/MultiMC complet (instance.cfg et mmc-pack.json) est requis.");
            long total=0,count=0;
            for(var e:entries){
                if(!e.getName().startsWith(prefix))throw new IOException("Fichier extérieur à l'instance");String relative=e.getName().substring(prefix.length());if(relative.isEmpty())continue;
                String low=relative.toLowerCase(Locale.ROOT);
                if(Arrays.stream(low.split("/")).anyMatch(n->Set.of("accounts.json","launcher_accounts.json","launcher_profiles.json","prismlauncher.cfg","usercache.json","username-cache.json").contains(n))||low.contains(".cookiecore"))throw new IOException("Identifiants ou données de gestion interdits dans l'export");
                if(official!=null&&PrismLauncherService.containsPersonalData(relative))throw new IOException("Données personnelles interdites dans un pack officiel");
                Path file=InstanceFiles.child(target,relative.endsWith("/")?relative.substring(0,relative.length()-1):relative);
                if(e.isDirectory()){Files.createDirectories(file);continue;}Files.createDirectories(file.getParent());
                try(var in=zip.getInputStream(e);var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){
                    byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){total+=n;if(total>4L*1024*1024*1024)throw new IOException("Export trop volumineux (4 Gio maximum)");progress.report("Extraction du paquet",count,entries.size());out.write(bytes,0,n);}
                }progress.report("Extraction du paquet",++count,entries.size());
            }
        }
        describe(target);if(official!=null)PrismLauncherService.validateInstalledFiles(target,official);
        // Imported exports must not execute the author's custom shell commands.
        PrismConfig.write(target.resolve("instance.cfg"),Map.of("PreLaunchCommand","","PostExitCommand","","WrapperCommand","","uuid",UUID.randomUUID().toString()));
    }
}
