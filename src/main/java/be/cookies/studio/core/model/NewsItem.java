package be.cookies.studio.core.model;

import java.time.LocalDate;

public class NewsItem {
    private final String imageUrl;
    private final String categorie;
    private final String titre;
    private final String description;
    private final LocalDate publicationDate;
    private final String url;

    public NewsItem(String imageUrl, String categorie, String titre, String description, LocalDate publicationDate, String url){
        this.imageUrl = imageUrl;
        this.categorie = categorie;
        this.titre = titre;
        this.description = description;
        this.publicationDate = publicationDate;
        this.url = url;
    }

    public String getCategorie() {
        return categorie;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public String getTitre() {
        return titre;
    }

    public String getUrl() {
        return url;
    }
}
