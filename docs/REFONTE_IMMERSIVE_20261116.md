# Refonte immersive Cookie Core — 16 novembre 2026

## Périmètre et préservation
Dépôt demandé : `C:/Users/kalay/Desktop/core`. Branche locale `design/liquid-glass-20261116`, sans commit, push ou publication. L’état initial comportait déjà la migration MSIX, des suppressions de l’ancien updater, un logo et une palette modifiés. Ces travaux sont conservés. Sauvegarde du source initial : `build/design-baseline/src-before.zip`.

Comparaison SHA-256 avec cette sauvegarde : CookieCoreApp (intro et transition), PackagedLauncher, Theme.java, PrismLauncherService, PrismRuntime, InstanceManager, InstanceContentService et CoreIntro.mp4 inchangés. Aucun changement de Gradle ni des outils Store dans cette mission.

## Direction et implémentation
Skill demandé installé depuis `dickwu/apple-design-skill`, puis lecture du SKILL.md et des références Liquid Glass, layout, typography, color, accessibility, sidebars, windows, modality, desktop et cross-platform. Les captures Rinaorc n’étaient pas attachées à la conversation ; aucune comparaison pixel à pixel n’est revendiquée.

Principes retenus : fond étendu derrière la navigation, matériau réservé aux contrôles, fenêtres Windows décorées et redimensionnables, séparation du décor et du texte. Références : `liquid-glass.md > The two layers`, `layout.md > Visual hierarchy`, `windows.md > Best practices`, `typography.md > Ensuring legibility`. Le résultat utilise JavaFX et constitue une approximation visuelle, pas le matériau natif Apple.

Règles communes : Segoe UI ; corps 14–16 px, secondaires 13 px, titres 32–46 px ; rythme d’espacement 8/12/16/24/32 ; fond #101216, contenu #191C22, panneaux #23272F, texte #F4F5F7, secondaire #BDC4D0. Accents tirés des palettes existantes ; succès #78D6A3, avertissement #F0C674, erreur #FF8795 indépendants. Le texte de l’action principale choisit blanc ou noir suivant le contraste calculé. Focus visible ; animations 160/220 ms désactivables.

- `ImmersiveShell` : fond de fenêtre recadré sans déformation, fondu, voiles de lecture, navigation flottante avec image floutée distincte du contenu. Aucun snapshot par frame. Image floutée mise en cache ; matériau masqué dans les fenêtres sans barre latérale.
- `ArtworkService` : résolution via le cache réseau existant, décodage sur deux workers, taille maximale 1920 × 1080, cache LRU de six images, douze demandes simultanément retenues au maximum. Chaque shell rejette les réponses périmées et utilise mc.jpg en secours.
- Les callbacks du shell sont liés à la génération de navigation : une ancienne page ne peut pas imposer son illustration après sa fermeture.
- `ThemeService` applique une palette de rendu neutre sans réécrire les couleurs enregistrées. Toutes les couleurs personnalisées restent éditables et persistées dans la palette complète. Les anciennes couleurs de fonds/textes sont conservées mais ne recolorent plus les surfaces.
- `LauncherSettings` accepte toujours son ancien constructeur ; JSON version 1 conservé. Champs optionnels `reducedMotion` et `reducedTransparency`, faux lorsqu’absents. Les autres modifications de préférences les conservent.

