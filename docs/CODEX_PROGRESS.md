# Suivi Codex — migration Microsoft Store

> Ce document est un état de migration. Ne pas confondre « observé dans GitHub » et « exécuté/testé ». À mettre à jour à chaque phase : date, commit, résultats et blocages. Laisser les cases de réalisation non cochées avant preuve.

## Baseline (audit 06/11/2026)
- [x] Vérifié via GitHub : Java 25 / JavaFX 25 / Gradle 9.3 ; `PackagedLauncher`, `CookieCoreApp`, ancien auto-update dans `src/main/java/.../update/`.
- [x] Vérifié via GitHub : `tools/update/` et `.github/workflows/windows-release.yml` construisent les ZIPs et manifestes Ed25519 du programme.
- [x] Vérifié via GitHub : `InstanceManager` contient la mise à jour séparée des modpacks et le stockage utilisateur est sous `%LOCALAPPDATA%\\CookiesStudio\\CookieCore`.
- [ ] Audit Codex sur clone **actuel** et état `git status` avant toute modification.

## Implémentation (à réaliser par Codex)
- [ ] Branche dédiée et test baseline Gradle.
- [ ] Identité du produit Partner Center disponible ou paramétrage explicite non fictif.
- [ ] Image autonome Java 25/JavaFX 25 construite et exécutable.
- [ ] Manifeste MSIX/identité/ressources conformes ; MSIX x64 construit.
- [ ] Retrait/neutralisation complet du programme auto-updater GitHub sans casser l'entrée JavaFX.
- [ ] Workflow GitHub Actions produit MSIX + tests + SHA-256 ; publication contrôlée.
- [ ] Anciennes tâches/scripts/classes/tests/docs obsolètes supprimés **après** remplacements et vérification des références.
- [ ] Tests unitaires de tous les services et contrôleurs conservés.
- [ ] Tests manuels Windows et matrice `STORE_TEST_MATRIX.md` complétée.
- [ ] Test de persistance et Prism avec vrai MSIX.
- [ ] Test installation/mise à jour issue réellement du Store (si disponible).
- [ ] README/runbook à jour ; bilan clair des changements, suppressions, risques et limites.

## Blocages externes potentiels
- Valeurs d'identité MSIX exactes de Partner Center (Name/Publisher/PublisherDisplayName) non récupérées par cet audit.
- Accès au compte Microsoft et approbation/soumission : action du propriétaire, **pas** simulée par Codex.
- Signature Microsoft et certificat réel : uniquement après certification Store.
- Validation de l'exécutable Prism externe sous Windows avec Smart App Control : test requis.

## Rapport de prochaine intervention
- Commit / branche : À renseigner.
- Changements accomplis : Aucun à ce stade (seul le dossier de mission a été préparé).
- Tests lancés et résultats : Aucun test de code MSIX lancé pendant la préparation documentaire.
- Blockers : À actualiser.
- Prochaine action : suivre `CODEX_PROMPT.md` et `STORE_MSIX_MIGRATION.md`.

## 07/11/2026 — phase A locale
- Projet réel : C:\Users\kalay\Desktop\core. Aucune opération distante, aucun changement de branche/index/commit.
- Modifications préexistantes sauvegardées dans build/msix-audit (diffs staged/unstaged, copie exacte UpdateFiles.java).
- Baseline Gradle hors ligne : BUILD SUCCESSFUL, rapports copiés dans build/msix-audit/baseline-results.
- Seuls consommateurs externes du package update : CookieCoreApp et PackagedLauncher. InstanceManager et le cycle modpack sont indépendants.
- SDK 10.0.26100.0 / MakeAppx et JDK 25 locaux disponibles. Identité Partner Center fournie par le propriétaire, stockée dans tools/store/identity.json (aucun secret).
- Caches Gradle, TEMP et profil de tests isolés sous build/. Réseau Java redirigé vers proxy local fermé pendant les validations.

