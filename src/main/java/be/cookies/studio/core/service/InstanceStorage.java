package be.cookies.studio.core.service;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class InstanceStorage {
    public static Path validate(String value) throws IOException {
        final Path path;
        try{path=Path.of(value).toAbsolutePath().normalize();}catch(RuntimeException e){throw new IOException("Chemin de dossier invalide.");}
        if(value.isBlank()||!Path.of(value).isAbsolute())throw new IOException("Choisis un chemin absolu.");
        Files.createDirectories(path);
        Path probe=Files.createTempFile(path,".cookiecore-write-",".tmp");Files.delete(probe);return path;
    }
    public static Path physicalRoot(Path data,String configured) throws IOException {
        return configured.isBlank()?data.resolve("instances"):validate(configured);
    }
    public static void link(Path alias, Path target) throws IOException, InterruptedException {
        if(alias.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))return;
        if(Files.exists(alias,LinkOption.NOFOLLOW_LINKS))throw new IOException("Le chemin Prism existe déjà : "+alias);
        Files.createDirectories(alias.getParent());
        if(System.getProperty("os.name").startsWith("Windows")){
            String script="$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path '"+alias.toString().replace("'","''")+"' -Target '"+target.toString().replace("'","''")+"' | Out-Null";
            String encoded=Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            var p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!p.waitFor(20,TimeUnit.SECONDS)||p.exitValue()!=0)throw new IOException("Impossible de relier le dossier à Prism. L'instance est conservée dans "+target);
        } else Files.createSymbolicLink(alias,target.toAbsolutePath());
        if(!Files.isSameFile(alias,target))throw new IOException("Le lien vers l'instance n'a pas pu être vérifié.");
    }
    static void unlink(Path alias,Path expected) throws IOException {
        if(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS))return;
        var attrs=Files.readAttributes(alias,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isSymbolicLink()&&!attrs.isOther())throw new IOException("Le chemin Prism n'est pas une jonction : "+alias);
        // A just-renamed target makes the junction dangling; its recorded target must still match.
        Path actual;
        try{actual=Files.readSymbolicLink(alias).toAbsolutePath().normalize();}
        catch(NotLinkException junction){
            if(!System.getProperty("os.name").startsWith("Windows"))throw junction;
            // Windows junctions are mount points, not Java symbolic links. Verify the reparse
            // target and remove only the directory entry, with a non-recursive .NET operation.
            String script="$ErrorActionPreference='Stop'; $linkPath='"+alias.toAbsolutePath().normalize().toString().replace("'","''")+"'; $expectedPath='"+expected.toAbsolutePath().normalize().toString().replace("'","''")+"'; "
                +"$item=Get-Item -LiteralPath $linkPath -Force; if($item.LinkType -ne 'Junction' -or $item.Target.Count -ne 1){throw 'Not a junction'}; "
                +"$actualPath=[IO.Path]::GetFullPath([string]$item.Target[0]); if($actualPath -ne $expectedPath){throw 'Unexpected target'}; [IO.Directory]::Delete($linkPath,$false)";
            String encoded=Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try{if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Délai de retrait de la jonction dépassé");}if(process.exitValue()!=0)throw new IOException("Jonction non retirée : vérifie sa cible avant récupération");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Retrait de jonction interrompu");}
            return;
        }
        if(!actual.equals(expected.toAbsolutePath().normalize()))throw new IOException("Cible de jonction inattendue");
        Files.delete(alias);
    }
}
