package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public final class InstanceContentService {
    public enum Kind { MODS("mods"),RESOURCE_PACKS("resourcepacks"),SHADERS("shaderpacks"); final String folder;Kind(String folder){this.folder=folder;} }
    public record Item(String file,String name,String version,String modId,boolean disabled,String origin,String warning,byte[] image){
        @Override public String toString(){return (disabled?"[Désactivé] ":"")+name+" · "+version+" — "+origin+(warning.isBlank()?"":" ⚠ "+warning);}
    }
    private final InstanceManager manager;
    public InstanceContentService(InstanceManager manager){this.manager=manager;}
    public Path directory(String id,Kind kind) throws IOException {
        Path root=manager.folder(id);Path game=PrismLauncherService.gameDirectory(root);if(Files.exists(game))InstanceFiles.ordinary(game);
        Path result=game.resolve(kind.folder);if(Files.exists(result))InstanceFiles.ordinary(result);return result;
    }
    public List<Item> list(String id,Kind kind) throws IOException {
        var local=manager.get(id);Path folder=directory(id,kind);if(!Files.isDirectory(folder))return List.of();
        var managed=PackManifest.read(local.folder());boolean legacy=local.official()!=null&&!Files.exists(local.folder().resolve(PackManifest.FILE));var result=new ArrayList<Item>();
        try(var files=Files.list(folder)){for(var file:files.sorted().toList()){
            InstanceFiles.ordinary(file);String name=file.getFileName().toString();String key=local.folder().relativize(file).toString().replace('\\','/');if(key.endsWith(".disabled"))key=key.substring(0,key.length()-9);
            String expected=managed.get(key);String origin=expected==null?(legacy?"Origine inconnue (ancien pack)":"Personnel"):(Files.isRegularFile(file)&&expected.equals(InstanceFiles.hash(file))?"Pack":"Pack modifié");
            result.add(inspect(file,kind,local,origin));
        }}
        Map<String,Long> counts=result.stream().filter(i->!i.modId().isBlank()&&!i.disabled()).collect(java.util.stream.Collectors.groupingBy(Item::modId,java.util.stream.Collectors.counting()));
        return result.stream().map(i->counts.getOrDefault(i.modId(),0L)>1?new Item(i.file(),i.name(),i.version(),i.modId(),i.disabled(),i.origin(),i.warning()+" Doublon d'identifiant de mod",i.image()):i).toList();
    }
    private static String string(JsonObject object,String key,String fallback){return object.has(key)&&object.get(key).isJsonPrimitive()?object.get(key).getAsString():fallback;}
    private static byte[] entry(ZipFile zip,String name,int limit) throws IOException {
        var e=zip.getEntry(name);if(e==null)return null;if(e.isDirectory()||e.getSize()>limit)throw new IOException("Métadonnées trop volumineuses");
        try(var in=zip.getInputStream(e)){byte[] b=in.readNBytes(limit+1);if(b.length>limit)throw new IOException("Métadonnées trop volumineuses");return b;}
    }
    private static String text(ZipFile zip,String name) throws IOException {byte[] data=entry(zip,name,262144);return data==null?null:new String(data,java.nio.charset.StandardCharsets.UTF_8);}
    private static String toml(String text,String key){var m=Pattern.compile("(?m)^\\s*"+Pattern.quote(key)+"\\s*=\\s*[\"']([^\"']+)").matcher(text);return m.find()?m.group(1):"";}
    static Item inspect(Path file,Kind kind,LocalInstance instance,String origin) throws IOException {
        String name=file.getFileName().toString(),version="Version non renseignée",modId="",warning="";byte[] image=null;
        if(Files.isDirectory(file))return new Item(name,name,"Dossier","",false,origin,"Métadonnées de dossier non analysées",null);
        try(var zip=new ZipFile(file.toFile())){
            if(kind==Kind.MODS){
                String fabric=text(zip,"fabric.mod.json");String forge=text(zip,"META-INF/mods.toml"),neo=text(zip,"META-INF/neoforge.mods.toml");
                if(fabric!=null){var json=JsonParser.parseString(fabric).getAsJsonObject();modId=string(json,"id","");name=string(json,"name",modId);version=string(json,"version",version);
                    if(instance.loader()!=ModLoader.FABRIC)warning="Mod Fabric dans une instance "+instance.loader();
                    var depends=json.getAsJsonObject("depends");if(depends!=null&&depends.has("minecraft")&&depends.get("minecraft").isJsonPrimitive()){
                        String required=depends.get("minecraft").getAsString();if(required.matches("[0-9]+(\\.[0-9]+)+")&&!required.equals(instance.minecraft()))warning+=" Minecraft attendu : "+required;
                    }
                    String icon=string(json,"icon","");if(!icon.isBlank())image=entry(zip,icon,2_000_000);
                }else if(forge!=null||neo!=null){String data=neo!=null?neo:forge;modId=toml(data,"modId");name=toml(data,"displayName");if(name.isBlank())name=file.getFileName().toString();version=toml(data,"version");
                    if(neo!=null&&instance.loader()!=ModLoader.NEOFORGE||neo==null&&instance.loader()!=ModLoader.FORGE)warning="Chargeur du mod à vérifier (Forge/NeoForge)";
                    String logo=toml(data,"logoFile");if(!logo.isBlank())image=entry(zip,logo,2_000_000);
                }else warning="Métadonnées absentes : compatibilité inconnue";
            }else if(kind==Kind.RESOURCE_PACKS){String meta=text(zip,"pack.mcmeta");if(meta==null)throw new IOException("pack.mcmeta absent");var pack=JsonParser.parseString(meta).getAsJsonObject().getAsJsonObject("pack");version="Format "+(pack.has("pack_format")?pack.get("pack_format").toString():"non indiqué");if(pack.has("description"))name=pack.get("description").isJsonPrimitive()?pack.get("description").getAsString():pack.get("description").toString();image=entry(zip,"pack.png",2_000_000);
            }else {if(zip.stream().noneMatch(e->e.getName().startsWith("shaders/")))throw new IOException("Dossier shaders/ absent");version="À activer dans Minecraft";image=entry(zip,"pack.png",2_000_000);}
        }catch(RuntimeException|IOException e){warning="Archive ou métadonnées illisibles : "+e.getMessage();}
        if(name.length()>300)name=name.substring(0,300);return new Item(file.getFileName().toString(),name,version,modId,file.getFileName().toString().endsWith(".disabled"),origin,warning,image);
    }
    public void add(String id,Kind kind,List<Path> files,InstanceFiles.Progress progress) throws IOException {
        try(var lock=new InstanceOperationLock(manager.root)){
            manager.requireIdle();manager.noPending(id);var local=manager.get(id);Path folder=directory(id,kind);Files.createDirectories(folder);
            var names=new HashSet<String>();
            for(Path source:files){InstanceFiles.ordinary(source);String name=source.getFileName().toString();if(!names.add(name.toLowerCase(Locale.ROOT))||InstanceFiles.exists(folder.resolve(name)))throw new IOException("Un contenu de ce nom existe déjà : "+name);
                if(kind==Kind.MODS&&!name.toLowerCase(Locale.ROOT).endsWith(".jar")||kind!=Kind.MODS&&!name.toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IOException("Extension incompatible : "+name);
                try(var zip=new ZipFile(source.toFile())){if(zip.size()==0)throw new IOException("Archive vide");if(kind==Kind.RESOURCE_PACKS&&zip.getEntry("pack.mcmeta")==null)throw new IOException("pack.mcmeta absent");if(kind==Kind.SHADERS&&zip.stream().noneMatch(e->e.getName().startsWith("shaders/")))throw new IOException("Archive de shaders invalide");}
            }
            var added=new ArrayList<Path>();try{
                for(Path source:files){Path target=folder.resolve(source.getFileName()),tmp=Files.createTempFile(folder,".adding-",".tmp");
                    try{Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);if(!InstanceFiles.hash(source).equals(InstanceFiles.hash(tmp)))throw new IOException("Copie incomplète");progress.report("Ajout des contenus",added.size(),files.size());Files.move(tmp,target);added.add(target);}finally{Files.deleteIfExists(tmp);}}
            }catch(IOException e){for(var file:added)Files.deleteIfExists(file);throw e;}
        }
    }
    private Path selected(String id,Kind kind,String name) throws IOException {if(name.isBlank()||name.contains("/")||name.contains("\\")||name.contains(":")||name.equals(".")||name.equals(".."))throw new IOException("Nom de fichier invalide");Path p=directory(id,kind).resolve(name);InstanceFiles.ordinary(p);return p;}
    public void toggle(String id,String name) throws IOException {
        try(var lock=new InstanceOperationLock(manager.root)){manager.requireIdle();manager.noPending(id);Path source=selected(id,Kind.MODS,name);
            if(!name.toLowerCase(Locale.ROOT).endsWith(".jar")&&!name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled"))throw new IOException("Seuls les mods JAR peuvent être activés ou désactivés");
            Path target=source.resolveSibling(name.endsWith(".disabled")?name.substring(0,name.length()-9):name+".disabled");if(InstanceFiles.exists(target))throw new IOException("La version activée/désactivée existe déjà");Files.move(source,target);}
    }
    public void remove(String id,Kind kind,String name) throws IOException {
        try(var lock=new InstanceOperationLock(manager.root)){manager.requireIdle();manager.noPending(id);Path source=selected(id,kind,name);if(Files.isDirectory(source))InstanceFiles.manifest(source,InstanceFiles.QUIET);
            Path backup=manager.folder(id).resolve(".cookiecore-content-backups").resolve(UUID.randomUUID().toString());Files.createDirectories(backup);Files.move(source,backup.resolve(name));}
    }
}