## 07/11/2026 — phases B/C/D locales
- App-image et MSIX x64 réels construits ; MakeAppx 10.0.26100.0 valide puis extrait, tous les fichiers payload comparés SHA-256. Identité Partner Center réelle intégrée.
- Ancien updater retiré, appels UI remplacés, verrou de GUI et compatibilité des verrous historiques ajoutés. Sources/tests/scripts dédiés retirés ; copie exacte du UpdateFiles.java préexistant conservée hors compilation.
- 93 tests JUnit, 0 échec, 0 erreur, 2 ignorés (intégrations réseau explicites) ; 24 contrôles PowerShell PASS. Baseline : 109 tests, 0 échec, 3 ignorés. Écart = retrait des tests updater et ajout de 3 tests LauncherSession, aucun test modpack supprimé.
- Test runtime livré : MP4 réellement en lecture, shell, six écrans et fermeture ; fallback sans MP4 : PASS. Captures produites ; intro et paramètres inspectés.
- Le smoke a révélé l’absence historique de jdk.management (OperatingSystemMXBean) : module ajouté ; navigation et erreurs JavaFX désormais vérifiées par le harness. Les premières preuves antérieures à cette correction ne sont pas une validation finale.
- EXE natif : BLOCKED, Windows rapporte « Une stratégie de contrôle d’application a bloqué ce fichier ». Aucun contournement ni changement de sécurité. Les tests via java.exe embarqué prouvent le runtime, pas le démarrage du package installé.
- Contrôle UI externe indisponible (MXC CreateProcessSecurityEnvironment 0x8007000F) ; captures JavaFX du harness inspectées à la place.
- Installation/signature de test, update/désinstallation MSIX, Prism réel, authentification, Store/certification/SAC : NOT RUN/BLOCKED, voir matrice. Installer ferait écrire Windows hors du projet, interdit par le périmètre actuel.
- Workflow et runbook préparés uniquement localement. Aucune opération distante, aucune modification des paramètres GitHub ou de Partner Center.

## Livraison locale
- Rapport : docs/STORE_MIGRATION_REPORT.md ; procédure personnelle : docs/STORE_RELEASE_RUNBOOK.md.
- MSIX final : build/store-1.0.0/CookieCore-1.0.0.0-x64.msix, 42 741 103 octets.
- SHA-256 : 88e5e1c5b1ef646d90779df4c1640e7d8e473dee585a9fc0c3cb9d9d85fced2a.
- Dernière reconstruction : descriptif du manifeste rendu compatible PowerShell 5 ; JAR et EXE identiques à ceux du smoke final. MakeAppx pack/unpack/hashes repassés.
- Index Git inchangé ; copie UpdateFiles identique ; git diff --check et parse AST PowerShell PASS.
- Phase E (Store) et recette MSIX installé non réalisées. Prochaine action du propriétaire : revue locale, tests d’installation dans VM, puis soumission personnelle si satisfaisante. Ne pas exécuter de publication distante depuis cette session.

## Nouvelle validation du 07/11/2026 — kit utilisable par le propriétaire

- Lanceur : TESTER-COOKIE-CORE.cmd ; guide : docs/TESTER_COOKIE_CORE.md.
- Exécution complète : build/validation/20261107-205821-ba0754/rapport.html (résultats JSON et journaux à côté).
- 12 contrôles automatiques PASS ; 2 domaines NOT RUN (signature/installation et recette réelle/Store). JUnit : 91 réussis, 2 ignorés ; tests de packaging : 24 assertions réussies.
- Le véritable CookieCore.exe a cette fois démarré : MP4, six écrans et fermeture propre PASS, avec agent externe réservé au test. Aucune politique Windows n’a été modifiée. Cela met à jour l’observation précédente de blocage pour ce nouvel artefact ; ce n’est pas une preuve de démarrage MSIX installé ou de confiance Store.
- Nouveau MSIX issu des sources actuelles, y compris le logo utilisateur : build/validation/20261107-205821-ba0754/package/CookieCore-1.0.0.0-x64.msix.
- SHA-256 : f9bdcba0af29b7fe2492395ec743c5fd7f0c040964313af7c34cb85fb1f7fad9.
- Ancien package sous build/store-1.0.0 conservé, empreinte inchangée ; logo utilisateur et index Git également inchangés par le kit.
- Comparateur de données factices : données inchangées acceptées ; une modification et une suppression détectées sur copie jetable. Ne constitue pas un test install/update/uninstall MSIX.
- Rapport HTML autonome : 16 fiches de recette, notes locales, export/import JSON lié au même run et au même hash. Test Node sans dépendance : affichage, 16 scénarios, export documenté, rejet d’un bilan étranger, résultats automatiques immuables PASS.
- Les erreurs du premier passage du kit (paramètre d’identité par défaut et compatibilité PowerShell 5) ont été corrigées puis le parcours complet réexécuté. Utiliser le rapport ci-dessus ou celui produit par un nouveau passage.
- Aucun accès distant, installation, certificat, modification système ou opération Git d’écriture pendant cette validation. Signature, recette MSIX installé, Prism réel, compte Microsoft, WACK et Store restent des étapes personnelles sur machine de test.

