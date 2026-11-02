package be.cookies.studio.core.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Presentation-only helpers. No data loading or theme persistence. */
public final class ViewSupport {
    private ViewSupport() {}

    public static void rounded(Region region,double radius) {
        Rectangle clip=new Rectangle();clip.setArcWidth(radius*2);clip.setArcHeight(radius*2);
        clip.widthProperty().bind(region.widthProperty());clip.heightProperty().bind(region.heightProperty());region.setClip(clip);
    }

    public static void hero(Region hero) {
        hero.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(360, Math.min(460, hero.getWidth() * .46)), hero.widthProperty()));
        hero.setMaxHeight(Region.USE_PREF_SIZE);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32); clip.setArcHeight(32);
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty());
        hero.setClip(clip);
    }

    public static void collapseWhenEmpty(Label label) {
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.managedProperty());
    }

    public static FadeTransition reveal(Node page) {
        FadeTransition fade = new FadeTransition(Duration.millis(160), page);
        fade.setFromValue(.65); fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT); fade.play();
        return fade;
    }

    public static void cardHover(Node card) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(140), card);
        transition.setInterpolator(Interpolator.EASE_OUT);
        card.hoverProperty().addListener((observable, oldValue, hovered) -> {
            transition.stop(); transition.setToY(hovered ? -2 : 0); transition.play();
        });
        card.sceneProperty().addListener((observable, before, after) -> {
            if (after == null) { transition.stop(); card.setTranslateY(0); }
        });
    }
}
