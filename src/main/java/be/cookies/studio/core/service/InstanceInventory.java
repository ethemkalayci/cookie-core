package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class InstanceInventory {
    static final String RECORD=".cookiecore-instance.json";
    final Path root,data;
    public InstanceInventory(){this(new PrismLauncherService().getDataDirectory().getParent());}
    public InstanceInventory(Path root){this.root=root.toAbsolutePath().normalize();data=this.root.resolve("prism-data");}
    Path entry(String id) throws IOException {if(!id.matches("cookiecore-(official-[1-9][0-9]*|personal-[a-f0-9-]{36})"))throw new IOException("Identifiant invalide");return root.resolve("inventory").resolve(id+".json");}
    public List<LocalInstance> list() throws IOException {
        var result=new ArrayList<LocalInstance>();Path folder=root.resolve("inventory");if(!Files.isDirectory(folder))return result;
        try(var paths=Files.list(folder)){for(var p:paths.filter(p->p.toString().endsWith(".json")).toList()){var record=InstanceFiles.readJson(p,LocalInstance.class);if(!entry(record.id()).equals(p))throw new IOException("Inventaire incohérent");result.add(record);}}
        result.sort(Comparator.comparing(LocalInstance::name,String.CASE_INSENSITIVE_ORDER));return result;
    }
    public LocalInstance get(String id) throws IOException {return InstanceFiles.readJson(entry(id),LocalInstance.class);}
    void save(LocalInstance record) throws IOException {InstanceFiles.writeJson(entry(record.id()),record);}
    void forget(String id) throws IOException {Files.deleteIfExists(entry(id));}
    static void stamp(LocalInstance record,Path folder) throws IOException {InstanceFiles.writeJson(folder.resolve(RECORD),record);}
    public Path verified(LocalInstance record) throws IOException {
        Path folder=record.folder();
        if(!Files.isDirectory(folder))throw new IOException("Emplacement indisponible : "+folder+". Rebranche le disque avant de réessayer.");
        if(!folder.toRealPath().equals(folder.toAbsolutePath().normalize()))throw new IOException("L'emplacement physique a été remplacé par un lien : opération refusée.");
        var onDisk=InstanceFiles.readJson(folder.resolve(RECORD),LocalInstance.class);
        if(!record.id().equals(onDisk.id())||!record.identity().equals(onDisk.identity()))throw new IOException("L'identité du dossier ne correspond pas à l'inventaire.");
        if(folder.equals(root)||folder.equals(data)||folder.equals(data.resolve("instances"))||folder.getParent()==null)throw new IOException("Dossier partagé interdit");
        return folder;
    }
    /** Discover only previous Cookie Core installations, never personal Prism directories. */
    public void discover() throws IOException {
        try(var lock=new InstanceOperationLock(root)){
            Path instances=data.resolve("instances");if(!Files.isDirectory(instances))return;
            try(var dirs=Files.list(instances)){for(Path alias:dirs.toList()){
                String id=alias.getFileName().toString();if(!id.matches("cookiecore-official-[1-9][0-9]*")||Files.exists(entry(id)))continue;
                if(!Files.isDirectory(alias))continue;Path folder=alias.toRealPath();
                if(Files.exists(folder.resolve(RECORD))){var r=InstanceFiles.readJson(folder.resolve(RECORD),LocalInstance.class);if(r.id().equals(id))save(r.at(folder));continue;}
                Path marker=folder.resolve(".cookiecore-install.properties");if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))continue;
                var p=new Properties();try(var in=Files.newInputStream(marker)){p.load(in);}
                try{
                    int catalog=Integer.parseInt(p.getProperty("catalogId"));if(!id.equals("cookiecore-official-"+catalog))continue;
                    var cfg=PrismConfig.read(folder.resolve("instance.cfg"));
                    var official=new OfficialInstance(catalog,p.getProperty("minecraftVersion"),p.getProperty("modpackVersion"),ModLoader.valueOf(p.getProperty("modLoader")),p.getProperty("loaderVersion"),cfg.getOrDefault("name",id),"","","");
                    var record=new LocalInstance(id,UUID.randomUUID().toString(),official.getName(),folder.toString(),official.getMinecraftVersion(),official.getModLoader(),official.getLoaderVersion(),"",official);
                    stamp(record,folder);save(record);
                }catch(RuntimeException e){throw new IOException("Installation ancienne illisible : "+id,e);}
            }}
        }
    }
}
