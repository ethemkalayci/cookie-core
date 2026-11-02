package be.cookies.studio.core.service;

import be.cookies.studio.core.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** All methods that touch disk run on workers. Original trees survive every publication. */
public final class InstanceManager {
    public enum ConflictPolicy { KEEP_LOCAL, USE_PACK }
    public enum State { INSTALLED, UPDATE_AVAILABLE, INCOMPLETE, MISSING, ERROR }
    public record Status(State state,String installed,String target,String message){}
    public record UpdatePlan(String operation,LocalInstance before,OfficialInstance target,Path work,
                             Map<String,String> snapshot,List<String> conflicts,boolean legacy){}
    public record Journal(String operation,String kind,String phase,LocalInstance before,LocalInstance after,
                          String backup,String work,String note){ @Override public String toString(){return before.name()+" · "+kind+" · "+phase+" · "+operation.substring(0,8);} }
    public record Last(String id,Instant at){}
    final Path root,data;final InstanceInventory inventory;private final RunnableCheck idle;private final PrismVersions versions;
    @FunctionalInterface interface RunnableCheck {void check() throws IOException;}
    public InstanceManager(){this(new PrismLauncherService().getDataDirectory().getParent());}
    public InstanceManager(Path root){this(root,()->InstanceOperationLock.requireIdle(root.resolve("prism-data")));}
    InstanceManager(Path root,RunnableCheck idle){this(root,idle,new PrismVersions());}
    InstanceManager(Path root,RunnableCheck idle,PrismVersions versions){this.versions=versions;this.root=root.toAbsolutePath().normalize();data=this.root.resolve("prism-data");inventory=new InstanceInventory(this.root);this.idle=idle;}
    void requireIdle() throws IOException {idle.check();}
    public List<LocalInstance> list() throws IOException {inventory.discover();return inventory.list();}
    public LocalInstance get(String id) throws IOException {return inventory.get(id);}
    public Path folder(String id) throws IOException {return inventory.verified(get(id));}
    Path alias(String id){return data.resolve("instances").resolve(id);}
    Path journalFile(String operation) throws IOException {if(!operation.matches("[a-f0-9-]{36}"))throw new IOException("Opération invalide");return root.resolve("operations").resolve(operation+".json");}
    void journal(Journal j) throws IOException {InstanceFiles.writeJson(journalFile(j.operation()),j);}
    public List<Journal> operations() throws IOException {
        Path dir=root.resolve("operations");if(!Files.isDirectory(dir))return List.of();var list=new ArrayList<Journal>();
        try(var paths=Files.list(dir)){for(var p:paths.filter(p->p.toString().endsWith(".json")).toList())list.add(InstanceFiles.readJson(p,Journal.class));}return list;
    }
    void noPending(String id) throws IOException {
        for(var j:operations())if(j.before().id().equals(id)&&!Set.of("COMPLETE","RESTORED","ABANDONED").contains(j.phase()))throw new IOException("Une opération interrompue attend une récupération : "+j.operation());
    }
    public Status status(LocalInstance local,OfficialInstance target) {
        try{
            Path path=inventory.verified(local);
            if(!Files.isRegularFile(path.resolve("instance.cfg"))||!Files.isRegularFile(path.resolve("mmc-pack.json")))return new Status(State.INCOMPLETE,"?",target==null?"?":target.getModpackVersion(),"Installation incomplète : profil Prism manquant");
            PrismArchive.describe(path);noPending(local.id());
            String current=local.official()==null?"Personnel":local.official().getModpackVersion();
            if(target!=null&&local.official()!=null&&(!current.equals(target.getModpackVersion())||
                    !readMarker(path).getProperty("sha256","").equalsIgnoreCase(target.getSha256())))return new Status(State.UPDATE_AVAILABLE,current,target.getModpackVersion(),"Mise à jour disponible");
            return new Status(State.INSTALLED,current,current,"Installée");
        }catch(IOException e){return new Status(Files.isDirectory(local.folder())?State.ERROR:State.MISSING,"?",target==null?"?":target.getModpackVersion(),e.getMessage());}
    }
    static Properties readMarker(Path folder) throws IOException {var p=new Properties();Path f=folder.resolve(".cookiecore-install.properties");if(Files.exists(f))try(var in=Files.newInputStream(f)){p.load(in);}return p;}
    static void officialMarker(Path folder,OfficialInstance i) throws IOException {
        Properties p=new Properties();p.setProperty("catalogId",""+i.getId());p.setProperty("modpackVersion",i.getModpackVersion());p.setProperty("sha256",i.getSha256());p.setProperty("minecraftVersion",i.getMinecraftVersion());p.setProperty("modLoader",i.getModLoader().name());p.setProperty("loaderVersion",Objects.toString(i.getLoaderVersion(),""));
        try(var out=Files.newOutputStream(folder.resolve(".cookiecore-install.properties"))){p.store(out,"Cookie Core official instance");}
    }
    static LocalInstance record(OfficialInstance i,Path folder){return new LocalInstance("cookiecore-official-"+i.getId(),UUID.randomUUID().toString(),i.getName(),folder.toAbsolutePath().normalize().toString(),i.getMinecraftVersion(),i.getModLoader(),i.getLoaderVersion(),Objects.toString(i.getImagePath(),""),i);}
    public UpdatePlan prepareUpdate(String id,OfficialInstance target,Path archive,InstanceFiles.Progress progress) throws IOException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();noPending(id);var before=get(id);if(before.official()==null||target.getId()!=before.official().getId())throw new IOException("Une instance personnelle ne reçoit jamais un pack officiel.");
            Path source=inventory.verified(before);String op=UUID.randomUUID().toString();Path work=source.resolveSibling(".cookiecore-work-"+id+"-"+op);Files.createDirectory(work);
            Path pack=work.resolve("incoming");
            try{
                PrismArchive.extract(archive,pack,target,progress);
                Path oldGame=PrismLauncherService.gameDirectory(source),newGame=PrismLauncherService.gameDirectory(pack);
                if(!oldGame.getFileName().equals(newGame.getFileName())&&Files.exists(newGame))Files.move(newGame,pack.resolve(oldGame.getFileName()));
                var previous=PackManifest.read(source);var next=PackManifest.capture(pack,progress);var snapshot=InstanceFiles.manifest(source,progress);var conflicts=new ArrayList<String>();
                for(String name:next.keySet()){
                    if(name.equals("mmc-pack.json"))continue;
                    String actual=snapshot.get(name);if(actual==null)actual=snapshot.get(name+".disabled");
                    if(actual!=null&&!Objects.equals(actual,previous.get(name))&&!actual.equals(next.get(name)))conflicts.add(name);
                    else if(actual==null&&previous.containsKey(name))conflicts.add(name+" (supprimé localement)");
                }
                boolean legacy=!Files.exists(source.resolve(PackManifest.FILE));
                journal(new Journal(op,"UPDATE","PREPARED",before,before.updated(target),"",work.toString(),legacy?"Ancienne installation sans manifeste : aucun fichier inconnu supprimé":"Conflits : "+conflicts.size()));
                return new UpdatePlan(op,before,target,work,Map.copyOf(snapshot),List.copyOf(conflicts),legacy);
            }catch(IOException e){InstanceFiles.deleteTree(work);throw e;}
        }
    }
    public void finishUpdate(UpdatePlan plan,ConflictPolicy policy,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();Path source=inventory.verified(get(plan.before().id()));
            if(!plan.snapshot().equals(InstanceFiles.manifest(source,progress)))throw new IOException("L'instance a changé pendant la préparation. Annule cette mise à jour et recommence.");
            var pending=InstanceFiles.readJson(journalFile(plan.operation()),Journal.class);validateJournal(pending);if(!pending.phase().equals("PREPARED"))throw new IOException("Préparation déjà utilisée");
            Path incoming=plan.work().resolve("incoming"),candidate=plan.work().resolve("candidate");
            if(InstanceFiles.exists(candidate))InstanceFiles.deleteTree(candidate);
            InstanceFiles.copyTree(source,candidate,progress);var old=PackManifest.read(source);var next=PackManifest.capture(incoming,progress);var local=plan.snapshot();
            // Delete only unmodified, previously managed files. Modified removed files remain personal.
            for(var e:old.entrySet())if(!next.containsKey(e.getKey())&&!PackManifest.personal(e.getKey())){
                for(String name:List.of(e.getKey(),e.getKey()+".disabled"))if(e.getValue().equals(local.get(name)))Files.deleteIfExists(InstanceFiles.child(candidate,name));
            }
            for(var e:next.entrySet()){
                String name=e.getKey();String actual=local.get(name);boolean disabled=false;
                if(actual==null&&name.toLowerCase(Locale.ROOT).endsWith(".jar")&&local.containsKey(name+".disabled")){actual=local.get(name+".disabled");disabled=true;}
                boolean modified=(actual!=null&&!Objects.equals(actual,old.get(name)))||(actual==null&&old.containsKey(name));
                if(modified&&policy==ConflictPolicy.KEEP_LOCAL&&!name.equals("mmc-pack.json"))continue;
                Path destination=InstanceFiles.child(candidate,name+(disabled?".disabled":""));
                if(Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Conflit fichier/dossier : "+name+". Ancienne instance conservée.");
                Files.createDirectories(destination.getParent());Files.copy(InstanceFiles.child(incoming,name),destination,StandardCopyOption.REPLACE_EXISTING);
            }
            // Always retain the player's instance.cfg, JVM choices and UUID.
            PackManifest.write(candidate,next);officialMarker(candidate,plan.target());var after=plan.before().updated(plan.target());InstanceInventory.stamp(after,candidate);PrismArchive.describe(candidate);
            commit(pending,candidate,after,progress);
        }
    }
    private void commit(Journal initial,Path candidate,LocalInstance after,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        Path source=initial.before().folder();Path backup=source.resolveSibling(".cookiecore-backup-"+initial.before().id()+"-"+initial.operation());
        idle.check();progress.report("Publication — ne ferme pas Cookie Core",0,-1);
        var j=new Journal(initial.operation(),initial.kind(),"SWITCHING",initial.before(),after,backup.toString(),initial.work(),initial.note());journal(j);
        Files.move(source,backup); // No deletion. A crash here leaves the complete old tree.
        if(initial.kind().equals("MOVE")){
            if(!alias(initial.before().id()).equals(source))InstanceStorage.unlink(alias(initial.before().id()),source);
            Files.move(candidate,after.folder());InstanceStorage.link(alias(after.id()),after.folder());
        }else Files.move(candidate,source);
        inventory.save(after);refreshLast(after);
        journal(new Journal(j.operation(),j.kind(),"COMPLETE",j.before(),after,j.backup(),j.work(),"Sauvegarde conservée : "+backup));
    }
    public void abandon(String operation) throws IOException {
        try(var lock=new InstanceOperationLock(root)){
            var j=InstanceFiles.readJson(journalFile(operation),Journal.class);validateJournal(j);
            if(!j.phase().equals("PREPARED"))throw new IOException("Cette opération doit être restaurée, pas annulée.");
            if(!j.work().isEmpty())InstanceFiles.deleteTree(Path.of(j.work()));journal(new Journal(j.operation(),j.kind(),"ABANDONED",j.before(),j.after(),j.backup(),j.work(),"Annulée sans modifier l'installation"));
        }
    }
    private void validateJournal(Journal j) throws IOException {
        journalFile(j.operation());Path source=j.before().folder();String suffix=j.before().id()+"-"+j.operation();
        if(!j.backup().isEmpty()&&!Path.of(j.backup()).equals(source.resolveSibling(".cookiecore-backup-"+suffix)))throw new IOException("Chemin de sauvegarde invalide");
        if(!j.work().isEmpty()&&!Path.of(j.work()).equals(source.resolveSibling(".cookiecore-work-"+suffix))&&
            !(j.kind().equals("MOVE")&&Path.of(j.work()).equals(j.after().folder().resolveSibling(".cookiecore-work-"+suffix))))throw new IOException("Chemin temporaire invalide");
    }
    public void restore(String operation) throws IOException,InterruptedException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();var j=InstanceFiles.readJson(journalFile(operation),Journal.class);validateJournal(j);
            if(j.phase().equals("PURGING"))throw new IOException("Suppression définitive interrompue : la sauvegarde est partielle. Récupère manuellement les fichiers restants dans "+j.backup());
            if(j.phase().equals("PREPARED")){throw new IOException("Utilise Annuler la préparation : l'original est intact.");}
            if(j.backup().isBlank())throw new IOException("Aucune sauvegarde publiée");
            Path backup=Path.of(j.backup());
            if(!InstanceFiles.exists(backup)&&Files.isDirectory(j.before().folder())){
                var original=InstanceFiles.readJson(j.before().folder().resolve(InstanceInventory.RECORD),LocalInstance.class);
                if(InstanceFiles.JSON.toJson(original).equals(InstanceFiles.JSON.toJson(j.before()))){
                    inventory.verified(j.before());PrismArchive.describe(j.before().folder());Path link=alias(j.before().id());
                    if(!InstanceFiles.exists(link))InstanceStorage.link(link,j.before().folder());
                    else if(!Files.isSameFile(link,j.before().folder()))throw new IOException("Lien Prism à vérifier manuellement : l'original est conservé");
                    inventory.save(j.before());journal(new Journal(j.operation(),j.kind(),"ABANDONED",j.before(),j.after(),j.backup(),j.work(),"Original intact ou déjà restauré ; opération débloquée"));return;
                }
            }
            var saved=InstanceFiles.readJson(backup.resolve(InstanceInventory.RECORD),LocalInstance.class);
            if(!saved.identity().equals(j.before().identity()))throw new IOException("Identité de sauvegarde invalide");
            Path current=j.after().folder(),original=j.before().folder(),link=alias(j.before().id());
            if(InstanceFiles.exists(link)&&!link.equals(current)&&!link.equals(original)){try{InstanceStorage.unlink(link,current);}catch(IOException wrongTarget){InstanceStorage.unlink(link,original);}}
            if(j.kind().equals("MOVE")&&InstanceFiles.exists(link)&&link.equals(original))InstanceStorage.unlink(link,current);
            if(Files.isDirectory(current)&&!current.equals(backup)){
                inventory.verified(j.after());Files.move(current,current.resolveSibling(".cookiecore-replaced-"+UUID.randomUUID()));
            }
            if(InstanceFiles.exists(original))throw new IOException("Le chemin original est occupé. Sauvegarde conservée : "+backup);
            Files.move(backup,original);InstanceStorage.link(link,original);inventory.save(j.before());refreshLast(j.before());
            journal(new Journal(j.operation(),j.kind(),"RESTORED",j.before(),j.after(),j.backup(),j.work(),"Version précédente restaurée ; version remplacée conservée séparément"));
        }
    }
    public void move(String id,Path destination,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();noPending(id);var before=get(id);Path source=inventory.verified(before);
            Path requested=destination.toAbsolutePath().normalize();Path ancestor=requested;while(!Files.exists(ancestor)&&ancestor.getParent()!=null)ancestor=ancestor.getParent();requested=ancestor.toRealPath().resolve(ancestor.relativize(requested));if(requested.startsWith(source))throw new IOException("La destination est contenue dans la source");
            Path parent=InstanceStorage.validate(destination.toString()).toRealPath();Path target=parent.resolve(id);
            boolean returningToPrism=target.equals(alias(id))&&!target.equals(source)&&Files.isDirectory(target)&&Files.isSameFile(target,source);
            if(parent.startsWith(source)||source.startsWith(target)||(InstanceFiles.exists(target)&&!returningToPrism)||parent.equals(data)||parent.equals(root))throw new IOException("Destination invalide, imbriquée ou déjà occupée");
            long bytes=0;for(String name:InstanceFiles.manifest(source,progress).keySet())bytes+=Files.size(InstanceFiles.child(source,name));
            if(Files.getFileStore(parent).getUsableSpace()<bytes+64*1024*1024)throw new IOException("Espace disponible insuffisant pour une copie vérifiée");
            String op=UUID.randomUUID().toString();Path work=parent.resolve(".cookiecore-work-"+id+"-"+op);Files.createDirectory(work);var after=before.at(target);
            var j=new Journal(op,"MOVE","PREPARED",before,after,"",work.toString(),"Copie vers "+target);journal(j);
            Path candidate=work.resolve("candidate");InstanceFiles.copyTree(source,candidate,progress);InstanceInventory.stamp(after,candidate);commit(j,candidate,after,progress);
        }
    }
    public void uninstall(String id,boolean keepBackup,InstanceFiles.Progress progress) throws IOException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();noPending(id);var before=get(id);Path source=inventory.verified(before);InstanceFiles.manifest(source,progress);
            String op=UUID.randomUUID().toString();Path backup=source.resolveSibling(".cookiecore-backup-"+id+"-"+op);
            var j=new Journal(op,"UNINSTALL","SWITCHING",before,before,backup.toString(),"","Désinstallation");journal(j);
            progress.report("Retrait de l'instance",0,-1);idle.check();Files.move(source,backup);
            if(!alias(id).equals(source))InstanceStorage.unlink(alias(id),source);
            inventory.forget(id);clearLast(id);
            journal(new Journal(op,j.kind(),"COMPLETE",before,before,backup.toString(),"","Sauvegarde : "+backup));
            if(!keepBackup){journal(new Journal(op,j.kind(),"PURGING",before,before,backup.toString(),"","Suppression définitive demandée ; ne pas restaurer automatiquement"));InstanceFiles.deleteTree(backup);journal(new Journal(op,j.kind(),"COMPLETE",before,before,"","","Suppression sans sauvegarde demandée"));}
        }
    }
    public LocalInstance create(String name,String minecraft,ModLoader loader,String version,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        var components=versions.components(minecraft,loader,version); // validates against official Prism metadata
        try(var lock=new InstanceOperationLock(root)){
            idle.check();Path staging=Files.createTempDirectory(root,".new-instance-");
            try{PrismConfig.write(staging.resolve("instance.cfg"),Map.of("InstanceType","OneSix","name",name,"uuid",UUID.randomUUID().toString()));InstanceFiles.writeJson(staging.resolve("mmc-pack.json"),Map.of("formatVersion",1,"components",components));Files.createDirectory(staging.resolve(".minecraft"));return publishPersonal(staging,name,progress);}finally{InstanceFiles.deleteTree(staging);}
        }
    }
    public LocalInstance importArchive(String name,Path archive,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();Path staging=Files.createTempDirectory(root,".import-instance-");
            try{PrismArchive.extract(archive,staging,null,progress);return publishPersonal(staging,name,progress);}finally{InstanceFiles.deleteTree(staging);}
        }
    }
    private LocalInstance publishPersonal(Path staged,String name,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        var components=PrismArchive.describe(staged);String id="cookiecore-personal-"+UUID.randomUUID();var settings=new SettingsService(root).current();
        Path parent=InstanceStorage.physicalRoot(data,settings.installDirectory());Files.createDirectories(parent);Path target=parent.toRealPath().resolve(id);
        var record=new LocalInstance(id,UUID.randomUUID().toString(),name,target.toString(),components.minecraft(),components.loader(),components.loaderVersion(),"",null);
        PrismConfig.write(staged.resolve("instance.cfg"),Map.of("name",name));MemorySettings.apply(staged,settings.memoryMiB(),false,data);InstanceInventory.stamp(record,staged);
        Path temp=Files.createTempDirectory(parent,".personal-");
        try{InstanceFiles.copyTree(staged,temp,progress);idle.check();Files.move(temp,target);inventory.save(record);InstanceStorage.link(alias(id),target);return record;}finally{InstanceFiles.deleteTree(temp);}
    }
    public void rename(String id,String name) throws IOException {
        try(var lock=new InstanceOperationLock(root)){idle.check();noPending(id);var r=get(id).renamed(name);Path path=inventory.verified(r);PrismConfig.write(path.resolve("instance.cfg"),Map.of("name",name));InstanceInventory.stamp(r,path);inventory.save(r);refreshLast(r);}
    }
    public void illustration(String id,Path image) throws IOException {
        try(var lock=new InstanceOperationLock(root)){
            idle.check();noPending(id);var r=get(id);Path path=inventory.verified(r);
            if(Files.size(image)>10_000_000)throw new IOException("Image supérieure à 10 Mo");
            java.awt.image.BufferedImage decoded;
            try(var input=javax.imageio.ImageIO.createImageInputStream(image.toFile())){
                var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Image invalide");var reader=readers.next();
                try{reader.setInput(input);long pixels=(long)reader.getWidth(0)*reader.getHeight(0);if(pixels>20_000_000)throw new IOException("Illustration trop grande (20 mégapixels maximum)");decoded=reader.read(0);}finally{reader.dispose();}
            }
            Path copy=path.resolve(".cookiecore-illustration-"+UUID.randomUUID()+".png");javax.imageio.ImageIO.write(decoded,"png",copy.toFile());r=r.illustrated(copy.toUri().toString());InstanceInventory.stamp(r,path);inventory.save(r);refreshLast(r);
        }
    }
    public void launch(String id) throws IOException {
        try(var lock=new InstanceOperationLock(root)){noPending(id);var r=get(id);Path folder=inventory.verified(r);PrismArchive.describe(folder);
            if(!Files.isDirectory(alias(id))||!Files.isSameFile(alias(id),folder))throw new IOException("Lien Prism absent. Utilise Rétablir le lien.");
            new PrismLauncherService(root).launchLocal(id,folder);InstanceFiles.writeJson(root.resolve("last-local-instance.json"),new Last(id,Instant.now()));
            if(r.official()!=null)new LastInstanceService(root).save(r.official(),Instant.now());
        }
    }
    public void configure(String id) throws IOException {try(var lock=new InstanceOperationLock(root)){noPending(id);Path folder=folder(id);if(!Files.isDirectory(alias(id))||!Files.isSameFile(alias(id),folder))throw new IOException("Lien Prism à rétablir avant configuration");new PrismLauncherService(root).showInstance(id);}}
    public void repairLink(String id) throws IOException,InterruptedException {try(var lock=new InstanceOperationLock(root)){idle.check();var r=get(id);Path folder=inventory.verified(r);InstanceStorage.link(alias(id),folder);}}
    public Optional<Last> last() throws IOException {Path file=root.resolve("last-local-instance.json");return Files.exists(file)?Optional.of(InstanceFiles.readJson(file,Last.class)):Optional.empty();}
    private void clearLast(String id) throws IOException {
        if(last().map(l->l.id().equals(id)).orElse(false))Files.deleteIfExists(root.resolve("last-local-instance.json"));
        var old=new LastInstanceService(root).load();if(old.isPresent()&&id.equals("cookiecore-official-"+old.get().instance().getId()))Files.deleteIfExists(root.resolve("last-instance.properties"));
    }
    private void refreshLast(LocalInstance r) throws IOException {
        var old=new LastInstanceService(root).load();if(r.official()!=null&&old.isPresent()&&r.official().getId()==old.get().instance().getId())new LastInstanceService(root).save(r.official(),old.get().launchedAt());
    }
}
