package be.cookies.studio.core.update;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Stable protocol-1 supervisor. Holds installation lock throughout child lifetime. */
public final class Bootstrap {
    static String version(Path root,String file) throws Exception {return new ReleaseVersion(Files.readString(root.resolve(file)).trim()).value();}
    static void recover(Path root) throws Exception {
        if(!Files.exists(root.resolve("pending")))return;
        String failed=version(root,"pending"),previous=version(root,"previous");
        UpdateFiles.atomic(root.resolve("current"),previous);
        UpdateFiles.atomic(root.resolve("rejected-"+failed),"startup failed");
        UpdateFiles.atomic(root.resolve("notice"),"Retour à la version précédente : "+previous);
        Files.delete(root.resolve("pending"));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Installation root required");
        Path root=Path.of(args[0]).toAbsolutePath().normalize();UpdateFiles.safe(root);
        try(var channel=FileChannel.open(root.resolve("bootstrap.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=channel.tryLock()) {
            if(lock==null)return;
            // An orphan child from a crashed supervisor must exit before recovery.
            try(var appChannel=FileChannel.open(root.resolve("app.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var appLock=appChannel.tryLock()) {if(appLock==null)return;}
            recover(root);
            for(int attempts=0;attempts<3;attempts++) {
                String current=version(root,"current");
                Path image=root.resolve("versions").resolve(current);UpdateFiles.safe(image);
                String token=UUID.randomUUID().toString();Path health=root.resolve("health-"+token);
                ProcessBuilder builder=new ProcessBuilder(image.resolve("CookieCore.exe").toString());
                builder.environment().put("COOKIE_CORE_ROOT",root.toString());
                builder.environment().put("COOKIE_CORE_HEALTH",token);
                builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("application.log").toFile()));
                Process app;
                try { app=builder.start(); }
                catch(Exception error){if(Files.exists(root.resolve("pending"))){recover(root);continue;}throw error;}
                long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
                while(app.isAlive() && !Files.exists(health) && System.nanoTime()<deadline)Thread.sleep(200);
                boolean healthy=Files.exists(health) && Files.readString(health).equals(current);
                Files.deleteIfExists(health);
                if(!healthy && Files.exists(root.resolve("pending"))) {
                    app.destroy();if(!app.waitFor(10,TimeUnit.SECONDS)){app.destroyForcibly();if(!app.waitFor(10,TimeUnit.SECONDS))return;}
                    recover(root);continue;
                }
                if(healthy) { Files.deleteIfExists(root.resolve("pending")); UpdateFiles.atomic(root.resolve("confirmed"),current); }
                while(app.isAlive()) {
                    if(Files.exists(root.resolve("activate"))) {
                        if(!app.waitFor(45,TimeUnit.SECONDS)) {
                            Files.deleteIfExists(root.resolve("activate"));
                            UpdateFiles.atomic(root.resolve("notice"),"Installation annulée : Cookie Core ne s’est pas fermé. Relancez après la fin des opérations.");
                        }
                    } else Thread.sleep(300);
                }
                if(!Files.exists(root.resolve("activate")))return;
                String next=version(root,"activate");Files.delete(root.resolve("activate"));
                if(new ReleaseVersion(next).compareTo(new ReleaseVersion(current))<=0 || Files.exists(root.resolve("rejected-"+next)))return;
                Path candidate=root.resolve("versions").resolve(next);UpdateFiles.safe(candidate);
                var manifest=ReleaseManifest.verify(Files.readAllBytes(candidate.resolve("release.json")),Files.readAllBytes(candidate.resolve("release.sig")),ReleaseManifest.trustedKeys());
                if(!manifest.version().value().equals(next))throw new SecurityException("Activation invalide");
                UpdateFiles.atomic(root.resolve("previous"),current);
                UpdateFiles.atomic(root.resolve("pending"),next);
                UpdateFiles.atomic(root.resolve("current"),next);
            }
        }
    }
}

