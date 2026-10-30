package be.cookies.studio.core.model;

public class GameInstance {
    private int id;
    private String minecraftVersion;
    private ModLoader modLoader;
    private String loaderVersion;
    private String name;
    private String imagePath;

    public GameInstance(int id,String minecraftVersion, ModLoader modLoader, String loaderVersion, String name, String imagePath) {
        if (id <= 0){
            throw new IllegalArgumentException("l'identifiant de l'instance doit être suppérieur à 0.");
        }

        // Vérification du nom
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'instance ne peut pas être vide.");
        }
        // Vérification de la version Minecraft
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("La version Minecraft est obligatoire.");
        }
        // Vérification du modloader
        if (modLoader == null) {
            throw new IllegalArgumentException("Le modloader ne peut pas être null.");
        }
        // Gestion de la version du modloader
        if (modLoader == ModLoader.VANILLA) {
            this.loaderVersion = null;
        } else {
            if (loaderVersion == null || loaderVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "La version du modloader est obligatoire pour " + modLoader
                );
            }

            this.loaderVersion = loaderVersion;
        }
        // Initialisation des attributs
        this.id = id;
        this.minecraftVersion = minecraftVersion;
        this.modLoader = modLoader;
        this.name = name;
        this.imagePath = imagePath;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImagePath() {
        return imagePath;
    }

    public ModLoader getModLoader() {
        return modLoader;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'instance ne peut pas être vide.");
        }
        this.name = name;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}