## 09/11/2026 — diagnostic WACK et reconstruction isolée
- EXE pré-MSIX retrouvé : build/validation/20261107-230407-e5bb47/package/image/CookieCore/CookieCore.exe ; empreinte identique à CookieCore-B.msix et au rapport C:\VMShared\rapport.xml.
- Rapport existant : global WARNING ; 22 PASS, TEST88 FAIL facultatif, TEST92 WARNING (impossible de traiter le binaire).
- Sources copiées sous C:\Users\kalay\Documents\ChatGPT\Cookies Core\diagnostic-wack-20261109\rebuild-core. Compilation offline PASS ; 24 contrôles packaging PASS ; nouveau MSIX non signé construit, pack/unpack et intégrité PASS. EXE reconstruit identique à l'ancien.
- Manifeste PerMonitorV2 validé ; test natif MP4/six écrans/fermeture PASS ; fenêtre réellement PER_MONITOR_AWARE_V2 selon API Windows. Aucun correctif code/DPI/Prism justifié.
- Sources originales inchangées (hashes vérifiés). Aucune suppression demandée au shell, aucun clean, installation, certificat, changement Git ou accès distant. Suite JUnit/validateur global non lancés ; tests contenant suppression volontaire évités.
- WACK non relancé : à reproduire dans la VM de certification, sans installer le package sur l'hôte. Aucun PASS WACK ou Store revendiqué.
- Diagnostic et preuves : C:\Users\kalay\Documents\ChatGPT\Cookies Core\diagnostic-wack-20261109\DIAGNOSTIC.md. Prochaine action : identifier l'accès à la VM de test et y enregistrer un nouveau rapport WACK sans remplacer le précédent.

## 09/11/2026 — nettoyage build demandé par le propriétaire
29 dossiers de sorties anciennes/intermédiaires supprimés après vérification de leur confinement sous build et absence de liens ; 4,33 Gio de tailles de fichiers retirés. Liste et bilan : C:\Users\kalay\Documents\ChatGPT\Cookies Core\diagnostic-wack-20261109\cleanup-plan.json et cleanup-result.json.
Conservés : local-gradle (nécessaire au validateur hors ligne), dernière validation 20261107-230407-e5bb47 et EXE pré-MSIX, rapports, sauvegardes de code/diffs msix-audit, preuves du diagnostic séparé. Les anciens dossiers package mentionnés dans l'historique peuvent ne plus exister après ce nettoyage autorisé. Sources, données joueur, Prism et Git non modifiés. C:\VMShared\CookieCore-B.msix conservé et hash revérifié.

## Nouveau passage WACK dans VirtualBox — 09/11/2026
Rapport fourni par le propriétaire : C:\VMShared\rapport-wack-20261109-212132.xml ; SHA256 EBC43E7F28BE3E600C7CCAABE414431094ADE2AD7F82E7D2076206E1F0592A80.
ReportGenerationTime brut : 09/11/2026 21:21:16. Campagne complète (PARTIAL_RUN=FALSE), WACK 10.0.28000.2957, OS 10.0.26300.0.
Comparaison au 7 novembre : même EXE SHA256 EB7C86DA4ED65544DAB5B41D96AE4969E1C9607FE1B29DD6F3D0BCDFBAFCE9C2 ; mêmes résultats (22 PASS, TEST88 FAIL facultatif, TEST92 WARNING obligatoire, global WARNING), mêmes 16 messages TEST88 et deux messages TEST92. Aucun nouveau défaut ni amélioration observé. Le nouveau passage reproduit l'impossibilité d'analyse DPI ; la simple relance ne la résout pas.
Les preuves mt.exe et de contexte réel PER_MONITOR_AWARE_V2 sur l'hôte restent valables pour cet EXE, sans prouver le comportement de sa fenêtre dans la VM. Pas de modification de code justifiée par ce rapport seul. Prochaine investigation utile : journal détaillé WACK de TEST92 dans la VM (cause de l'impossibilité de traiter le binaire), puis comparaison contrôlée de l'environnement si nécessaire. Ne pas relancer à l'identique en boucle, ne pas supprimer JavaFX/Prism, ne pas déclarer WACK ou Store validé.

