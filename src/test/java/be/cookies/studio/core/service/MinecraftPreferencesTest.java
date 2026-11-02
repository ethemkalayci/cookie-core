package be.cookies.studio.core.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MinecraftPreferencesTest {
 @TempDir Path root;
 @Test void writesRealPrismKeysAndKeepsPersonalSettings() throws Exception {
  Path cfg=root.resolve("instance.cfg");Files.writeString(cfg,"[General]\nname=MontDino\nOverrideMemory=false\nCustom=value\n[Other]\nMaxMemAlloc=777\n");
  assertTrue(MemorySettings.apply(root,2048,false,root.resolve("data")));
  var read=PrismConfig.read(cfg);assertEquals("true",read.get("OverrideMemory"));assertEquals("2048",read.get("MaxMemAlloc"));assertEquals("512",read.get("MinMemAlloc"));assertTrue(Files.readString(cfg).replace("\r\n","\n").contains("[Other]\nMaxMemAlloc=777"));
  assertTrue(MemorySettings.apply(root,3072,false,root.resolve("data")));assertEquals("3072",PrismConfig.read(cfg).get("MaxMemAlloc"));
  PrismConfig.write(cfg,Map.of("MaxMemAlloc","2560"));assertFalse(MemorySettings.apply(root,2048,false,root.resolve("data")));assertEquals("2560",PrismConfig.read(cfg).get("MaxMemAlloc"));
 }
 @Test void explicitOverrideWinsAndFreshImportsUseDefault() throws Exception {
  Path cfg=root.resolve("instance.cfg");Files.writeString(cfg,"[General]\nOverrideMemory=true\nMaxMemAlloc=4096\nMinMemAlloc=1024\n");
  assertFalse(MemorySettings.apply(root,2048,false,root));assertTrue(MemorySettings.apply(root,2048,true,root));assertEquals("2048",PrismConfig.read(cfg).get("MaxMemAlloc"));
 }
 @Test void disabledMemoryLeavesFileUntouched() throws Exception {Path c=root.resolve("instance.cfg");Files.writeString(c,"original");assertFalse(MemorySettings.apply(root,0,false,root));assertEquals("original",Files.readString(c));}
 @Test void generalSectionAfterOtherIsPreserved() throws Exception {Path c=root.resolve("instance.cfg");Files.writeString(c,"[Other]\nx=y\n[General]\nname=Hello\n");PrismConfig.write(c,Map.of("MaxMemAlloc","2048"));assertEquals("2048",PrismConfig.read(c).get("MaxMemAlloc"));assertTrue(Files.readString(c).replace("\r\n","\n").startsWith("[Other]\nx=y\n[General]"));}
 @Test void externalDirectoryActuallyLinkedAndOldDataUntouched() throws Exception {
  Path old=root.resolve("data/instances/old");Files.createDirectories(old);Files.writeString(old.resolve("save.dat"),"PLAYER DATA");
  Path external=InstanceStorage.validate(root.resolve("other disk/new packs").toString()).resolve("new");Files.createDirectories(external);Files.writeString(external.resolve("instance.cfg"),"[General]\nname=New");
  Path alias=root.resolve("data/instances/new");InstanceStorage.link(alias,external);
  assertTrue(Files.isSameFile(alias,external));assertTrue(Files.exists(alias.resolve("instance.cfg")));assertEquals("PLAYER DATA",Files.readString(old.resolve("save.dat")));
 }

 @Test void explicitAdoptionBacksUpInitialConfiguration() throws Exception {
  Path data=root.resolve("data"), folder=data.resolve("instances/example");Files.createDirectories(folder);
  Files.writeString(folder.resolve(".cookiecore-install.properties"),"catalogId=1");
  String original="[General]\nOverrideMemory=true\nMaxMemAlloc=6144\nMinMemAlloc=1024\n";
  Files.writeString(folder.resolve("instance.cfg"),original);
  MemorySettings.adoptDefault(new MemorySettings.Target(folder,"Example"),2048,data);
  assertEquals("2048",PrismConfig.read(folder.resolve("instance.cfg")).get("MaxMemAlloc"));
  assertEquals(original,Files.readString(folder.resolve("instance.cfg.before-cookiecore-memory")));
 }
}
