
package be.cookies.studio.core.model;

public class OfficialInstance extends GameInstance {

    private final String modpackVersion;
    private final String downloadUrl;
    private final String sha256;

    public OfficialInstance(
            int id,
            String minecraftVersion,
            String modpackVersion,
            ModLoader modLoader,
            String loaderVersion,
            String name,
            String imagePath,
            String downloadUrl,
            String sha256
    ) {
        super(id, minecraftVersion, modLoader, loaderVersion, name, imagePath);

        if (modpackVersion == null || modpackVersion.isBlank()) {
            throw new IllegalArgumentException("La version du modpack est obligatoire.");
        }

        if (downloadUrl == null || sha256 == null) {
            throw new IllegalArgumentException(
                    "L'URL de téléchargement et le SHA-256 ne peuvent pas être null."
            );
        }

        boolean hasDownloadUrl = !downloadUrl.isBlank();
        boolean hasSha256 = !sha256.isBlank();

        if (hasDownloadUrl != hasSha256) {
            throw new IllegalArgumentException(
                    "L'URL de téléchargement et le SHA-256 doivent être renseignés ensemble."
            );
        }

        if (hasDownloadUrl) {
            java.net.URI uri;

            try {
                uri = java.net.URI.create(downloadUrl);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "L'URL de téléchargement est invalide.", e
                );
            }

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null) {
                throw new IllegalArgumentException(
                        "L'URL de téléchargement doit être une URL HTTPS valide."
                );
            }

            if (!sha256.matches("[a-fA-F0-9]{64}")) {
                throw new IllegalArgumentException(
                        "Le SHA-256 doit contenir exactement 64 caractères hexadécimaux."
                );
            }
        }

        this.modpackVersion = modpackVersion;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
    }

    public String getModpackVersion() {
        return modpackVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isDownloadAvailable() {
        return !downloadUrl.isBlank();
    }
}