## Analyse des journaux détaillés WACK — 09/11/2026
Source : C:\VMShared\WACK-diagnostic-20261109-212601\Profil-AppCertKit\post_process_trace_DPIAwarenessValidation_1192026_212112.txt.
Cause immédiate de WARNING identifiée : EXCEPTION[Infrastructure], HRESULT E_FAIL dans BinaryInfoClass.IsPerMonitorV2DPIAwarenessSet(), appelé par AppManifestContainsDPIAwarenesElement puis IsDPIAwarePackage (ligne 174 du source interne WACK). Le composant n'a pas terminé normalement l'analyse PerMonitorV2. Le journal détecte également une référence SetProcessDpiAwarenessContext dans awt.dll ; cela est une analyse statique, pas une preuve d'appel exécuté.
Contre-test local direct du composant COM WACK sur l'EXE B : GetImageType=34404 (x64), HasDpiManifest=True, GetDPIAware=True, IsPerMonitorV2DPIAwarenessSet=False. Pas d'exception sur cet hôte : le comportement exact de la VM n'est donc pas reproduit.
Expériences sur quatre copies EXE exclusivement : réinjection identique du manifeste ; valeur PerMonitorV2 seule ; remplacement du préfixe dpi2 par namespace par défaut ; combinaison de ces deux changements. Les quatre renvoient DPI=True et PerMonitorV2=False dans le composant local. Résultats : manifest-experiments-v2/results.json. Premier essai mt avec syntaxe incorrecte conservé séparément, non utilisé comme preuve.
Ces expériences ne démontrent aucun correctif ; ne pas appliquer ces variantes au projet. La liste de valeurs séparées par virgules est autorisée par Microsoft : https://learn.microsoft.com/en-us/windows/win32/sbscs/application-manifests . Le manifeste original reste valide et sa fenêtre a été observée en PMv2. L'origine profonde de E_FAIL reste indéterminée (analyseur/contexte/binaire) ; ne pas affirmer une incompatibilité VirtualBox ni une certification réussie.
Prochaine étape utile : contre-test avec une autre installation WACK/Windows sur le même package et conservation des versions, ou transmettre à Microsoft le rapport, la trace d'exception, le manifeste et la preuve de contexte DPI. Pas de modification JavaFX/Prism ni de source effectuée.


## 15 novembre 2026 — nouveau MSIX
Voir MSIX_VALIDATION_20261115.md : erreur PowerShell reproduite (version 1 sans guillemets, 1.0.0 avec guillemets), parenthèse Gradle corrigée, capacité unvirtualizedResources rétablie pour cohérence/persistance. MSIX 1.0.0.0 x64 avec identité EthemKalayci.CookiesCore généré ; 24 assertions packaging, 91 tests Java et trois parcours smoke réussis (2 tests Java ignorés). Non soumis/non certifié ; identité Partner Center et recette MSIX installée restent à vérifier. Aucun commit/push.


## Correction après téléversement Partner Center
La capture du propriétaire montre le rejet de Package/Properties/DisplayName=Cookie Core : nom non réservé ; la nouvelle fiche est Cookies Core. Les deux DisplayName du manifeste sont corrigés en Cookies Core. Nouveau package : build/store-1.0.0-name-fixed/CookieCore-1.0.0.0-x64.msix. MakeAppx, extraction et intégrité PASS ; 24 tests packaging PASS. SHA-256 : b65111ebb6d9ee5e5323a76cf4c67f5afc7d7b84df7c25538a1f4ba6c5994e8a. Ancien package conservé. Identité, version et capacités inchangées. Acceptation Store encore à vérifier.

## Refonte immersive — 16 novembre 2026
Mission de design explicitement autorisée par l’utilisateur, en remplacement de la restriction de design liée à la précédente mission MSIX. Dépôt du Bureau confirmé. Implémentation : shell immersif JavaFX, fonds contextuels asynchrones bornés, thèmes d’accent, réglages de confort persistants, sélection immédiate des instances, bibliothèque à onglets, fenêtres/dialogues et vues cohérentes. Rapport et limites : `docs/REFONTE_IMMERSIVE_20261116.md`.

Validation : compilation et suite complète 98 tests (96 réussis, 2 ignorés), 24 contrôles Store ; tests UI à 150 % réussis ; application et captures JavaFX examinées. Aucun changement à l’intro, Prism, InstanceManager, packaging, palettes source ou données personnelles. Aucun commit/push. Prochaine action de recette manuelle : après revue visuelle, vérifier une connexion Microsoft et le lancement d’une instance existante ; ne pas lancer une installation volumineuse seulement pour la présentation.

