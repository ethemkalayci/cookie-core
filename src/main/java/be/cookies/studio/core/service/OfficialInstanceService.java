package be.cookies.studio.core.service;
import be.cookies.studio.core.model.*;
import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class OfficialInstanceService {
    static final String INSTANCE_URL="https://ethemkalayci.github.io/cookie-core-data/instances/instances.json";
    public record Catalog(List<OfficialInstance> instances,boolean offline,Instant savedAt,String message){}
    interface Fetch {String get() throws IOException,InterruptedException;}
    private final Path cache;private final Fetch fetch;
    public OfficialInstanceService(){this(new PrismLauncherService().getDataDirectory().getParent(),()->{
        var request=HttpRequest.newBuilder(URI.create(INSTANCE_URL)).timeout(Duration.ofSeconds(12)).GET().build();
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()){
            if(response.statusCode()!=200)throw new IOException("Catalogue HTTP "+response.statusCode());
            byte[] body=in.readNBytes(2_000_001);if(body.length>2_000_000)throw new IOException("Catalogue trop volumineux");return new String(body,java.nio.charset.StandardCharsets.UTF_8);
        }
    });}
    OfficialInstanceService(Path root,Fetch fetch){cache=root.resolve("official-catalog-cache.json");this.fetch=fetch;}
    public List<OfficialInstance> loadOfficialInstances() throws IOException,InterruptedException{return loadCatalog().instances();}
    public Catalog loadCatalog() throws IOException,InterruptedException {
        try{
            String body=fetch.get();var instances=parse(body);var envelope=new JsonObject();envelope.addProperty("savedAt",Instant.now().toString());envelope.add("catalog",JsonParser.parseString(body));
            InstanceFiles.writeJson(cache,envelope);return new Catalog(instances,false,Instant.parse(envelope.get("savedAt").getAsString()),"Catalogue à jour");
        }catch(IOException|RuntimeException failure){
            try{
                var saved=InstanceFiles.readJson(cache,JsonObject.class);var instances=parse(saved.get("catalog").toString());var date=Instant.parse(saved.get("savedAt").getAsString());
                return new Catalog(instances,true,date,"Hors ligne — dernier catalogue valide du "+date);
            }catch(Exception ignored){throw new IOException("Catalogue indisponible et aucun cache valide. Les installations locales restent accessibles.",failure);}
        }
    }
    /** Read the validated last catalog without waiting for the network. Call off the FX thread. */
    public Optional<Catalog> cachedCatalog() {
        try {
            var saved=InstanceFiles.readJson(cache,JsonObject.class);
            var instances=parse(saved.get("catalog").toString());
            var date=Instant.parse(saved.get("savedAt").getAsString());
            return Optional.of(new Catalog(instances,true,date,"Catalogue enregistré"));
        } catch (Exception ignored) { return Optional.empty(); }
    }
    static List<OfficialInstance> parse(String body) throws IOException {
        try {
            // Analyse du JSON
            JsonElement root = JsonParser.parseString(body);

            if (!root.isJsonArray()) {
                throw new IOException(
                        "Format JSON invalide : un tableau est attendu."
                );
            }

            JsonArray instancesArray = root.getAsJsonArray();

            // Liste des instances officielles
            List<OfficialInstance> instances = new ArrayList<>();

            // Identifiants déjà rencontrés
            Set<Integer> ids = new HashSet<>();

            // Parcours du catalogue
            for (JsonElement element : instancesArray) {

                if (!element.isJsonObject()) {
                    throw new IOException(
                            "Format JSON invalide : une instance doit être un objet."
                    );
                }

                JsonObject object = element.getAsJsonObject();

                // Extraction des propriétés obligatoires
                int id = object.get("id").getAsInt();

                String name = object.get("name").getAsString();

                String minecraftVersion =
                        object.get("minecraftVersion").getAsString();

                ModLoader modLoader = ModLoader.valueOf(
                        object.get("modLoader").getAsString()
                );

                // Version du modloader, absente pour VANILLA
                String loaderVersion = null;

                if (object.has("loaderVersion")
                        && !object.get("loaderVersion").isJsonNull()) {

                    loaderVersion =
                            object.get("loaderVersion").getAsString();
                }

                // Image de l'instance
                String imagePath = null;

                if (object.has("imagePath")
                        && !object.get("imagePath").isJsonNull()) {

                    imagePath = object.get("imagePath").getAsString();
                }

                // Version du modpack
                String modpackVersion =
                        object.get("modpackVersion").getAsString();

                // URL de téléchargement
                String downloadUrl = "";

                if (object.has("downloadUrl")
                        && !object.get("downloadUrl").isJsonNull()) {

                    downloadUrl =
                            object.get("downloadUrl").getAsString();
                }

                // Empreinte SHA-256
                String sha256 = "";

                if (object.has("sha256")
                        && !object.get("sha256").isJsonNull()) {

                    sha256 = object.get("sha256").getAsString();
                }

                // Vérification des identifiants uniques
                if (!ids.add(id)) {
                    throw new IOException(
                            "Identifiant d'instance dupliqué : " + id
                    );
                }

                // Création de l'objet métier validé
                OfficialInstance instance = new OfficialInstance(
                        id,
                        minecraftVersion,
                        modpackVersion,
                        modLoader,
                        loaderVersion,
                        name,
                        imagePath,
                        downloadUrl,
                        sha256
                );

                // Ajout à la liste
                instances.add(instance);
            }

            return instances;

        } catch (RuntimeException e) {

            throw new IOException(
                    "Le catalogue des instances officielles est invalide.",
                    e
            );
        }
    }
}
