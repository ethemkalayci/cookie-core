package be.cookies.studio.core.service;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only projection of Prism format 3; never exports credentials. */
public final class PrismAccountService {
    public record Profile(String id, String name) {}
    private final Path file;
    public PrismAccountService(Path dataDirectory) { file = dataDirectory.resolve("accounts.json"); }
    public Optional<Profile> readSelected() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("Fichier de comptes Prism trop volumineux.");
        try (var reader = Files.newBufferedReader(file)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("formatVersion").getAsInt() != 3) throw new IOException("Format de comptes Prism non pris en charge.");
            var eligible = new ArrayList<Profile>();
            Profile selected = null;
            boolean hasActive = false;
            for (var element : root.getAsJsonArray("accounts")) {
                var account = element.getAsJsonObject();
                boolean active = account.has("active") && account.get("active").getAsBoolean();
                hasActive |= active;
                if (!"MSA".equals(account.get("type").getAsString())) continue;
                if (!account.has("profile") || !account.has("entitlement")) continue;
                var entitlement = account.getAsJsonObject("entitlement");
                if (!entitlement.has("canPlayMinecraft") || !entitlement.get("canPlayMinecraft").getAsBoolean()) continue;
                var profile = account.getAsJsonObject("profile");
                String id = profile.get("id").getAsString(), name = profile.get("name").getAsString();
                if (!id.matches("[0-9a-fA-F]{32}") || !name.matches("[A-Za-z0-9_]{1,16}")) continue;
                var value = new Profile(id, name);
                eligible.add(value);
                if (active) selected = value;
            }
            return Optional.ofNullable(selected != null ? selected : !hasActive && eligible.size() == 1 ? eligible.getFirst() : null);
        } catch (RuntimeException e) {
            // Do not attach Gson errors: they can include credential-bearing JSON.
            throw new IOException("Les comptes Prism sont illisibles. Réessaie après leur sauvegarde.");
        }
    }
}
