
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.NewsItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NewsService {

    private static final String NEWS_URL =
            "https://ethemkalayci.github.io/cookie-core-data/news/news.json";

    private final NewsCacheService cacheService =
            new NewsCacheService();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NewsLoadResult loadNews()
            throws IOException, InterruptedException {

        try {
            String json = downloadNews();

            // Validation avant l'enregistrement
            List<NewsItem> news = parseNews(json);

            try {
                cacheService.save(json);
            } catch (IOException e) {
                // L'échec du cache ne doit pas empêcher
                // l'affichage des actualités téléchargées.
//                System.err.println(
//                        "Impossible d'enregistrer le cache : "
//                                + e.getMessage()
//                );
                e.printStackTrace();
            }

            return new NewsLoadResult(news, false);

        } catch (IOException networkError) {

            System.err.println(
                    "Actualités distantes indisponibles : "
                            + networkError.getMessage()
            );

            try {
                String cachedJson = cacheService.load();
                List<NewsItem> cachedNews = parseNews(cachedJson);

                return new NewsLoadResult(cachedNews, true);

            } catch (IOException cacheError) {

                networkError.addSuppressed(cacheError);
                throw networkError;
            }
        }
    }

    private String downloadNews() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NEWS_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Erreur HTTP : " + response.statusCode()
            );
        }

        return response.body();
    }

    private List<NewsItem> parseNews(String json)
            throws IOException {

        try {
            JsonArray array = JsonParser
                    .parseString(json)
                    .getAsJsonArray();

            List<NewsItem> news = new ArrayList<>();

            for (JsonElement element : array) {

                JsonObject object = element.getAsJsonObject();

                NewsItem item = new NewsItem(
                        object.get("imageUrl").getAsString(),
                        object.get("categorie").getAsString(),
                        object.get("titre").getAsString(),
                        object.get("description").getAsString(),
                        LocalDate.parse(
                                object.get("publicationDate").getAsString()
                        ),
                        object.get("url").getAsString(),
                        object.get("id").getAsInt()
                );

                news.add(item);
            }

            return news;

        } catch (RuntimeException e) {
            throw new IOException(
                    "Le fichier des actualités est invalide",
                    e
            );
        }
    }
}
