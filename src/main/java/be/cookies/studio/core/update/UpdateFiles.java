package be.cookies.studio.core.update;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public final class UpdateFiles {
    public static void safe(Path path) throws IOException {
        for(Path p=path.toAbsolutePath();p!=null;p=p.getParent()) {
            if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)) {
                var a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink() || a.isOther())throw new IOException("Lien/reparse point interdit: "+p);
            }
        }
    }
    public static void atomic(Path path,String value) throws IOException {
        safe(path); Path temp=path.resolveSibling(path.getFileName()+".tmp");safe(temp);
        try(var c=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            c.write(java.nio.charset.StandardCharsets.UTF_8.encode(value));c.force(true);
        }
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public static void hash(Path zip,ReleaseManifest m) throws Exception {
        if(Files.size(zip)!=m.size())throw new SecurityException("Téléchargement tronqué");
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(zip)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}
        if(!HexFormat.of().formatHex(digest.digest()).equals(m.sha256()))throw new SecurityException("Empreinte invalide");
    }
    // Inspect central directory attributes: reject Unix links, devices and DOS reparse points.
    private static void inspectZip(Path zip) throws IOException {
        try(var f=new RandomAccessFile(zip.toFile(),"r")) {
            int length=(int)Math.min(f.length(),65557);byte[] tail=new byte[length];f.seek(f.length()-length);f.readFully(tail);
            var b=ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);int end=-1;
            for(int i=length-22;i>=0;i--)if(b.getInt(i)==0x06054b50 && i+22+Short.toUnsignedInt(b.getShort(i+20))==length){end=i;break;}
            if(end<0 || b.getShort(end+4)!=0 || b.getShort(end+6)!=0)throw new IOException("ZIP invalide/multivolume");
            int count=Short.toUnsignedInt(b.getShort(end+10));long offset=Integer.toUnsignedLong(b.getInt(end+16));
            if(count==65535 || count>20000 || offset>=f.length())throw new IOException("ZIP64 ou trop d'entrées");
            f.seek(offset);
            for(int i=0;i<count;i++) {
                byte[] h=new byte[46];f.readFully(h);var c=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
                if(c.getInt(0)!=0x02014b50 || (c.getShort(8)&1)!=0)throw new IOException("ZIP chiffré ou invalide");
                int mode=(c.getInt(38)>>>16)&0xf000;int dos=c.getInt(38)&0xffff;
                if((mode!=0 && mode!=0x8000 && mode!=0x4000) || (dos&0x400)!=0)throw new IOException("Fichier spécial interdit");
                f.seek(f.getFilePointer()+Short.toUnsignedInt(c.getShort(28))+Short.toUnsignedInt(c.getShort(30))+Short.toUnsignedInt(c.getShort(32)));
            }
        }
    }
    public static void extract(Path zip,Path target) throws IOException {
        extract(zip,target,2L*1024*1024*1024);
    }
    static void extract(Path zip,Path target,long expandedLimit) throws IOException {
        safe(target);inspectZip(zip);Files.createDirectory(target);
        var names=new HashSet<String>();long total=0;int count=0;
        try(var in=new ZipInputStream(Files.newInputStream(zip))) {
            for(ZipEntry entry;(entry=in.getNextEntry())!=null;) {
                if(++count>20000)throw new IOException("Trop d'entrées");
                String name=entry.getName();if(entry.isDirectory())name=name.substring(0,name.length()-1);
                if(name.isEmpty() || name.contains("\\") || name.startsWith("/") || !names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Chemin ZIP invalide/dupliqué");
                for(String part:name.split("/",-1)) {
                    if(!part.matches("[A-Za-z0-9_.() @+-]+") || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                        || part.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\..*)?"))throw new IOException("Nom Windows interdit");
                }
                Path out=target.resolve(name).normalize();if(!out.startsWith(target))throw new IOException("Zip Slip");safe(out);
                if(entry.isDirectory()){Files.createDirectories(out);continue;}Files.createDirectories(out.getParent());
                try(var stream=Files.newOutputStream(out,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;) {total+=n;if(total>expandedLimit)throw new IOException("ZIP décompressé trop grand");stream.write(buffer,0,n);}
                }
            }
        }
        for(String required:List.of("CookieCore.exe","runtime/bin/java.exe","app/cookie-core.jar"))
            if(!Files.isRegularFile(target.resolve(required)))throw new IOException("Distribution incomplète: "+required);
    }
}


