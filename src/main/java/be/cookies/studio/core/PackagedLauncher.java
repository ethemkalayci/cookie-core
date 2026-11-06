package be.cookies.studio.core;
/** Non-Application entry point for the packaged JavaFX classpath launcher. */
public final class PackagedLauncher {
    public static void main(String[] args) throws Exception {
        if(!be.cookies.studio.core.update.UpdateUi.acquire())return;
        CookieCoreApp.main(args);
    }
}
