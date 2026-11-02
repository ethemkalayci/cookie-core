package be.cookies.studio.core.controller;

import be.cookies.studio.core.service.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** One connection session shared by all account pages; leaving the page cannot duplicate Prism. */
public class AccountController {
    private static final StringProperty status = new SimpleStringProperty("Connecte ton compte Minecraft avec Prism.");
    private static final BooleanProperty busy = new SimpleBooleanProperty(false);
    private static final AtomicBoolean cancelled = new AtomicBoolean(), finished = new AtomicBoolean();
    private static Thread worker;
    private static Process ownedWindow;
    private Runnable success = () -> {};
    public AccountController() { this(true); }
    AccountController(boolean load) { backgroundLoading = load; }
    private final boolean backgroundLoading;
    private boolean hasProfile;
    @FXML private Label profileName, profileBadge, profileInitial, profileDescription;
    @FXML private Button refreshButton;
    @FXML private Label statusLabel;
    @FXML private Button connectButton, finishButton, cancelButton;
    @FXML private ProgressIndicator progress;
    @FXML private javafx.scene.layout.FlowPane connectionActions;
    @FXML private void initialize() {
        statusLabel.textProperty().bind(status);
        be.cookies.studio.core.ui.ViewSupport.collapseWhenEmpty(statusLabel);
        connectButton.disableProperty().bind(busy);
        finishButton.disableProperty().bind(busy.not());
        cancelButton.disableProperty().bind(busy.not());
        connectionActions.visibleProperty().bind(busy);
        connectionActions.managedProperty().bind(busy);
        progress.visibleProperty().bind(busy);
        progress.managedProperty().bind(busy);
        finishButton.visibleProperty().bind(busy); finishButton.managedProperty().bind(busy);
        cancelButton.visibleProperty().bind(busy); cancelButton.managedProperty().bind(busy);
        refreshButton.disableProperty().bind(busy);
        displayProfile(Optional.empty());
        if (backgroundLoading) refresh();
    }
    public void setOnConnected(Runnable callback) { success = callback; }
    @FXML private void refresh() {
        var task = new javafx.concurrent.Task<Optional<PrismAccountService.Profile>>() {
            @Override protected Optional<PrismAccountService.Profile> call() throws Exception {
                return new PrismAccountService(new PrismLauncherService().getDataDirectory()).readSelected();
            }
        };
        task.setOnSucceeded(e -> displayProfile(task.getValue()));
        task.setOnFailed(e -> {
            displayProfile(Optional.empty());
            profileDescription.setText("Impossible de lire le compte. Ouvre Prism pour le vérifier, puis actualise cette page.");
        });
        Thread reader = new Thread(task, "account-profile"); reader.setDaemon(true); reader.start();
    }
    void displayProfile(Optional<PrismAccountService.Profile> profile) {
        hasProfile = profile.isPresent();
        profileBadge.getStyleClass().removeAll("success", "warning");
        profileBadge.getStyleClass().add(hasProfile ? "success" : "warning");
        profileName.setText(profile.map(PrismAccountService.Profile::name).orElse("Ton aventure t'attend"));
        profileInitial.setText(profile.map(p -> p.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT)).orElse("?"));
        profileBadge.setText(hasProfile ? "COMPTE MICROSOFT AJOUTÉ" : "AUCUN COMPTE SÉLECTIONNÉ");
        profileDescription.setText(hasProfile
                ? "Ton profil Minecraft est prêt pour tes prochaines aventures. Prism vérifiera ta session au lancement."
                : "Ajoute ton compte Microsoft dans Prism pour retrouver ton pseudo et lancer tes instances.");
        connectButton.setText(hasProfile ? "Gérer mon compte dans Prism" : "Se connecter avec Microsoft");
        if (!busy.get()) status.set("");
    }
    @FXML private void connect() {
        if (hasProfile) begin(this::refresh, true);
        else begin(success);
    }
    @FXML private void finish() { finished.set(true); }
    @FXML private void cancel() {
        cancelled.set(true);
        status.set("Annulation en cours…");
        if (worker != null) worker.interrupt();
    }
    public void begin(Runnable onSuccess) { begin(onSuccess, false); }
    private void begin(Runnable onSuccess, boolean managing) {
        if (busy.get()) return;
        busy.set(true); cancelled.set(false); finished.set(false);
        status.set("Préparation du moteur Prism dédié à Cookie Core…");
        worker = new Thread(() -> {
            var prism = new PrismLauncherService();
            var accounts = new PrismAccountService(prism.getDataDirectory());
            boolean connected = false;
            String result = "Connexion annulée.";
            try {
                var before = accounts.readSelected();
                var file = prism.getDataDirectory().resolve("accounts.json");
                var stamp = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
                prism.prepareRuntime();
                if (cancelled.get()) throw new InterruptedException();
                // Reuse a still-running process left from a previous session; never spawn another dialog.
                if (ownedWindow == null || !ownedWindow.isAlive()) ownedWindow = prism.openAccountWindow();
                Platform.runLater(() -> status.set(managing ? "Gère tes comptes dans Prism, puis clique sur Terminé. Choisis le compte par défaut pour changer de joueur." : "Dans Prism, ajoute ton compte Microsoft et termine la configuration si nécessaire."));
                Optional<PrismAccountService.Profile> previous = Optional.empty();
                int stable = 0;
                boolean observedRunning = false;
                for (int tick=0; tick<600; tick++) {
                    if (cancelled.get()) throw new InterruptedException();
                    Optional<PrismAccountService.Profile> current;
                    try { current = accounts.readSelected(); }
                    catch (java.io.IOException e) {
                        Platform.runLater(() -> status.set("En attente de la sauvegarde du compte par Prism…"));
                        Thread.sleep(1000); continue;
                    }
                    if (tick >= 2 && ownedWindow != null && ownedWindow.isAlive()) observedRunning = true;
                    if (managing && observedRunning && !ownedWindow.isAlive()) {
                        connected = true; result = "Gestion du compte terminée."; break;
                    }
                    stable = current.equals(previous) ? stable + 1 : 0; previous = current;
                    boolean saved = Files.exists(file) && !java.util.Objects.equals(stamp, Files.getLastModifiedTime(file));
                    boolean requested = finished.getAndSet(false);
                    if (current.isPresent() && stable >= 2 && (requested || (!managing && (saved || !current.equals(before))))) {
                        connected = true; result = "Compte sélectionné : " + current.get().name(); break;
                    }
                    if (requested) {
                        if (current.isPresent()) finished.set(true);
                        else Platform.runLater(() -> status.set("Aucun profil jouable dans le Prism de Cookie Core. Termine la connexion et choisis le compte par défaut dans cette fenêtre."));
                    }
                    // A CLI hand-off exits immediately: do not mistake it for cancellation.
                    if (tick > 5 && ownedWindow != null && !ownedWindow.isAlive() && current.isEmpty()) {
                        Platform.runLater(() -> status.set("En attente du compte. Si tu as fermé Prism, clique sur Annuler puis Réessayer."));
                    }
                    Thread.sleep(1000);
                }
                if (!connected) result = "Connexion non confirmée après dix minutes. Réessaie quand tu es prêt.";
            } catch (InterruptedException e) {
                result = "Connexion annulée.";
            } catch (Exception e) {
                result = "Connexion impossible : " + e.getMessage();
            } finally {
                // Clear interruption so the normal window-close request can complete.
                Thread.interrupted();
                try { prism.closeAccountWindow(ownedWindow); }
                catch (Exception ignored) { /* Never force-kill Prism or discard a saved profile. */ }
            }
            final boolean ok = connected && !cancelled.get();
            final String message = cancelled.get() ? "Connexion annulée." : result;
            Platform.runLater(() -> {
                busy.set(false); worker = null; status.set(message);
                if (ok) onSuccess.run();
            });
        }, "cookiecore-prism-account");
        worker.setDaemon(true); worker.start();
    }
}


