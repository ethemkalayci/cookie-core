package be.cookies.studio.core.model;

import java.time.LocalDate;

public class NewsItem {
    private final String imagePath;
    private final String categorie;
    private final String titre;
    private final String description;
    private final LocalDate publicationDate;

    public NewsItem(String imagePath,String categorie,String titre, String description,LocalDate publicationDate){
        this.imagePath = imagePath;
        this.categorie = categorie;
        this.titre = titre;
        this.description = description;
        this.publicationDate = publicationDate;
    }

    public String getCategorie() {
        return categorie;
    }

    public String getDescription() {
        return description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public String getTitre() {
        return titre;
    }
}
