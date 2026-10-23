
package be.cookies.studio.core.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NewsCacheService {

    private final Path cacheFile;

    public NewsCacheService() {
        String localAppData = System.getenv("LOCALAPPDATA");

        Path baseDirectory;

        if (localAppData != null && !localAppData.isBlank()) {
            baseDirectory = Path.of(localAppData);
        } else {
            baseDirectory = Path.of(
                    System.getProperty("user.home"),
                    ".cookiecore"
            );
        }

        cacheFile = baseDirectory.resolve(
                Path.of("CookiesStudio", "CookieCore", "cache", "news.json")
        );
    }

    public String load() throws IOException {
        return Files.readString(
                cacheFile,
                StandardCharsets.UTF_8
        );
    }


    public void save(String json) throws IOException {

        Path directory = cacheFile.getParent();
        Files.createDirectories(directory);

        Path temporaryFile = Files.createTempFile(
                directory,
                "news-",
                ".tmp"
        );

        try {
            Files.writeString(
                    temporaryFile,
                    json,
                    StandardCharsets.UTF_8
            );

            CacheFiles.publish(temporaryFile, cacheFile, true);

        } finally {
            CacheFiles.cleanup(temporaryFile);
        }
    }

}


