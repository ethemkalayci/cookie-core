package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SettingsServiceTest {
 @TempDir Path root;
 @Test void defaultsAndEightExactPalettes(){
  assertEquals("AMETHYSTE",new SettingsService(root).current().activeTheme());assertEquals(8,Theme.values().length);
  String[] b={"#0E0B1D","#120C10","#0D0414","#11110D","#0B132B","#0F1F17","#0D1526","#140F14"};
  String[] a={"#5A3B8C","#BE3046","#FF00FF","#F4CE58","#5BC0DE","#6B7F67","#4DA3FF","#F39EC5"};
  for(int i=0;i<8;i++){assertEquals(b[i],Theme.values()[i].colors().get(ThemeColor.BACKGROUND));assertEquals(a[i],Theme.values()[i].colors().get(ThemeColor.ACCENT));assertEquals(ThemeColor.values().length,Theme.values()[i].colors().size());}
  assertEquals("#00FFFF",Theme.NEON.colors().get(ThemeColor.ACCENT_SECONDARY));assertEquals("#BAFF00",Theme.NEON.colors().get(ThemeColor.ACCENT_TERTIARY));assertEquals("Forêt",Theme.FORET.label);
 }
 @Test void customColorsRoundTripAndOfficialsImmutable() throws Exception {
  var service=new SettingsService(root);var colors=new EnumMap<ThemeColor,String>(Theme.AMETHYSTE.colors());colors.put(ThemeColor.BACKGROUND,"#123456");
  var theme=new LauncherSettings.CustomTheme("Mon thème",Theme.AMETHYSTE,colors);
  service.update(c->c.appearance("custom-one",Map.of("custom-one",theme)));
  service.update(c->c.minecraft(2048,root.resolve("packs").toString()));
  var read=new SettingsService(root).current();assertEquals("#123456",read.activeColors().get(ThemeColor.BACKGROUND));assertEquals(2048,read.memoryMiB());assertEquals("#0E0B1D",Theme.AMETHYSTE.colors().get(ThemeColor.BACKGROUND));
  assertThrows(UnsupportedOperationException.class,()->Theme.AMETHYSTE.colors().put(ThemeColor.TEXT,"#000000"));
 }
 @Test void invalidAndCorruptFallbackPreservesFile() throws Exception {
  Files.writeString(root.resolve("settings.json"),"corrupt");var service=new SettingsService(root);assertEquals("AMETHYSTE",service.current().activeTheme());assertEquals("corrupt",Files.readString(root.resolve("settings.json")));
  service.update(c->c);assertEquals("corrupt",Files.readString(root.resolve("settings.previous.json")));
 }
 @Test void deleteActiveCustomReturnsToOfficial() throws Exception {
  var s=new SettingsService(root);s.update(c->c.appearance("custom-a",Map.of("custom-a",new LauncherSettings.CustomTheme("A",Theme.RUBIS,Theme.RUBIS.colors()))));
  s.update(c->c.appearance("AMETHYSTE",Map.of()));assertEquals("AMETHYSTE",new SettingsService(root).current().activeTheme());
 }
 @Test void invalidHexAndContrast(){assertThrows(IllegalArgumentException.class,()->SettingsService.hex("red"));assertEquals(21,ThemeService.contrast("#000000","#FFFFFF"),.01);assertEquals(1,ThemeService.contrast("#222222","#222222"),.01);}
 @org.junit.jupiter.api.Test void legacyIdentifiersPreserveActiveAndCustomThemes() throws Exception {
  String[] legacy={"CORBAX","REDX","NAGIPI","ARTH","SPEED","CORBEAU","CHRIS","YASMINE"};
  for(int i=0;i<legacy.length;i++){
   var theme=Theme.values()[i];var service=new SettingsService(root);
   var colors=new EnumMap<ThemeColor,String>(theme.colors());colors.put(ThemeColor.TEXT,"#123456");
   service.update(c->new LauncherSettings(theme.name(),Map.of("custom-test",new LauncherSettings.CustomTheme("Personnel",theme,colors)),1024,root.toString()));
   var file=root.resolve("settings.json");
   String old=Files.readString(file).replace("\""+theme.name()+"\"","\""+legacy[i]+"\"");Files.writeString(file,old);
   var loaded=new SettingsService(root);var current=loaded.current();
   assertEquals(theme.name(),current.activeTheme());assertEquals(theme,current.customThemes().get("custom-test").base());
   assertEquals("#123456",current.customThemes().get("custom-test").colors().get(ThemeColor.TEXT));
   assertEquals(1024,current.memoryMiB());assertEquals(root.toString(),current.installDirectory());
   assertEquals(old,Files.readString(file));assertEquals("",loaded.warning());
   loaded.update(c->c.appearance("custom-test",c.customThemes()));
   assertFalse(Files.readString(file).contains("\""+legacy[i]+"\""));
   assertEquals("custom-test",new SettingsService(root).current().activeTheme());
   assertEquals("#123456",new SettingsService(root).current().activeColors().get(ThemeColor.TEXT));
  }
 }
}
