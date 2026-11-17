package be.cookies.studio.core;

/** Non-Application entry point required by the packaged JavaFX classpath launcher.
 * Windows / Microsoft Store owns the lifecycle of the installed application. */
public final class PackagedLauncher {
    private PackagedLauncher() { }
    public static void main(String[] args) { CookieCoreApp.main(args); }
}