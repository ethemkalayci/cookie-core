package be.cookies.studio.core.controller;
import javafx.fxml.FXML;
public class DownloadsController {
    private Runnable browse=()->{};
    public void setBrowse(Runnable action){browse=action;}
    @FXML private void browseInstances(){browse.run();}
}
