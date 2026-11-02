package be.cookies.studio.core.model;
public enum ThemeColor {
    BACKGROUND("Arrière-plans", "Fond principal"),
    SIDEBAR("Arrière-plans", "Barre latérale"),
    PANEL("Arrière-plans", "Panneaux secondaires"),
    CARD("Arrière-plans", "Cartes"),
    CONTENT("Arrière-plans", "Surfaces de contenu"),
    NAV_ACTIVE("Navigation", "Élément actif"),
    NAV_INACTIVE("Navigation", "Élément inactif"),
    NAV_HOVER("Navigation", "Survol navigation"),
    SELECTION("Navigation", "Sélection"),
    BUTTON("Boutons", "Fond des boutons"),
    BUTTON_TEXT("Boutons", "Texte des boutons"),
    BUTTON_HOVER("Boutons", "Survol des boutons"),
    BUTTON_DISABLED("Boutons", "Boutons désactivés"),
    PLAY("Boutons", "Bouton Jouer"),
    TEXT("Textes", "Texte principal"),
    TEXT_SECONDARY("Textes", "Texte secondaire"),
    TEXT_MUTED("Textes", "Texte atténué"),
    BORDER("Éléments visuels", "Bordures"),
    SEPARATOR("Éléments visuels", "Séparateurs"),
    ICON("Éléments visuels", "Icônes"),
    ACCENT("Éléments visuels", "Accent principal"),
    ACCENT_SECONDARY("Éléments visuels", "Accent secondaire"),
    ACCENT_TERTIARY("Éléments visuels", "Accent complémentaire"),
    PROGRESS("Éléments visuels", "Progression"),
    SUCCESS("Éléments visuels", "Réussite"),
    ERROR("Éléments visuels", "Erreur"),
    WARNING("Éléments visuels", "Avertissement"),
    BANNER_TEXT("Bannières", "Texte sur les images"),
    OVERLAY("Bannières", "Voile sur les images");
    public final String group, label;
    ThemeColor(String group, String label) { this.group=group; this.label=label; }
    public String css() { return "-cc-" + name().toLowerCase(java.util.Locale.ROOT).replace('_','-'); }
}
