package be.cookies.studio.core.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstanceOperationLockTest {
    @Test void unrelatedJavaApplicationsDoNotBlockInstanceOperations() {
        for(String main:new String[]{"be.cookies.studio.core.PackagedLauncher","org.gradle.launcher.daemon.bootstrap.GradleDaemon",
                "worker.org.gradle.process.internal.worker.GradleWorkerMain","com.intellij.idea.Main","com.example.Tool"})
            assertFalse(InstanceOperationLock.isMinecraftJava("C:/jdk/bin/java.exe",new String[]{"-Xmx1G",main}));
    }
    @Test void prismAndModdedMinecraftRemainProtected() {
        for(String main:new String[]{"org.prismlauncher.EntryPoint","org.multimc.EntryPoint","net.minecraft.client.main.Main",
                "net.minecraft.launchwrapper.Launch","net.fabricmc.loader.impl.launch.knot.KnotClient",
                "org.quiltmc.loader.impl.launch.knot.KnotClient","cpw.mods.bootstraplauncher.BootstrapLauncher",
                "cpw.mods.modlauncher.Launcher","net.neoforged.bootstrap.Bootstrap"})
            assertTrue(InstanceOperationLock.isMinecraftJava("C:/jdk/bin/JAVAW.EXE",new String[]{main}));
        assertTrue(InstanceOperationLock.isMinecraftJava("/usr/bin/java",new String[]{"custom.Launch","--gameDir","/games/test"}));
    }
    @Test void unreadableJavaProcessesRemainProtectedButOtherExecutablesDoNot() {
        assertTrue(InstanceOperationLock.isMinecraftJava("C:/jdk/bin/java.exe",new String[0]));
        assertFalse(InstanceOperationLock.isMinecraftJava("C:/tools/notjava.exe",new String[0]));
    }
}
