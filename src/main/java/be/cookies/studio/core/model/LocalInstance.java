package be.cookies.studio.core.model;
import java.nio.file.Path;

/** A stable local identity, independent of the remote catalog. */
public record LocalInstance(String id, String identity, String name, String location,
                            String minecraft, ModLoader loader, String loaderVersion,
                            String image, OfficialInstance official) {
    public LocalInstance {
        if (id == null || !id.matches("cookiecore-(official-[1-9][0-9]*|personal-[a-f0-9-]{36})")) throw new IllegalArgumentException("Identifiant invalide");
        java.util.UUID.fromString(identity);
        if(name == null || name.isBlank() || name.length()>100 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Nom invalide (1 à 100 caractères)");
        if(location == null || !Path.of(location).isAbsolute()) throw new IllegalArgumentException("Emplacement invalide");
        if(minecraft == null || minecraft.isBlank() || loader == null) throw new IllegalArgumentException("Version invalide");
        if(official != null && !id.equals("cookiecore-official-"+official.getId()))throw new IllegalArgumentException("Identité officielle incohérente");
        if(official == null && !id.startsWith("cookiecore-personal-"))throw new IllegalArgumentException("Type incohérent");
    }
    public Path folder(){return Path.of(location);}
    public LocalInstance at(Path path){
        String next=image;
        if(image!=null&&image.startsWith("file:"))try{Path old=Path.of(java.net.URI.create(image));if(old.startsWith(folder()))next=path.resolve(folder().relativize(old)).toUri().toString();}catch(RuntimeException ignored){}
        return new LocalInstance(id,identity,name,path.toString(),minecraft,loader,loaderVersion,next,official);
    }
    public LocalInstance renamed(String value){return new LocalInstance(id,identity,value,location,minecraft,loader,loaderVersion,image,official);}
    public LocalInstance illustrated(String value){return new LocalInstance(id,identity,name,location,minecraft,loader,loaderVersion,value,official);}
    public LocalInstance updated(OfficialInstance next){return new LocalInstance(id,identity,name,location,next.getMinecraftVersion(),next.getModLoader(),next.getLoaderVersion(),official!=null&&java.util.Objects.equals(image,official.getImagePath())?next.getImagePath():image,next);}
    @Override public String toString(){return name+" — "+(official==null?"Personnel":official.getModpackVersion())+" · "+minecraft;}
}
