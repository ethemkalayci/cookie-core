
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.OfficialInstance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class ModpackDownloadService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Path downloadModpack(OfficialInstance instance)
            throws IOException, InterruptedException {return downloadModpack(instance,InstanceFiles.QUIET);}
    public Path downloadModpack(OfficialInstance instance,InstanceFiles.Progress progress) throws IOException,InterruptedException {
        if (instance == null) {
            throw new IllegalArgumentException(
                    "L'instance officielle ne peut pas être null."
            );
        }

        if (!instance.isDownloadAvailable()) {
            throw new IllegalArgumentException(
                    "Aucun téléchargement disponible pour "
                            + instance.getName()
            );
        }

        Path tempFile = Files.createTempFile(
                "cookiecore-modpack-",
                ".zip"
        );

        boolean completed = false;

        try {
            URI downloadUri = URI.create(instance.getDownloadUrl());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(downloadUri)
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> response=httpClient.send(request,HttpResponse.BodyHandlers.ofInputStream());
            String actualSha256;
            try(var input=response.body()){
                if(response.statusCode()!=200)throw new IOException("Échec du téléchargement du modpack. HTTP : "+response.statusCode());
                long expected=response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if(expected>4L*1024*1024*1024)throw new IOException("Archive supérieure à 4 Gio");
                var digest=MessageDigest.getInstance("SHA-256");long received=0;byte[] buffer=new byte[65536];int count;
                var timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread thread=new Thread(r,"download-timeout");thread.setDaemon(true);return thread;});
                var timeout=timer.schedule(()->{try{input.close();}catch(IOException ignored){}},10,java.util.concurrent.TimeUnit.MINUTES);
                try(var output=Files.newOutputStream(tempFile)){
                    while((count=input.read(buffer))!=-1){received+=count;if(received>4L*1024*1024*1024)throw new IOException("Archive supérieure à 4 Gio");progress.report("Téléchargement de "+instance.getName(),received,expected);output.write(buffer,0,count);digest.update(buffer,0,count);}
                }finally{timeout.cancel(false);timer.shutdownNow();}
                if(expected>=0&&received!=expected)throw new IOException("Téléchargement incomplet");
                actualSha256=HexFormat.of().formatHex(digest.digest());progress.report("Vérification SHA-256",received,received);
            }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}

            // Comparaison avec l'empreinte du catalogue
            if (!actualSha256.equalsIgnoreCase(instance.getSha256())) {
                throw new IOException(
                        "L'intégrité du modpack "
                                + instance.getName()
                                + " n'a pas pu être vérifiée."
                );
            }

            completed = true;

            return tempFile;

        } finally {
            // Nettoyage si téléchargement ou vérification échoue
            if (!completed) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    // Le fichier temporaire pourra nécessiter
                    // un nettoyage ultérieur.
                }
            }
        }
    }

}
