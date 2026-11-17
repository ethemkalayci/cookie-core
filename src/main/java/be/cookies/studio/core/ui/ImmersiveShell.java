package be.cookies.studio.core.ui;

import be.cookies.studio.core.service.*;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Décor et verre sont séparés du contenu : aucun effet sur le texte. */
public final class ImmersiveShell extends BorderPane {
    private final Pane backdrop = new Pane();
    private ImageView current = new ImageView(), incoming = new ImageView();
    private final ImageView glassImage = new ImageView();
    private final Region shade = new Region(), glassTint = new Region();
    private final Pane glass = new Pane(glassImage, glassTint);
    private final Rectangle glassClip=new Rectangle(), backdropClip=new Rectangle();
    private long generation;
    private FadeTransition fade;
    private String source;
    private final java.util.function.Function<String,java.util.concurrent.CompletableFuture<Image>> loader;
    public ImmersiveShell() { this(ArtworkService::load); }
    ImmersiveShell(java.util.function.Function<String,java.util.concurrent.CompletableFuture<Image>> loader) {
        this.loader=loader;
        backdrop.setClip(backdropClip);
        backdrop.setManaged(false); backdrop.setMouseTransparent(true);
        glass.setManaged(false); glass.setMouseTransparent(true);
        shade.setManaged(false);glassTint.setManaged(false);
        shade.getStyleClass().add("world-shade"); glassTint.getStyleClass().add("glass-tint");
        current.setEffect(new GaussianBlur(18));incoming.setEffect(new GaussianBlur(18));
        current.setCache(true);incoming.setCache(true);
        glassImage.setEffect(new GaussianBlur(28));glassImage.setCache(true);
        glassClip.setArcWidth(32);glassClip.setArcHeight(32);glass.setClip(glassClip);
        backdrop.getChildren().addAll(current, incoming, shade);
        getChildren().addAll(backdrop, glass);
        sceneProperty().addListener((o,a,b) -> { if (b == null) { generation++; if(fade!=null)fade.stop(); } });
        showArtwork(null);
    }
    public void showArtwork(String path) {
        source = path; long request = ++generation;
        loader.apply(path).thenAccept(image -> Platform.runLater(() -> {
            if (request != generation) return;
            if (image == null) loader.apply(null).thenAccept(fallback -> Platform.runLater(() -> { if(request==generation) display(fallback); }));
            else display(image);
        }));
    }
    public String artworkSource() { return source; }
    public Image displayedImage() { return current.getImage(); }
    private void display(Image image) {
        if (image == null) return;
        if (fade != null) { fade.stop(); current.setImage(incoming.getImage()); current.setOpacity(1); fade=null; }
        if (current.getImage() == image) { incoming.setOpacity(0); return; }
        incoming.setImage(image); incoming.setOpacity(0); glassImage.setImage(image); requestLayout();
        fade = new FadeTransition(Duration.millis(220), incoming);
        fade.setToValue(1);
        fade.setOnFinished(e -> { current.setImage(image); incoming.setOpacity(0); fade=null; requestLayout(); });
        if (ThemeService.reducedMotion() || current.getImage()==null) { current.setImage(image); incoming.setOpacity(0); fade=null; }
        else fade.play();
    }
    public static Rectangle2D crop(Image image, double width, double height) {
        if(image==null || width<=0 || height<=0)return null;
        double w=image.getWidth(),h=image.getHeight(),ratio=width/height;
        return w/h>ratio ? new Rectangle2D((w-h*ratio)/2,0,h*ratio,h) : new Rectangle2D(0,(h-w/ratio)/2,w,w/ratio);
    }
    private void fit(ImageView view) {
        view.setFitWidth(getWidth()); view.setFitHeight(getHeight());
        view.setViewport(crop(view.getImage(),getWidth(),getHeight()));
    }
    @Override protected void layoutChildren() {
        super.layoutChildren();
        backdrop.resizeRelocate(0,0,getWidth(),getHeight());
        backdropClip.setWidth(getWidth());backdropClip.setHeight(getHeight());
        fit(current); fit(incoming); fit(glassImage);
        shade.resizeRelocate(0,0,getWidth(),getHeight());
        glass.setVisible(getLeft()!=null);
        if(getLeft()!=null) {
            var bounds=getLeft().getBoundsInParent();
            glass.resizeRelocate(bounds.getMinX(),bounds.getMinY(),bounds.getWidth(),bounds.getHeight());
            glassImage.relocate(-bounds.getMinX(),-bounds.getMinY());
            glassClip.setWidth(bounds.getWidth());glassClip.setHeight(bounds.getHeight());
            glassTint.resizeRelocate(0,0,bounds.getWidth(),bounds.getHeight());
        }
        glassImage.setVisible(!ThemeService.reducedTransparency());
    }
}
