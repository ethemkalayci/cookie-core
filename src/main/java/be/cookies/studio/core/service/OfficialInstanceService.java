
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.ModLoader;
import be.cookies.studio.core.model.OfficialInstance;
import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OfficialInstanceService {

    private static final String INSTANCE_URL =
            "https://ethemkalayci.github.io/cookie-core-data/instances/instances.json";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<OfficialInstance> loadOfficialInstances()
            throws IOException, InterruptedException {

        // Construction de la requête HTTP
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INSTANCE_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        // Envoi de la requête
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        // Vérification du statut HTTP
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Erreur HTTP : " + response.statusCode()
            );
        }

        try {
            // Analyse du JSON
            JsonElement root = JsonParser.parseString(response.body());

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

        } catch (JsonParseException | IllegalArgumentException |
                 IllegalStateException e) {

            throw new IOException(
                    "Le catalogue des instances officielles est invalide.",
                    e
            );
        }
    }
}
