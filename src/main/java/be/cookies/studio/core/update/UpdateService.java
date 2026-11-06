package be.cookies.studio.core.update;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public final class UpdateService {
    private final Path root;
    private final ReleaseHttp transport;
    private final Properties keys;
    public UpdateService(Path root) throws Exception { this(root,new ReleaseHttp(),ReleaseManifest.trustedKeys()); }
    UpdateService(Path root,ReleaseHttp transport,Properties keys) { this.root=root.toAbsolutePath().normalize();this.transport=transport;this.keys=keys; }
    public Path prepare(byte[] manifest,byte[] signature,Path archive,Properties keys,ReleaseVersion current) throws Exception {
        UpdateFiles.safe(root);
        var release=ReleaseManifest.verify(manifest,signature,keys);
        if(release.version().compareTo(current)<=0)throw new SecurityException("Downgrade ou version identique");
        if(Files.exists(root.resolve("rejected-"+release.version())))throw new SecurityException("Version précédemment rejetée");
        UpdateFiles.hash(archive,release);
        if(Files.getFileStore(root).getUsableSpace()<3L*1024*1024*1024)throw new java.io.IOException("3 Gio libres nécessaires");
        Path versions=root.resolve("versions");Files.createDirectories(versions);UpdateFiles.safe(versions);
        Path staging=versions.resolve("staging-"+UUID.randomUUID());
        UpdateFiles.extract(archive,staging);
        String embedded=Files.readString(staging.resolve("app/version.txt")).trim();
        if(!embedded.equals(release.version().value()))throw new SecurityException("Version embarquée divergente");
        Files.write(staging.resolve("release.json"),manifest,StandardOpenOption.CREATE_NEW);
        Files.write(staging.resolve("release.sig"),signature,StandardOpenOption.CREATE_NEW);
        Path destination=versions.resolve(release.version().value());UpdateFiles.safe(destination);
        Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);
        return destination;
    }
    public void check(ReleaseVersion current,Consumer<String> status,Runnable ready) throws Exception {
        UpdateFiles.safe(root);
        try(var channel=FileChannel.open(root.resolve("update.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=channel.tryLock()) {
            if(lock==null)throw new java.io.IOException("Mise à jour déjà en cours");
            status.accept("Recherche d’une mise à jour Cookie Core…");
            var http=transport;
            var base=ReleaseManifest.REPOSITORY+"latest/download/";
            byte[] bytes=http.bytes(java.net.URI.create(base+"release.json"),16384);
            byte[] signature=http.bytes(java.net.URI.create(base+"release.sig"),256);
            var manifest=ReleaseManifest.verify(bytes,signature,keys);
            if(manifest.version().compareTo(current)<=0){status.accept("Cookie Core "+current+" — à jour");return;}
            if(Files.getFileStore(root).getUsableSpace()<3L*1024*1024*1024)throw new java.io.IOException("3 Gio libres nécessaires");
            Path archive=root.resolve("download-"+UUID.randomUUID()+".zip");
            try {
                http.download(manifest.artifact(),archive,manifest.size(),n->status.accept("Téléchargement Cookie Core : "+(n*100/manifest.size())+" %"));
                status.accept("Vérification de la distribution…");
                Path prepared=prepare(bytes,signature,archive,keys,current);
                // Request is written only after complete verified immutable image exists.
                UpdateFiles.atomic(root.resolve("ready"),prepared.getFileName().toString());
                status.accept("Prêt à redémarrer — terminez les opérations en cours");ready.run();
            } finally {Files.deleteIfExists(archive);}
        }
    }
}

