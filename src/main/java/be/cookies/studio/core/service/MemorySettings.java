package be.cookies.studio.core.service;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class MemorySettings {

    public record Target(Path folder,String label) { @Override public String toString(){return label;} }
    public static List<Target> targets(Path data) throws IOException {
        Path instances=data.resolve("instances");if(!Files.isDirectory(instances))return List.of();
        var result=new ArrayList<Target>();
        try(var folders=Files.list(instances)){
            for(var folder:folders.filter(Files::isDirectory).toList()) {
                if(!Files.isRegularFile(folder.resolve(".cookiecore-install.properties")))continue;
                var c=PrismConfig.read(folder.resolve("instance.cfg"));
                String memory=Boolean.parseBoolean(c.getOrDefault("OverrideMemory","false"))?c.getOrDefault("MaxMemAlloc","?")+" Mio":"défaut Prism";
                result.add(new Target(folder,c.getOrDefault("name",folder.getFileName().toString())+" — "+memory));
            }
        }return result;
    }
    public static void adoptDefault(Target target,int memory,Path data) throws IOException {
        if(memory==0)throw new IOException("Enregistre d'abord une allocation Cookie Core.");
        Path folder=target.folder().toAbsolutePath().normalize();
        if(!folder.getParent().equals(data.resolve("instances").toAbsolutePath().normalize())||!Files.isRegularFile(folder.resolve(".cookiecore-install.properties")))throw new IOException("Instance non gérée par Cookie Core.");
        if(prismRunning(data))throw new IOException("Ferme Prism avant de modifier la mémoire de cette instance.");
        Path backup=folder.resolve("instance.cfg.before-cookiecore-memory");
        if(!Files.exists(backup))Files.copy(folder.resolve("instance.cfg"),backup);
        apply(folder,memory,true,data);
    }
    public static long totalMiB(){var bean=ManagementFactory.getOperatingSystemMXBean();return bean instanceof com.sun.management.OperatingSystemMXBean os?os.getTotalMemorySize()/1048576:0;}
    public static long freeMiB(){var bean=ManagementFactory.getOperatingSystemMXBean();return bean instanceof com.sun.management.OperatingSystemMXBean os?os.getFreeMemorySize()/1048576:0;}
    public static int maxMiB(){long total=totalMiB();return (int)(total==0?4096:Math.max(1024,Math.min(32768,Math.min(total-2048,total*3/4))/256*256));}
    public static boolean prismRunning(Path data){
        for(var process:ProcessHandle.allProcesses().toList()){
            var info=process.info();String cmd=info.command().orElse("");
            if(!cmd.toLowerCase(Locale.ROOT).endsWith("prismlauncher.exe"))continue;
            String[] args=info.arguments().orElse(new String[0]);
            for(int i=0;i+1<args.length;i++)if((args[i].equals("--dir")||args[i].equals("-d"))&&Path.of(args[i+1]).toAbsolutePath().normalize().equals(data.toAbsolutePath().normalize()))return true;
            // Unknown arguments: conservatively protect the dedicated runtime's open process.
            if(args.length==0&&Path.of(cmd).equals(PrismRuntime.executable(data)))return true;
        }return false;
    }
    public static boolean apply(Path folder, int max, boolean fresh, Path data) throws IOException {
        if(max==0)return false;
        if(max<1024||max>maxMiB())throw new IOException("Mémoire sélectionnée incompatible avec cet ordinateur.");
        Path cfg=folder.resolve("instance.cfg");var c=PrismConfig.read(cfg);
        boolean override=Boolean.parseBoolean(c.getOrDefault("OverrideMemory","false"));
        boolean managed=Objects.equals(c.get("MaxMemAlloc"),c.get("CookieCoreMemoryMax"))
                && Objects.equals(c.get("MinMemAlloc"),c.get("CookieCoreMemoryMin")) && c.containsKey("CookieCoreMemoryMax");
        // Explicit per-instance choices win over the Cookie Core default.
        if(!fresh&&override&&!managed)return false;
        var updates=Map.of("OverrideMemory","true","MinMemAlloc","512","MaxMemAlloc",Integer.toString(max),
                "CookieCoreMemoryMax",Integer.toString(max),"CookieCoreMemoryMin","512");
        if(updates.entrySet().stream().allMatch(e->e.getValue().equals(c.get(e.getKey()))))return false;
        if(!fresh&&prismRunning(data))throw new IOException("Ferme la fenêtre Prism de Cookie Core avant de lancer avec la nouvelle allocation mémoire. Aucun réglage n'a été écrasé.");
        PrismConfig.write(cfg,updates);return true;
    }
}
