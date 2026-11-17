package be.cookies.studio.core.service;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Cross-process lock shared by launch and all local mutations. */
final class InstanceOperationLock implements AutoCloseable {
    private final FileChannel channel;private final FileLock lock;
    InstanceOperationLock(Path root) throws IOException {
        Files.createDirectories(root);channel=FileChannel.open(root.resolve("instance-operations.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock value;
        try{value=channel.tryLock();}catch(OverlappingFileLockException e){channel.close();throw new IOException("Une opération sur les instances est déjà en cours.");}
        if(value==null){channel.close();throw new IOException("Une autre fenêtre Cookie Core travaille sur les instances.");}lock=value;
    }
    static void requireIdle(Path data) throws IOException {
        for(var process:ProcessHandle.allProcesses().toList()){
            if(process.pid()==ProcessHandle.current().pid())continue;
            var info=process.info();String command=info.command().orElse("").toLowerCase(Locale.ROOT);
            if(command.endsWith("prismlauncher.exe")||command.endsWith("/prismlauncher"))throw new IOException("Ferme Prism et Minecraft avant de modifier les fichiers, puis réessaie.");
            if(isMinecraftJava(command,info.arguments().orElse(new String[0])))
                throw new IOException(info.arguments().orElse(new String[0]).length==0
                    ? "Un processus Java ne peut pas être identifié. Ferme le jeu et les applications Java non nécessaires, puis réessaie."
                    : "Minecraft est actif. Ferme le jeu avant de modifier les instances, puis réessaie.");

        }
    }
    /** Classify the launch target, not every JVM (IDE, Gradle and tools also use Java). */
    static boolean isMinecraftJava(String command,String[] arguments) {
        String executable=command.replace('\\','/').toLowerCase(Locale.ROOT);
        if(!(executable.endsWith("/java.exe")||executable.endsWith("/javaw.exe")||executable.endsWith("/java")))return false;
        // An unreadable JVM command line cannot safely be classified as unrelated.
        if(arguments.length==0)return true;
        for(String argument:arguments) {
            String value=argument.toLowerCase(Locale.ROOT);
            if(value.equals("org.prismlauncher.entrypoint")||value.equals("org.multimc.entrypoint")
                    ||value.startsWith("net.minecraft.")||value.startsWith("net.fabricmc.loader.")
                    ||value.startsWith("org.quiltmc.loader.")||value.startsWith("cpw.mods.modlauncher.")
                    ||value.startsWith("cpw.mods.bootstraplauncher.")||value.startsWith("net.minecraftforge.bootstrap.")
                    ||value.startsWith("net.neoforged.bootstrap.")||value.equals("--gamedir"))return true;
        }
        return false;
    }
    public void close() throws IOException {try{lock.release();}finally{channel.close();}}
}
