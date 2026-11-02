package be.cookies.studio.core.service;

import be.cookies.studio.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class InstanceLifecycleTest {
    @TempDir Path temp;
    Path root(){return temp.resolve("app");}
    InstanceManager manager(){return new InstanceManager(root(),()->{});}
    Map<String,String> files(){return new HashMap<>(Map.of("instance.cfg","InstanceType=OneSix\nname=Test\n", "mmc-pack.json","{\"formatVersion\":1,\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.20.1\"}]}",".minecraft/config/keep.txt","original",".minecraft/config/gone.txt","old"));}
    Path zip(Map<String,String> entries)throws IOException{Path p=Files.createTempFile(temp,"export-",".zip");try(var out=new ZipOutputStream(Files.newOutputStream(p))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();}}return p;}
    OfficialInstance official(Path zip,String version)throws IOException{return new OfficialInstance(1,"1.20.1",version,ModLoader.VANILLA,"","Test","","https://example.org/pack.zip",InstanceFiles.hash(zip));}
    LocalInstance installed()throws Exception{Path z=zip(files());new PrismLauncherService(root()).installModpack(official(z,"alpha"),z);return manager().list().getFirst();}
    void write(Path folder,String name,String value)throws IOException{Path p=folder.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,value);}
    @Test void updatePreservesWorldsAdditionsModifiedFilesAndRemovesOnlyUnmodifiedManagedFiles()throws Exception{
        var m=manager();var i=installed();Path f=i.folder();write(f,".minecraft/saves/world/level.dat","my world");write(f,".minecraft/mods/personal.jar","personal");write(f,".minecraft/config/keep.txt","my change");
        var next=files();next.remove(".minecraft/config/gone.txt");next.put(".minecraft/config/keep.txt","new default");next.put(".minecraft/config/new.txt","new");Path z=zip(next);
        var plan=m.prepareUpdate(i.id(),official(z,"release-z"),z,InstanceFiles.QUIET);assertTrue(plan.conflicts().contains(".minecraft/config/keep.txt"));
        m.finishUpdate(plan,InstanceManager.ConflictPolicy.KEEP_LOCAL,InstanceFiles.QUIET);
        assertEquals("my change",Files.readString(f.resolve(".minecraft/config/keep.txt")));assertFalse(Files.exists(f.resolve(".minecraft/config/gone.txt")));assertEquals("new",Files.readString(f.resolve(".minecraft/config/new.txt")));assertEquals("my world",Files.readString(f.resolve(".minecraft/saves/world/level.dat")));assertTrue(Files.exists(f.resolve(".minecraft/mods/personal.jar")));
        assertEquals("release-z",m.get(i.id()).official().getModpackVersion());assertEquals(InstanceManager.State.INSTALLED,m.status(m.get(i.id()),official(z,"release-z")).state());
        m.restore(plan.operation());assertEquals("alpha",m.get(i.id()).official().getModpackVersion());assertEquals("old",Files.readString(f.resolve(".minecraft/config/gone.txt")));assertEquals("my world",Files.readString(f.resolve(".minecraft/saves/world/level.dat")));
    }
    @Test void usePackPolicyKeepsBackupOfPersonalConflict()throws Exception{
        var m=manager();var i=installed();write(i.folder(),".minecraft/config/keep.txt","mine");var next=files();next.put(".minecraft/config/keep.txt","pack");Path z=zip(next);var plan=m.prepareUpdate(i.id(),official(z,"beta"),z,InstanceFiles.QUIET);m.finishUpdate(plan,InstanceManager.ConflictPolicy.USE_PACK,InstanceFiles.QUIET);
        assertEquals("pack",Files.readString(i.folder().resolve(".minecraft/config/keep.txt")));var j=m.operations().getFirst();assertEquals("mine",Files.readString(Path.of(j.backup()).resolve(".minecraft/config/keep.txt")));
    }
    @Test void legacyUpdatesNeverGuessOwnership()throws Exception{
        var m=manager();var i=installed();Files.delete(i.folder().resolve(PackManifest.FILE));var next=files();next.remove(".minecraft/config/gone.txt");Path z=zip(next);var plan=m.prepareUpdate(i.id(),official(z,"next"),z,InstanceFiles.QUIET);assertTrue(plan.legacy());m.finishUpdate(plan,InstanceManager.ConflictPolicy.KEEP_LOCAL,InstanceFiles.QUIET);assertTrue(Files.exists(i.folder().resolve(".minecraft/config/gone.txt")));
    }
    @Test void updateRejectsWrongHashAndDamagedArchiveWithoutChangingSource()throws Exception{
        var m=manager();var i=installed();var before=InstanceFiles.manifest(i.folder(),InstanceFiles.QUIET);Path z=zip(files());var bad=new OfficialInstance(1,"1.20.1","next",ModLoader.VANILLA,"","Test","","https://example.org/x.zip","a".repeat(64));assertThrows(IOException.class,()->m.prepareUpdate(i.id(),bad,z,InstanceFiles.QUIET));Path broken=temp.resolve("broken.zip");Files.writeString(broken,"not a zip");assertThrows(IOException.class,()->m.prepareUpdate(i.id(),official(broken,"next"),broken,InstanceFiles.QUIET));assertEquals(before,InstanceFiles.manifest(i.folder(),InstanceFiles.QUIET));
    }
    @Test void cancellationAndConcurrentChangesPreserveSourceAndRequireRecovery()throws Exception{
        var m=manager();var i=installed();Path z=zip(files());var before=InstanceFiles.manifest(i.folder(),InstanceFiles.QUIET);assertThrows(InterruptedIOException.class,()->m.prepareUpdate(i.id(),official(z,"next"),z,(s,d,t)->{throw new InterruptedIOException();}));assertEquals(before,InstanceFiles.manifest(i.folder(),InstanceFiles.QUIET));
        var plan=m.prepareUpdate(i.id(),official(z,"next"),z,InstanceFiles.QUIET);assertThrows(IOException.class,()->m.rename(i.id(),"Blocked"));write(i.folder(),".minecraft/personal.txt","new");assertThrows(IOException.class,()->m.finishUpdate(plan,InstanceManager.ConflictPolicy.KEEP_LOCAL,InstanceFiles.QUIET));m.abandon(plan.operation());assertEquals("new",Files.readString(i.folder().resolve(".minecraft/personal.txt")));
    }
    @Test void interruptedPublicationRestoresCompleteOriginal()throws Exception{
        var m=manager();var i=installed();Path z=zip(files());var plan=m.prepareUpdate(i.id(),official(z,"next"),z,InstanceFiles.QUIET);var j=m.operations().getFirst();Path backup=i.folder().resolveSibling(".cookiecore-backup-"+i.id()+"-"+j.operation());m.journal(new InstanceManager.Journal(j.operation(),j.kind(),"SWITCHING",j.before(),j.after(),backup.toString(),j.work(),"test interruption"));Files.move(i.folder(),backup);m.restore(j.operation());assertEquals("alpha",m.get(i.id()).official().getModpackVersion());assertTrue(Files.exists(i.folder().resolve("instance.cfg")));
    }
    @Test void personalImportPersistsStableIdentityAndSanitizesCommands()throws Exception{
        var f=files();f.put("instance.cfg","InstanceType=OneSix\nname=X\nPreLaunchCommand=unsafe\n");f.put(".minecraft/saves/World/level.dat","world");var m=manager();var i=m.importArchive("My world",zip(f),InstanceFiles.QUIET);m.rename(i.id(),"Renamed");var reloaded=manager().get(i.id());assertEquals(i.identity(),reloaded.identity());assertEquals("Renamed",reloaded.name());assertNull(reloaded.official());assertEquals("",PrismConfig.read(i.folder().resolve("instance.cfg")).get("PreLaunchCommand"));assertEquals("world",Files.readString(i.folder().resolve(".minecraft/saves/World/level.dat")));
        var z=zip(files());assertThrows(IOException.class,()->m.prepareUpdate(i.id(),official(z,"no"),z,InstanceFiles.QUIET));
    }
    @Test void unsafeArchiveAndNamesAreRejected()throws Exception{
        var f=files();f.put("../escape.txt","bad");assertThrows(IOException.class,()->manager().importArchive("Bad",zip(f),InstanceFiles.QUIET));assertFalse(Files.exists(root().resolve("escape.txt")));assertThrows(IllegalArgumentException.class,()->manager().importArchive("\n",zip(files()),InstanceFiles.QUIET));
    }
    @Test void moveWithJunctionPreservesFilesAndCanBeRestored()throws Exception{
        var m=manager();var i=installed();write(i.folder(),".minecraft/saves/world/data","world");Path destination=Files.createDirectory(temp.resolve("disk"));m.move(i.id(),destination,InstanceFiles.QUIET);var moved=m.get(i.id());assertTrue(Files.isSameFile(m.alias(i.id()),moved.folder()));assertEquals("world",Files.readString(moved.folder().resolve(".minecraft/saves/world/data")));var j=m.operations().getFirst();m.restore(j.operation());assertEquals(i.location(),m.get(i.id()).location());assertEquals("world",Files.readString(i.folder().resolve(".minecraft/saves/world/data")));
    }
    @Test void moveFromExternalJunctionAndUninstallOnlyItsInstance()throws Exception{
        var m=manager();var i=installed();m.move(i.id(),Files.createDirectory(temp.resolve("disk1")),InstanceFiles.QUIET);m.move(i.id(),Files.createDirectory(temp.resolve("disk2")),InstanceFiles.QUIET);Path actual=m.folder(i.id());m.uninstall(i.id(),true,InstanceFiles.QUIET);assertFalse(InstanceFiles.exists(m.alias(i.id())));assertFalse(Files.exists(actual));assertTrue(m.list().isEmpty());var uninstall=m.operations().stream().filter(j->j.kind().equals("UNINSTALL")).findFirst().orElseThrow();m.restore(uninstall.operation());assertTrue(Files.isSameFile(m.alias(i.id()),actual));
    }
    @Test void unsafeDestinationsAndConcurrentOperationsAreRejected()throws Exception{
        var m=manager();var i=installed();Path child=i.folder().resolve("bad/new");assertThrows(IOException.class,()->m.move(i.id(),child,InstanceFiles.QUIET));assertFalse(Files.exists(child));try(var lock=new InstanceOperationLock(root())){assertThrows(IOException.class,()->m.rename(i.id(),"No"));}assertEquals("Test",m.get(i.id()).name());
    }
    @Test void nestedJunctionIsNeverTraversedOrDeleted()throws Exception{
        var m=manager();var i=installed();Path outside=Files.createDirectory(temp.resolve("outside"));Files.writeString(outside.resolve("keep.txt"),"safe");Path link=i.folder().resolve("external");InstanceStorage.link(link,outside);try{assertThrows(IOException.class,()->m.uninstall(i.id(),false,InstanceFiles.QUIET));assertEquals("safe",Files.readString(outside.resolve("keep.txt")));}finally{InstanceStorage.unlink(link,outside);}
    }
    @Test void contentAddToggleRemoveMetadataAndDuplicateDetection()throws Exception{
        var m=manager();var i=m.importArchive("Personal",zip(files()),InstanceFiles.QUIET);var service=new InstanceContentService(m);Path jar=temp.resolve("example.jar");Files.move(zip(Map.of("fabric.mod.json","{\"id\":\"example\",\"name\":\"Example\",\"version\":\"1.2\",\"depends\":{\"minecraft\":\"1.19.4\"}}")),jar);service.add(i.id(),InstanceContentService.Kind.MODS,List.of(jar),InstanceFiles.QUIET);var item=service.list(i.id(),InstanceContentService.Kind.MODS).getFirst();assertEquals("Example",item.name());assertEquals("1.2",item.version());assertTrue(item.warning().contains("1.19.4"));service.toggle(i.id(),"example.jar");assertTrue(service.list(i.id(),InstanceContentService.Kind.MODS).getFirst().disabled());service.toggle(i.id(),"example.jar.disabled");Path second=temp.resolve("duplicate.jar");Files.copy(jar,second);service.add(i.id(),InstanceContentService.Kind.MODS,List.of(second),InstanceFiles.QUIET);assertTrue(service.list(i.id(),InstanceContentService.Kind.MODS).stream().allMatch(x->x.warning().contains("Doublon")));service.remove(i.id(),InstanceContentService.Kind.MODS,"example.jar");assertEquals(1,service.list(i.id(),InstanceContentService.Kind.MODS).size());assertThrows(IOException.class,()->service.remove(i.id(),InstanceContentService.Kind.MODS,"../instance.cfg"));
    }
    @Test void resourceAndShaderArchivesAreNotExtracted()throws Exception{
        var m=manager();var i=m.importArchive("Personal",zip(files()),InstanceFiles.QUIET);var service=new InstanceContentService(m);Path resource=zip(Map.of("pack.mcmeta","{\"pack\":{\"pack_format\":15,\"description\":\"Test pack\"}}"));service.add(i.id(),InstanceContentService.Kind.RESOURCE_PACKS,List.of(resource),InstanceFiles.QUIET);assertEquals("Test pack",service.list(i.id(),InstanceContentService.Kind.RESOURCE_PACKS).getFirst().name());Path shader=zip(Map.of("shaders/test.fsh","void main(){}"));service.add(i.id(),InstanceContentService.Kind.SHADERS,List.of(shader),InstanceFiles.QUIET);assertTrue(Files.isRegularFile(service.directory(i.id(),InstanceContentService.Kind.SHADERS).resolve(shader.getFileName())));assertThrows(IOException.class,()->service.add(i.id(),InstanceContentService.Kind.RESOURCE_PACKS,List.of(shader),InstanceFiles.QUIET));
    }
    @Test void catalogKeepsLastValidCacheOnNetworkOrValidationFailure()throws Exception{
        String good="[{\"id\":1,\"name\":\"Test\",\"minecraftVersion\":\"1.20.1\",\"modpackVersion\":\"arbitrary label\",\"modLoader\":\"VANILLA\"}]";var initial=new OfficialInstanceService(root(),()->good).loadCatalog();assertFalse(initial.offline());byte[] before=Files.readAllBytes(root().resolve("official-catalog-cache.json"));var offline=new OfficialInstanceService(root(),()->"{}").loadCatalog();assertTrue(offline.offline());assertEquals(initial.savedAt(),offline.savedAt());assertArrayEquals(before,Files.readAllBytes(root().resolve("official-catalog-cache.json")));assertTrue(new OfficialInstanceService(root(),()->{throw new IOException("Offline");}).loadCatalog().offline());assertThrows(IOException.class,()->new OfficialInstanceService(temp.resolve("empty"),()->"broken").loadCatalog());
    }

    @Test void createsVanillaAndOnlyPublishedLoaderCombinations()throws Exception{
        var versions=new PrismVersions(uid->com.google.gson.JsonParser.parseString(switch(uid){
            case "net.minecraft","net.fabricmc.intermediary"->"[{\"version\":\"1.20.1\"}]";
            case "net.fabricmc.fabric-loader"->"[{\"version\":\"0.16.0\"}]";
            default->"[{\"version\":\"47.1\",\"requires\":[{\"uid\":\"net.minecraft\",\"equals\":\"1.20.1\"}]}]";
        }).getAsJsonArray());
        var m=new InstanceManager(root(),()->{},versions);
        for(var loader:ModLoader.values()){
            String version=loader==ModLoader.VANILLA?"":loader==ModLoader.FABRIC?"0.16.0":"47.1";
            var i=m.create("Test "+loader,"1.20.1",loader,version,InstanceFiles.QUIET);
            assertEquals(loader,PrismArchive.describe(i.folder()).loader());assertEquals(i,manager().get(i.id()));
        }
        assertThrows(IOException.class,()->m.create("Wrong","1.19",ModLoader.FORGE,"47.1",InstanceFiles.QUIET));
        assertTrue(versions.loaders("1.19",ModLoader.FABRIC).isEmpty());assertTrue(versions.loaders("1.19",ModLoader.FORGE).isEmpty());
    }
    @Test void uninstallWithoutBackupDeletesOnlyVerifiedInstanceAndClearsHistory()throws Exception{
        var m=manager();var i=installed();Path sibling=i.folder().resolveSibling("unrelated");Files.createDirectory(sibling);Files.writeString(sibling.resolve("keep"),"safe");
        InstanceFiles.writeJson(root().resolve("last-local-instance.json"),new InstanceManager.Last(i.id(),java.time.Instant.now()));new LastInstanceService(root()).save(i.official(),java.time.Instant.now());
        m.uninstall(i.id(),false,InstanceFiles.QUIET);assertFalse(Files.exists(i.folder()));assertEquals("safe",Files.readString(sibling.resolve("keep")));assertTrue(m.last().isEmpty());assertTrue(new LastInstanceService(root()).load().isEmpty());assertThrows(IOException.class,()->m.restore(m.operations().getFirst().operation()));
    }
    @Test void activeGameGuardBlocksMutationsAndDiskDisconnectionIsExplicit()throws Exception{
        var i=installed();var m=new InstanceManager(root(),()->{throw new IOException("Ferme Minecraft");});assertThrows(IOException.class,()->m.uninstall(i.id(),false,InstanceFiles.QUIET));assertThrows(IOException.class,()->new InstanceContentService(m).toggle(i.id(),"missing.jar"));assertTrue(Files.exists(i.folder()));Files.move(i.folder(),i.folder().resolveSibling("disconnected"));assertEquals(InstanceManager.State.MISSING,m.status(i,null).state());assertTrue(m.status(i,null).message().contains("Rebranche"));
    }
    @Test void updatePreservesDisabledAndLocallyDeletedMods()throws Exception{
        var m=manager();var i=installed();Path folder=i.folder();
        write(folder,".minecraft/mods/disabled.jar","old");write(folder,".minecraft/mods/deleted.jar","old");PackManifest.write(folder,PackManifest.capture(folder,InstanceFiles.QUIET));Files.move(folder.resolve(".minecraft/mods/disabled.jar"),folder.resolve(".minecraft/mods/disabled.jar.disabled"));Files.delete(folder.resolve(".minecraft/mods/deleted.jar"));
        var next=files();next.put(".minecraft/mods/disabled.jar","new");next.put(".minecraft/mods/deleted.jar","new");Path z=zip(next);var plan=m.prepareUpdate(i.id(),official(z,"next"),z,InstanceFiles.QUIET);m.finishUpdate(plan,InstanceManager.ConflictPolicy.KEEP_LOCAL,InstanceFiles.QUIET);
        assertFalse(Files.exists(folder.resolve(".minecraft/mods/deleted.jar")));assertFalse(Files.exists(folder.resolve(".minecraft/mods/disabled.jar")));assertEquals("new",Files.readString(folder.resolve(".minecraft/mods/disabled.jar.disabled")));
    }

    @Test void movedIllustrationAndReturnToDefaultDirectoryRemainValid()throws Exception{
        var m=manager();var i=installed();Path image=temp.resolve("illustration.png");javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",image.toFile());m.illustration(i.id(),image);m.move(i.id(),Files.createDirectory(temp.resolve("external")),InstanceFiles.QUIET);assertTrue(Files.isRegularFile(Path.of(java.net.URI.create(m.get(i.id()).image()))));m.move(i.id(),root().resolve("prism-data/instances"),InstanceFiles.QUIET);assertEquals(i.folder(),m.folder(i.id()));assertTrue(Files.isRegularFile(Path.of(java.net.URI.create(m.get(i.id()).image()))));
    }

    @Test void failedRenameBeforePublicationCanBeRecoveredWithoutDeletingOriginal()throws Exception{
        var m=manager();var i=installed();Path z=zip(files());var plan=m.prepareUpdate(i.id(),official(z,"next"),z,InstanceFiles.QUIET);var j=m.operations().getFirst();Path backup=i.folder().resolveSibling(".cookiecore-backup-"+i.id()+"-"+j.operation());m.journal(new InstanceManager.Journal(j.operation(),j.kind(),"SWITCHING",j.before(),j.after(),backup.toString(),j.work(),"rename failed"));m.restore(j.operation());assertEquals(InstanceManager.State.INSTALLED,m.status(i,null).state());assertEquals("alpha",m.get(i.id()).official().getModpackVersion());
    }
}
