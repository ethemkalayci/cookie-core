# Carte locale après migration MSIX — 07/11/2026

## Build et distribution
- `build.gradle.kts` : Java 25, JavaFX 25 controls/fxml/media ; JUnit 6 ; `packageInput`, `packageWindowsAppImage`, `packageMsix`, `testStorePackaging` (inclus dans `check`). Version stable MAJEUR.MINEUR.PATCH, conversion MSIX avec dernier composant zéro.
- `tools/store/identity.json` : identité réelle fournie par le propriétaire ; aucune clé privée.
- `tools/store/Package.ps1` : jlink (inclut jdk.management pour les paramètres RAM), jpackage app-image, MakeAppx pack/unpack, comparaison SHA-256 de chaque fichier extrait, rapport JSON.
- `tools/store/StorePackaging.ps1` : validation/version/manifest/assets et inspection du contenu JavaFX ; logo source existant, PNG carrés 44/50/150 px.
- `tools/store/Test-Packaging.ps1` : tests positifs/négatifs de version, identité, échappement XML, persistance, assets.
- `tools/store/Test-AppImage.ps1` / `AppImageSmoke.java` : tests sur runtime livré, MP4, fallback, six pages ; essai du véritable EXE avec agent réservé aux tests. Profil factice, réseau Java bloqué, aucune connexion Microsoft.
- `.github/workflows/windows-release.yml` : préparation locale d’un workflow manuel, tests + MSIX non signé + rapports en artifacts. Aucune soumission Store ou release GitHub automatique. Workflow jamais exécuté pendant cette intervention.

## Entrée et coexistence
- `PackagedLauncher` : point d’entrée non-Application nécessaire à JavaFX en classpath ; appelle CookieCoreApp directement.
- `CookieCoreApp` : interface et intro conservées ; acquiert/relâche LauncherSession, aucune vérification de release ou redémarrage forcé.
- `service/LauncherSession` : verrou dans le répertoire de données et réservation des verrous bootstrap/app de l’ancienne installation standard si présente. Refuse une deuxième GUI, sans terminer de processus ni remplacer d’installation.
- Aucun package `be.cookies.studio.core.update` compilé ou livré. Le fichier local UpdateFiles.java modifié avant intervention est conservé octet pour octet comme texte dans `tools/store/preserved/`, hors sources et package.

## Fonctionnalités conservées sans modification
PrismRuntime (11.1.1 + SHA-256), PrismLauncherService, PrismAccountService, PrismConfig, PrismArchive, PrismVersions, MemorySettings, InstanceManager (`prepareUpdate` / `finishUpdate`), InstanceInventory, InstanceStorage, InstanceFiles, PackManifest, InstanceContentService, OfficialInstanceService, SettingsService, NewsService, caches, LastInstanceService, FXML/CSS/thèmes/MP4. Les tests service/controller antérieurs restent en place.

Les catalogues/annonces/modpacks conservent leurs URL existantes. Aucun accès à ces URL n’a été effectué par les validations de cette session (proxy Java local fermé).

## Données
`%LOCALAPPDATA%\CookiesStudio\CookieCore` et chemins personnalisés inchangés. Le manifeste desktop full-trust désactive la virtualisation des écritures de fichiers et demande `unvirtualizedResources`. Validation réelle install/update/uninstall et visibilité depuis Prism : encore requise, cette déclaration ne remplace pas un test d’installation.

Voir STORE_RELEASE_RUNBOOK.md pour commandes, transition et publication ; STORE_TEST_MATRIX.md pour les preuves et blocages.