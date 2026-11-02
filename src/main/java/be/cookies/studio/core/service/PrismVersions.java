package be.cookies.studio.core.service;
import be.cookies.studio.core.model.ModLoader;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Version relationships come from Prism's own metadata, not a guessed compatibility table. */
public final class PrismVersions {
    private static final Map<String,JsonArray> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    interface Source {JsonArray get(String uid) throws IOException,InterruptedException;}
    private final Source source;
    public PrismVersions(){source=null;}
    PrismVersions(Source source){this.source=source;}
    private static String uid(ModLoader loader){return switch(loader){case FORGE->"net.minecraftforge";case NEOFORGE->"net.neoforged";case FABRIC->"net.fabricmc.fabric-loader";default->"net.minecraft";};}
    private JsonArray index(String uid) throws IOException,InterruptedException {
        if(source!=null)return source.get(uid);
        if(CACHE.containsKey(uid))return CACHE.get(uid);
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(HttpRequest.newBuilder(URI.create("https://meta.prismlauncher.org/v1/"+uid+"/index.json")).timeout(Duration.ofSeconds(20)).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()){
            if(response.statusCode()!=200)throw new IOException("Liste des versions Prism indisponible");byte[] bytes=in.readNBytes(8_000_001);if(bytes.length>8_000_000)throw new IOException("Liste de versions trop volumineuse");
            var values=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("versions");if(values==null)throw new IOException("Métadonnées Prism invalides");CACHE.put(uid,values);return values;
        }catch(RuntimeException e){throw new IOException("Métadonnées Prism illisibles",e);}
    }
    public List<String> minecraft() throws IOException,InterruptedException {
        var releases=new ArrayList<String>();var previews=new ArrayList<String>();
        for(var v:index("net.minecraft")){var item=v.getAsJsonObject();String version=item.get("version").getAsString();
            if(!item.has("type")||"release".equals(item.get("type").getAsString()))releases.add(version);else previews.add(version);
        }
        releases.addAll(previews);return releases;
    }
    public List<String> loaders(String minecraft,ModLoader loader) throws IOException,InterruptedException {
        if(loader==ModLoader.VANILLA)return List.of("");
        if(loader==ModLoader.FABRIC){boolean supported=false;for(var v:index("net.fabricmc.intermediary"))if(v.getAsJsonObject().get("version").getAsString().equals(minecraft))supported=true;if(!supported)return List.of();}
        var result=new ArrayList<String>();
        for(var v:index(uid(loader))){var item=v.getAsJsonObject();boolean compatible=loader==ModLoader.FABRIC;
            var requires=item.getAsJsonArray("requires");if(requires!=null)for(var value:requires){var r=value.getAsJsonObject();if("net.minecraft".equals(r.get("uid").getAsString()))compatible=r.has("equals")&&minecraft.equals(r.get("equals").getAsString());}
            if(compatible)result.add(item.get("version").getAsString());
        }return result;
    }
    List<Map<String,Object>> components(String minecraft,ModLoader loader,String version) throws IOException,InterruptedException {
        if(!minecraft().contains(minecraft)||!loaders(minecraft,loader).contains(version))throw new IOException("Combinaison Minecraft/modloader non publiée par Prism.");
        var list=new ArrayList<Map<String,Object>>();list.add(Map.of("uid","net.minecraft","version",minecraft,"important",true));
        if(loader==ModLoader.FABRIC)list.add(Map.of("uid","net.fabricmc.intermediary","version",minecraft));
        if(loader!=ModLoader.VANILLA)list.add(Map.of("uid",uid(loader),"version",version));return list;
    }
}
