import be.cookies.studio.core.CookieCoreApp;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Test harness only; never included in the shipped jar or MSIX. */
public class AppImageSmoke {
    static Path output;
    static boolean fallback, passed, played;
    static Throwable failure;
    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]); Files.createDirectories(output);
        fallback = Boolean.parseBoolean(args[1]);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { failure=error; Platform.exit(); });
        Application.launch(Probe.class);
        if (failure != null) throw new AssertionError("Packaged runtime smoke failed", failure);
        if (!passed) throw new AssertionError("Application exited before verification");
        Files.writeString(output.resolve("result.txt"), "PASS: bundled Java/JavaFX, " +
            (fallback ? "missing-video fallback" : "MP4 playback and shell") + ", six navigation screens, clean exit\n");
    }
    /** Test-only agent: observes the unmodified native EXE / PackagedLauncher entry. */
    public static void premain(String directory) throws Exception {
        output=Path.of(directory); Files.createDirectories(output); fallback=false;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { Files.writeString(output.resolve("native-result.txt"),
                passed && failure==null ? "PASS: native CookieCore.exe, MP4, six screens, clean shutdown\n" : "FAIL: native launcher smoke\n"); }
            catch(Exception error) { error.printStackTrace(); }
        }));
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{ failure=error; Platform.exit(); });
        Thread.ofPlatform().daemon().start(() -> {
            for (int attempts=0;attempts<200;attempts++) {
                try { Platform.runLater(() -> new Probe().monitor(null)); return; }
                catch(IllegalStateException notReady) { try { Thread.sleep(100); } catch(InterruptedException interrupted) { return; } }
            }
        });
    }
    static void snapshot(javafx.scene.Parent root, String name) throws Exception {
        root.applyCss(); root.layout(); WritableImage image = root.snapshot(null, null);
        var png = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<png.getHeight();y++) for(int x=0;x<png.getWidth();x++) png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(png,"png",output.resolve(name+".png").toFile());
    }
    public static class Probe extends CookieCoreApp {
        Timeline timer;
        int ticks, navigation;
        javafx.scene.Node previousPage;
        final String[] pages={"accueilButton","instancesButton","vanillaButton","downloadsButton","settingsButton","accountButton"};
        @Override public void start(Stage stage) {
            super.start(stage);
            monitor(stage);
        }
        void monitor(Stage stage) {
            timer = new Timeline(new KeyFrame(Duration.millis(500), event -> {
                try {
                    if (++ticks > 80) throw new AssertionError("Shell/navigation timeout");
                    for (Window window : List.copyOf(Window.getWindows())) {
                        if (window.getScene()==null) continue;
                        var media = window.getScene().getRoot().lookupAll("MediaView");
                        for (var node : media) if (node instanceof MediaView view && view.getMediaPlayer()!=null) {
                            if (view.getMediaPlayer().getError()!=null) throw view.getMediaPlayer().getError();
                            if (!played && view.getMediaPlayer().getStatus()==MediaPlayer.Status.PLAYING && view.getMediaPlayer().getCurrentTime().toMillis()>0) {
                                played=true; snapshot(window.getScene().getRoot(),"intro");
                            }
                        }
                    }
                    Stage activeStage = stage != null ? stage : Window.getWindows().stream()
                        .filter(w -> w instanceof Stage && w.getScene()!=null && w.getScene().lookup("#accueilButton")!=null)
                        .map(w -> (Stage)w).findFirst().orElse(null);
                    if (activeStage == null) return;
                    if (!activeStage.isShowing() || activeStage.getScene()==null || activeStage.getScene().lookup("#accueilButton")==null) return;
                    if (!fallback && !played) throw new AssertionError("MP4 playback was not observed");
                    if (navigation > 0) {
                        var content = (javafx.scene.layout.BorderPane)activeStage.getScene().lookup("#contentContainer");
                        if (content.getCenter()==null || content.getCenter()==previousPage) throw new AssertionError("Page did not load: "+pages[navigation-1]);
                        previousPage=content.getCenter();
                        snapshot(activeStage.getScene().getRoot(), pages[navigation-1]);
                    }
                    if (navigation == pages.length) { passed=true; timer.stop(); Platform.exit(); return; }
                    var button = (Button)activeStage.getScene().lookup("#"+pages[navigation++]);
                    if (button==null || button.isDisabled()) throw new AssertionError("Navigation unavailable");
                    button.fire();
                } catch(Throwable error) { failure=error; timer.stop(); Platform.exit(); }
            }));
            timer.setCycleCount(Timeline.INDEFINITE); timer.play();
        }
    }
}