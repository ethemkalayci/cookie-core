import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Offline release signing utility. Never generates or prints private keys. */
class SignRelease {
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("version archive output-directory key-id");
        String version=args[0],id=args[3];
        if(!version.matches("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})") || !id.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid version/key id");
        Path archive=Path.of(args[1]),out=Path.of(args[2]);Files.createDirectories(out);
        var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(archive)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}
        long size=Files.size(archive);if(size<=0 || size>512L*1024*1024)throw new IllegalArgumentException("Archive size");
        String json="{\"schemaVersion\":1,\"bootstrapProtocol\":1,\"keyId\":\""+id+"\",\"version\":\""+version+"\",\"channel\":\"stable\",\"platform\":\"windows-x64\",\"artifact\":\"https://github.com/ethemkalayci/cookie-core/releases/download/v"+version+"/CookieCore-"+version+"-windows-x64.zip\",\"size\":"+size+",\"sha256\":\""+HexFormat.of().formatHex(digest.digest())+"\"}\n";
        byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String privateValue=System.getenv("COOKIE_CORE_SIGNING_KEY");if(privateValue==null)throw new IllegalStateException("Missing COOKIE_CORE_SIGNING_KEY (PKCS8 base64)");
        var signature=Signature.getInstance("Ed25519");signature.initSign(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateValue))));signature.update(bytes);byte[] signed=signature.sign();
        var publicKeys=new Properties();publicKeys.load(new java.io.StringReader(System.getenv("COOKIE_CORE_UPDATE_KEYS")));
                String publicValue=publicKeys.getProperty(id);if(publicValue==null)throw new IllegalStateException("Public key id missing");
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            var embedded=new Properties();boolean foundVersion=false;
            try(var jar=new java.util.jar.JarInputStream(zip.getInputStream(zip.getEntry("app/cookie-core.jar")))) {
                for(java.util.jar.JarEntry entry;(entry=jar.getNextJarEntry())!=null;) {
                    if(entry.getName().equals("update-keys.properties"))embedded.load(new java.io.ByteArrayInputStream(jar.readAllBytes()));
                    if(entry.getName().equals("version.txt")) {foundVersion=true;if(!new String(jar.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim().equals(version))throw new SecurityException("Embedded version mismatch");}
                }
            }
            if(!foundVersion || !publicValue.equals(embedded.getProperty(id)))throw new SecurityException("Artifact trust/version differs from signing configuration");
        }
        signature.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue))));signature.update(bytes);
        if(!signature.verify(signed))throw new SecurityException("Signing key does not match embedded public key");
        Files.write(out.resolve("release.json"),bytes);Files.write(out.resolve("release.sig"),Base64.getEncoder().encode(signed));
    }
}

