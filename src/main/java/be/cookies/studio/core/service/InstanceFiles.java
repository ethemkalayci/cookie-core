package be.cookies.studio.core.service;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** File operations never follow nested links. Only a previously verified root may be resolved. */
public final class InstanceFiles {
    static final Gson JSON=new GsonBuilder().registerTypeAdapter(java.time.Instant.class,new TypeAdapter<java.time.Instant>() {
        public void write(com.google.gson.stream.JsonWriter out,java.time.Instant value) throws IOException {out.value(value.toString());}
        public java.time.Instant read(com.google.gson.stream.JsonReader in) throws IOException {return java.time.Instant.parse(in.nextString());}
    }).setPrettyPrinting().create();
    public interface Progress { void report(String step,long done,long total) throws IOException; }
    public static final Progress QUIET=(s,d,t)->{};
    static boolean exists(Path p){return Files.exists(p,LinkOption.NOFOLLOW_LINKS);}
    static void ordinary(Path p) throws IOException {
        var a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(a.isSymbolicLink()||a.isOther())throw new IOException("Lien ou fichier spécial refusé : "+p);
    }
    static String hash(Path file) throws IOException {
        ordinary(file);
        try{
            var hash=MessageDigest.getInstance("SHA-256");
            try(var in=Files.newInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Opération interrompue avant publication.");hash.update(b,0,n);}}
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static Map<String,String> manifest(Path folder,Progress progress) throws IOException {
        ordinary(folder);var out=new TreeMap<String,String>();
        try(var walk=Files.walk(folder)){
            var paths=walk.toList();long count=0;
            for(var p:paths){ordinary(p);if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))out.put(folder.relativize(p).toString().replace('\\','/'),hash(p));progress.report("Vérification des fichiers",++count,paths.size());}
        }return out;
    }
    static Path child(Path root,String relative) throws IOException {
        if(relative.isBlank()||relative.contains("\\")||relative.contains(":")||relative.startsWith("/"))throw new IOException("Chemin relatif dangereux");
        for(var part:relative.split("/"))if(part.equals("..")||part.equals(".")||part.endsWith(".")||part.endsWith(" "))throw new IOException("Chemin dangereux : "+relative);
        Path p=root.resolve(relative).normalize();if(!p.startsWith(root)||p.equals(root))throw new IOException("Chemin hors instance");return p;
    }
    static void copyTree(Path from,Path to,Progress progress) throws IOException {
        var expected=manifest(from,progress);Files.createDirectories(to);long done=0;
        // Include empty world/content directories as well as files.
        try(var walk=Files.walk(from)){for(var p:walk.toList()){ordinary(p);if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(to.resolve(from.relativize(p)));}}
        for(var entry:expected.entrySet()){
            Path target=child(to,entry.getKey());Files.createDirectories(target.getParent());Files.copy(child(from,entry.getKey()),target,StandardCopyOption.COPY_ATTRIBUTES);
            progress.report("Copie vérifiée",++done,expected.size());
        }
        if(!expected.equals(manifest(to,progress))||!expected.equals(manifest(from,progress)))throw new IOException("Des fichiers ont changé pendant la copie. Source conservée.");
    }
    static void writeJson(Path file,Object value) throws IOException {
        Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),".write-",".tmp");
        try{Files.writeString(tmp,JSON.toJson(value));try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}CacheFiles.publish(tmp,file,true);}finally{Files.deleteIfExists(tmp);}
    }
    static <T> T readJson(Path file,Class<T> type) throws IOException {
        ordinary(file);if(Files.size(file)>16_000_000)throw new IOException("Fichier de gestion trop volumineux");
        try {T value=JSON.fromJson(Files.readString(file),type);if(value==null)throw new IllegalArgumentException();return value;}catch(RuntimeException e){throw new IOException("Fichier de gestion illisible : "+file.getFileName(),e);}
    }
    static void deleteTree(Path root) throws IOException {
        if(!exists(root))return;
        // Validate the whole tree before deleting its first entry.
        manifest(root,QUIET);
        try(var walk=Files.walk(root)){for(var p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    }
}
