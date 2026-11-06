package be.cookies.studio.core.update;

import java.util.Set;

/** Audited worker names in the existing controllers; no changes to modpack/Prism logic. */
public final class ShutdownGate {
    private static final Set<String> WRITERS=Set.of("home-launch","settings-save","personal-instance-create");
    static boolean critical(String name) { return name.startsWith("cookiecore-prism-") || name.equals("cookiecore-instance-operation") || WRITERS.contains(name); }
    public static boolean busy() {return Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive() && critical(t.getName()));}
}