## Écrans et composants
| Parcours | Résultat |
|---|---|
| Accueil et actualités | Dernière instance sur fond commun, action principale, journal sur surface neutre ; cartes et liens existants conservés |
| Instances officielles | Liste de sélection directe et flèches ; sélection immédiate indépendante du chargement d’image ; ancien carrousel et caches non bornés retirés |
| Instances personnelles | Création et accès explicite à l’import/bibliothèque ; illustration personnelle reprise dans la fenêtre de gestion |
| Vanilla | Composition immersive, explication et action vers le parcours réel de création/gestion |
| Détails/réglages d’instance | Fenêtre avec fond contextuel, onglets Vue d’ensemble / Contenus, actions avancées repliables, pied de progression |
| Création/importation | Formulaire de création agrandi avec chrome Windows ; import conserve son sélecteur de fichier système et ses confirmations |
| Mods, shaders, resource packs | Recherche, filtres et actions existants conservés ; liste agrandie et styles communs, accessible en page et en bibliothèque |
| Téléchargements | État explicite et accès aux instances ; aucun faux suivi centralisé ajouté (la fonctionnalité n’existait pas) |
| Paramètres | Confort visuel, accents, palette complète repliable, Minecraft/stockage préservés |
| Compte Minecraft | Carte de connexion et profil, aide au changement de compte repliable ; intégration Prism inchangée |
| Informations/licences | Fenêtre accessible dans la navigation, composants utilisés, indication des notices distribuées et lien vers la licence Prism ; pas un nouvel audit juridique des dépendances |
| Menus/dialogues/erreurs/chargements | Styles communs, dialogues redimensionnables, minimum lisible et actions natives conservées |

## Vérification
- `gradlew.bat test check --no-daemon` : 98 tests, 96 réussis, 2 ignorés, aucun échec ; 24 vérifications Store packaging réussies.
- Chargement des FXML, handlers, navigation, absence de warnings CSS dans les tests UI, tailles logiques 1280×720 et 1920×1080 (ainsi que des tailles plus petites) vérifiés par la suite.
- `gradlew.bat test --tests '*ImmersiveVisualTest' --tests '*DesktopUiTest' -I tools/ui-scale.init.gradle '-Dcookiecore.uiScale=1.5' --no-daemon` : réussi ; échelle native JavaFX forcée à 150 %, sans modification des préférences Windows.
- Nouveaux tests : réponses d’images hors ordre, erreur et secours, recadrage, dimensions des voiles, absence de matériau hors navigation, cache/résolution, contraste des accents et persistance des options.
- Application réelle lancée par Gradle et observée sous Windows ; shell affiché après le démarrage normal. Fenêtre et arbre d’accessibilité inspectés. Captures des vrais FXML après chargement asynchrone dans `build/glass-*.png` ; création dans `build/ui-create-instance.png`, bibliothèque sur inventaire temporaire de test dans `build/ui-instance-library.png`.
- Deux défauts visuels corrigés pendant cette mission : voiles ramenés à zéro par le layout de Pane ; couche de verre non masquée dans les fenêtres sans navigation.

## Limites explicites
Pas de connexion Microsoft interactive, d’installation/mise à jour/lancement réel de Minecraft, de téléchargement de modpack massif ou de suppression de données pour vérifier le design. Les tests des services existants passent, mais cela ne remplace pas ces essais de bout en bout. Le flux MP4 n’a pas été modifié ; lecture vidéo complète et secours média forcé non retestés séparément. Pas de nouveau MSIX construit, signé ou soumis ; validation WACK/Store hors de cette mission. Parcours clavier complet et lecteur d’écran non audités exhaustivement. Les captures d’écrans de test hors réseau montrent volontairement les états vides/chargement ; la bibliothèque utilise un inventaire temporaire explicitement nommé comme fixture.

## Lancement
```powershell
cd C:\Users\kalay\Desktop\core
$env:JAVA_HOME='C:\Users\kalay\.jdks\temurin-25.0.4.1'
.\gradlew.bat run
```

## Ajustement après retour visuel — verre dépoli et palette
Le retour utilisateur remplace la neutralité stricte des surfaces par une teinte légère liée au thème. Le décor est maintenant flouté sur ses seuls ImageView (18 px, cache graphique), avec un voile teinté et moins de noir en bas de fenêtre. Panneaux, barre d’action et navigation partagent un matériau translucide dont la couleur est dérivée de l’accent ; le mode réduction de transparence conserve une version opaque. Aucun effet sur les textes.

Contenus : actions alignées à droite à partir de 620 px, retour sous les métadonnées en largeur réduite ; séparateurs légers et moins de fonds imbriqués. Focus sans halo lumineux. Palette : ScrollPane dédié, hauteur bornée, TabPane non comprimé, dépliage sans animation. Nouveau test `expandedPaletteScrollsToLastColor` vérifiant que la dernière couleur entre entièrement dans le viewport à la fin du défilement. Capture `build/palette-scroll-verified.png`.
