package be.cookies.studio.core.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Imports a private copy, so moving the original image does not break the preference. */
public final class BackgroundService {
    private BackgroundService() {}
    public static void choose(SettingsService settings, Path source) throws Exception {
        if(!Files.isRegularFile(source)||Files.size(source)>30_000_000)
            throw new IOException("Choisis une image PNG ou JPEG de moins de 30 Mo.");
        String extension=source.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if(!(extension.endsWith(".png")||extension.endsWith(".jpg")||extension.endsWith(".jpeg")))
            throw new IOException("Format attendu : PNG ou JPEG.");
        Path directory=settings.root().resolve("backgrounds");Files.createDirectories(directory);
        Path copy=directory.resolve(UUID.randomUUID()+ (extension.endsWith(".png")?".png":".jpg"));
        try {
            Files.copy(source,copy);
            if(ArtworkService.load(copy.toUri().toString()).get(30,java.util.concurrent.TimeUnit.SECONDS)==null)
                throw new IOException("Cette image ne peut pas être lue.");
            settings.update(current->current.background(copy.toUri().toString()));
        } catch(Exception error) { Files.deleteIfExists(copy);throw error; }
    }
    public static void reset(SettingsService settings) throws IOException { settings.update(current->current.background("")); }
}
