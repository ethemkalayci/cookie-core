package be.cookies.studio.core.update;

import java.nio.channels.*;
import java.nio.file.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public final class UpdateUi {
    private static FileChannel channel;
    private static FileLock lock;
    private static boolean started;
    static Path managedRoot() throws Exception {
        String value=System.getenv("COOKIE_CORE_ROOT");if(value==null || !System.getProperty("os.name").startsWith("Windows") || !System.getProperty("os.arch").equals("amd64"))return null;
        Path root=Path.of(value).toAbsolutePath().normalize();UpdateFiles.safe(root);
        String version=Bootstrap.version(root,"current");
        Path code=Path.of(UpdateUi.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        if(!code.equals(root.resolve("versions").resolve(version).resolve("app/cookie-core.jar")))return null;
        return root;
    }
    public static boolean acquire() throws Exception {
        Path root=managedRoot();if(root==null)return true;
        channel=FileChannel.open(root.resolve("app.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=channel.tryLock();return lock!=null;
    }
    private static void scheduleRestart(Path root,javafx.scene.layout.BorderPane shell,Label label,Button later) {
        int[] seconds={10};var timer=new javafx.animation.Timeline();timer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        later.setText("Plus tard");later.setVisible(true);later.setManaged(true);
        later.setOnAction(e->{timer.stop();label.setText("Mise à jour prête — installation au prochain lancement");later.setVisible(false);later.setManaged(false);});
        timer.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),e->{
            if(ShutdownGate.busy()){seconds[0]=10;label.setText("Mise à jour prête — attente de la fin des opérations en cours");return;}
            label.setText("Prêt à redémarrer — installation dans "+seconds[0]+" s");
            if(seconds[0]--<=0) {
                timer.stop();
                try {
                    // Executed on FX thread: no further user operations can start after this check.
                    if(shell.getCenter()!=null)shell.getCenter().setDisable(true);
                    if(shell.getLeft()!=null)shell.getLeft().setDisable(true);
                    String next=Bootstrap.version(root,"ready");UpdateFiles.atomic(root.resolve("activate"),next);
                    label.setText("Installation…");later.setDisable(true);Platform.exit();
                } catch(Exception error){label.setText("Installation impossible : "+error.getMessage());if(shell.getCenter()!=null)shell.getCenter().setDisable(false);if(shell.getLeft()!=null)shell.getLeft().setDisable(false);}
            }
        }));timer.play();
    }
    public static void attach(javafx.scene.layout.BorderPane shell) {
        Path root;
        try {root=managedRoot();}catch(Exception ignored){return;}
        String token=System.getenv("COOKIE_CORE_HEALTH");if(started || root==null || token==null)return;started=true;
        var label=new Label();label.setWrapText(true);var later=new Button();later.setVisible(false);later.setManaged(false);
        var bar=new HBox(12,label,later);bar.setStyle("-fx-padding: 8;");shell.setBottom(bar);
        Thread.ofVirtual().name("cookie-core-update").start(()->{
            try {
                String version;
                try(var in=UpdateUi.class.getResourceAsStream("/version.txt")){version=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();}
                var current=new ReleaseVersion(version);
                UpdateFiles.atomic(root.resolve("health-"+java.util.UUID.fromString(token)),version);
                if(Files.exists(root.resolve("notice"))) {String notice=Files.readString(root.resolve("notice"));Platform.runLater(()->label.setText(notice));Thread.sleep(5000);Files.deleteIfExists(root.resolve("notice"));}
                Runnable ready=()->Platform.runLater(()->scheduleRestart(root,shell,label,later));
                if(Files.exists(root.resolve("ready"))) {
                    String next=Bootstrap.version(root,"ready");Path candidate=root.resolve("versions").resolve(next);UpdateFiles.safe(candidate);
                    if(new ReleaseVersion(next).compareTo(current)>0 && !Files.exists(root.resolve("rejected-"+next))) {
                        var manifest=ReleaseManifest.verify(Files.readAllBytes(candidate.resolve("release.json")),Files.readAllBytes(candidate.resolve("release.sig")),ReleaseManifest.trustedKeys());
                        if(!manifest.version().value().equals(next))throw new SecurityException("Version préparée invalide");ready.run();return;
                    }
                    Files.delete(root.resolve("ready"));
                }
                new UpdateService(root).check(current,s->Platform.runLater(()->label.setText(s)),ready);
            } catch(Exception error){Platform.runLater(()->label.setText("Mise à jour indisponible — version locale utilisable : "+error.getMessage()));}
        });
    }
}
