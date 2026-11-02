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
            if(command.endsWith("java.exe")||command.endsWith("javaw.exe")||command.endsWith("/java")){
                String args=String.join(" ",info.arguments().orElse(new String[0]));
                if(args.contains("GradleDaemon")||args.contains("GradleWorkerMain")||args.contains("GradleWrapperMain")||args.contains("org.jetbrains")||args.contains("com.intellij"))continue;
                throw new IOException("Un autre processus Java est actif : Minecraft pourrait utiliser cette instance. Ferme le jeu (et les autres applications Java si nécessaire), puis réessaie.");
            }
        }
    }
    public void close() throws IOException {try{lock.release();}finally{channel.close();}}
}
