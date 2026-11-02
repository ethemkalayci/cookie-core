package be.cookies.studio.core.service;
import be.cookies.studio.core.model.ModLoader;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PrismVersionChoiceTest {
 @Test void stableMinecraftComesFirstWithoutHidingSnapshots()throws Exception{
  var versions=new PrismVersions(uid->JsonParser.parseString("[{\"version\":\"next-snapshot\",\"type\":\"snapshot\"},{\"version\":\"stable-new\",\"type\":\"release\"},{\"version\":\"stable-old\",\"type\":\"release\"}]").getAsJsonArray());
  assertEquals(List.of("stable-new","stable-old","next-snapshot"),versions.minecraft());
 }
 @Test void allCompatibleForgeVersionsRemainSelectable()throws Exception{
  var versions=new PrismVersions(uid->JsonParser.parseString("[{\"version\":\"47.4.28\",\"requires\":[{\"uid\":\"net.minecraft\",\"equals\":\"1.20.1\"}]},{\"version\":\"47.4.27\",\"requires\":[{\"uid\":\"net.minecraft\",\"equals\":\"1.20.1\"}]}]").getAsJsonArray());
  assertEquals(List.of("47.4.28","47.4.27"),versions.loaders("1.20.1",ModLoader.FORGE));assertTrue(versions.loaders("unknown-snapshot",ModLoader.FORGE).isEmpty());
 }
}
