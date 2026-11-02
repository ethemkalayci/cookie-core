package be.cookies.studio.core.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class PrismRuntimeTest {
 @TempDir Path dir;
 Path zip(String entry) throws Exception {
  Path file=dir.resolve("test.zip");
  try(var z=new ZipOutputStream(Files.newOutputStream(file))){z.putNextEntry(new ZipEntry(entry));z.write(new byte[]{1,2,3});z.closeEntry();}
  return file;
 }
 @Test void rejectsTraversal() throws Exception { Path target=Files.createDirectory(dir.resolve("out")); assertThrows(java.io.IOException.class,()->PrismRuntime.extract(zip("../escape"),target)); assertFalse(Files.exists(dir.resolve("escape"))); }
 @Test void rejectsWrongDigest() throws Exception { assertThrows(java.io.IOException.class,()->PrismRuntime.verify(zip("file"))); }
 @Test void preservesExistingFiles() throws Exception { Path target=Files.createDirectory(dir.resolve("out"));Files.writeString(target.resolve("file"),"original"); assertThrows(java.io.IOException.class,()->PrismRuntime.extract(zip("file"),target));assertEquals("original",Files.readString(target.resolve("file"))); }
 @Test void extractsNestedFiles() throws Exception {Path target=Files.createDirectory(dir.resolve("out"));PrismRuntime.extract(zip("nested/file"),target);assertEquals(3,Files.size(target.resolve("nested/file")));}
 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cookiecore.runtime.integration", matches="true") void installsOfficialRuntime() throws Exception {
  Path data=dir.resolve("prism-data");
  Path exe=PrismRuntime.ensure(data);
  assertTrue(Files.isRegularFile(exe));
  assertEquals(exe,PrismRuntime.ensure(data));
  var process=new ProcessBuilder(exe.toString(),"--version").redirectErrorStream(true).start();
  assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));
  assertEquals(0,process.exitValue());
  assertTrue(new String(process.getInputStream().readAllBytes()).contains("11.1.1"));
 }
}

