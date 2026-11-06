package be.cookies.studio.core.update;

import com.google.gson.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Signature covers exact manifest bytes, never a reserialized JSON object. */
public record ReleaseManifest(ReleaseVersion version, URI artifact, long size, String sha256) {
    public static final long MAX_ARCHIVE=512L*1024*1024;
    public static final String REPOSITORY="https://github.com/ethemkalayci/cookie-core/releases/";
    public static ReleaseManifest verify(byte[] bytes, byte[] signature, Properties keys) throws Exception {
        if(bytes.length>16384 || signature.length>256) throw new SecurityException("Métadonnées trop grandes");
        JsonObject json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        String key=keys.getProperty(json.get("keyId").getAsString());
        if(key==null)throw new SecurityException("Clé éditeur inconnue ou révoquée");
        var verifier=Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));
        verifier.update(bytes);
        if(!verifier.verify(Base64.getDecoder().decode(signature)))throw new SecurityException("Signature éditeur invalide");
        if(json.get("schemaVersion").getAsInt()!=1 || json.get("bootstrapProtocol").getAsInt()!=1
            || !"stable".equals(json.get("channel").getAsString()) || !"windows-x64".equals(json.get("platform").getAsString()))
            throw new SecurityException("Release incompatible");
        var version=new ReleaseVersion(json.get("version").getAsString());
        URI uri=URI.create(json.get("artifact").getAsString());
        if(!uri.toString().equals(REPOSITORY+"download/v"+version+"/CookieCore-"+version+"-windows-x64.zip"))
            throw new SecurityException("URL artefact interdite");
        long size=json.get("size").getAsLong(); String hash=json.get("sha256").getAsString();
        if(size<=0 || size>MAX_ARCHIVE || !hash.matches("[a-f0-9]{64}"))throw new SecurityException("Taille ou empreinte invalide");
        return new ReleaseManifest(version,uri,size,hash);
    }
    public static Properties trustedKeys() throws Exception {
        var p=new Properties();
        try(var in=ReleaseManifest.class.getResourceAsStream("/update-keys.properties")){if(in!=null)p.load(in);}
        return p;
    }
}