### Retour utilisateur : dépoli teinté / palette
Surfaces noires remplacées par un matériau translucide légèrement teinté de l’accent, illustration floutée sans flouter les textes, liste de contenus clarifiée avec actions latérales responsives. Défilement de la palette corrigé par viewport dédié ; test géométrique de la dernière couleur réussi. Captures JavaFX inspectées. Aucun changement métier ni couleur enregistrée modifiée.
Validation finale de cet ajustement : gradlew.bat test réussi, 99 tests dont 97 réussis et 2 ignorés ; captures 1280/1920 régénérées et examinées.

### Allègement accueil / contenus
Fonds du journal et des cartes retirés, bandeau de lancement transparent. Section Contenus de l’instance réorganisée : gestion en en-tête, catégories, recherche et outils regroupés, compteur compact. Tests ciblés de navigation, contenus et FXML réussis ; captures examinées.

### Fond neutre fourni par l’utilisateur
Image Downloads/testBg.png copiée sans transformation dans les ressources : images/banners/default-background.png. Utilisée comme secours du shell et pour accueil sans historique, paramètres, téléchargements, compte et Vanilla. Les pages neutres réinitialisent explicitement le fond pour ne pas conserver celui de l’instance précédente ; fonds contextuels conservés. Aucune dépendance au dossier Downloads à l’exécution.

### Mise en page adaptative et ajustement demandé — 17 novembre 2026
Pages neutres centrées avec largeur de lecture limitée ; gestion locale et création utilisent un hôte centré réutilisable. Création redimensionnable avec minimum réduit. Accueil et instances : partie supérieure sur toute la largeur disponible, texte à gauche, cloche/flèches à droite ; actualités et contenus inférieurs restent centrés (1368 px maximum). Suppression du statut redondant « Prête à jouer », conservation des erreurs et messages de lancement. Palette développée : hauteur utile 480 px, contenu non compressible, explication raccourcie et multilignes.
Validation : suite complète passée après centrage initial ; tests ImmersiveVisualTest, ThemeViewsTest et HomeViewTest repassés après ajustement. Rendus FXML hors réseau à 760×480, 1280×720, 1920×1080 et 3440×1440 ; captures accueil et palette examinées. Pas de lancement Minecraft ni modification de données joueur.

### Palette visible par défaut — 17 novembre 2026
Le volet Couleurs du thème est désormais ouvert et non repliable. Test de l'état initial et du défilement jusqu'à la dernière couleur : ThemeViewsTest passé via Gradle Wrapper. Aucun changement des préférences enregistrées.

### Cartes et réduction de transparence — 17 novembre 2026
Cartes d’actualités : matériau teinté, contour fin, espacement et titres renforcés. Panneau des contenus harmonisé. Correction des sélecteurs .opaque en .root:opaque pour correspondre à ThemeService ; surfaces, cartes, palette et contrôles deviennent opaques. Tests ThemeViewsTest, HomeViewTest et ImmersiveShellTest réussis, dont passage transparent/opaque/transparent sur les vrais composants FXML. Pas de vérification visuelle interactive sur cette étape.

### Transparence des cartes et illustration — 17 novembre 2026
Suppression du voile noir supplémentaire en mode réduction de transparence. L’illustration conserve le même voile dans les deux modes. Opacité des cartes news et du panneau contenus abaissée de 78 à 52 % en mode normal, surfaces solides conservées avec l’option activée. ThemeViewsTest passé, avec assertion de stabilité du fond lors de la bascule.

### Faux blocage par les JVM — 17 novembre 2026
InstanceOperationLock ne bloque plus toutes les applications Java identifiables : détection des points d’entrée Minecraft, Prism/MultiMC, Fabric, Quilt, Forge/NeoForge et --gameDir. Protection conservée pour Prism et les JVM dont les arguments sont illisibles. Tests dédiés et InstanceLifecycleTest exécutés. Aucun déplacement de données personnelles effectué.

### Fond personnel — 17 novembre 2026
Paramètres / Apparence : choix PNG/JPEG (30 Mo max), aperçu, application immédiate et restauration du fond intégré. Copie privée dans backgrounds, validation et décodage hors thread UI ; préférence facultative compatible settings v1, conservée lors des modifications de thème, mémoire et accessibilité. Fonds contextuels inchangés ; recours au fond intégré si la copie devient illisible. Compilation et tests paramètres/présentation réussis ; test import, indépendance du fichier original, persistance, image invalide et restauration. Sélecteur de fichiers non testé manuellement.

Vérification avant push : suite complète réussie (104 tests, 2 ignorés). Correction du défilement de la cloche vers les actualités en tenant compte des nouveaux conteneurs centrés.
