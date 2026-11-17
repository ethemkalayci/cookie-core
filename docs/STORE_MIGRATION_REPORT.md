# Livraison locale Cookie Core / MSIX — 07/11/2026

## Résultat

Migration technique locale implémentée. Le programme n’a plus d’auto-updater GitHub. MSIX Windows x64 réel construit, identité Partner Center fournie, MakeAppx pack/unpack réussi et chaque fichier extrait comparé à l’app-image par SHA-256. Le package est **non signé, non installé, non soumis et non certifié**.

Livrable : `build/store-1.0.0/CookieCore-1.0.0.0-x64.msix` (42 741 103 octets). SHA-256 : `88e5e1c5b1ef646d90779df4c1640e7d8e473dee585a9fc0c3cb9d9d85fced2a`. Rapport de build, manifeste et fichier `.sha256` dans le même arbre. Les artefacts intermédiaires sous build/msix-audit ne doivent pas être publiés.

Le démarrage direct du nouvel EXE a été tenté et bloqué par Windows : « Une stratégie de contrôle d’application a bloqué ce fichier ». Aucun contournement. Cela ne permet pas d’affirmer que le Store acceptera l’application ou Prism.

## Modifications et diff

Périmètre migration sur fichiers déjà suivis : **23 fichiers, +67 / -924 lignes**, hors modifications préexistantes. `git diff` n’inclut pas les nouveaux fichiers non suivis : les inspecter également.

- `build.gradle.kts` : remplace packageWindows par packageWindowsAppImage/packageMsix, contrôle des versions Store, check inclut les contrôles de packaging.
- `CookieCoreApp.java` et `PackagedLauncher.java` : retrait des deux appels updater ; lancement JavaFX préservé, verrou de session et message de collision.
- Nouveau `service/LauncherSession.java` et 3 tests : une GUI par dossier de données, coexistence avec les verrous de l’ancienne installation standard et enfant orphelin, libération des verrous et conservation de fichiers factices.
- Nouveaux `tools/store/Package.ps1`, `StorePackaging.ps1`, `identity.json` : app-image, jlink incluant jdk.management, assets du logo existant, manifeste réel, MakeAppx et inspection/hashes.
- Nouveaux `Test-Packaging.ps1`, `Test-AppImage.ps1`, `AppImageSmoke.java` : validation du packaging, runtime livré, intro/fallback/navigation, tentative du véritable EXE. Aucun harness/agent livré dans le MSIX.
- `.github/workflows/windows-release.yml` : workflow manuel de build/test/artifacts, sans release automatique ni secrets updater. Préparé uniquement localement, jamais exécuté.
- CODEX_PROMPT, PROJECT_MAP, CODEX_PROGRESS, STORE_RELEASE_RUNBOOK, STORE_TEST_MATRIX actualisés ; présent rapport ajouté. Ancienne spécification déplacée en archive sans perte.

Aucune modification de InstanceManager, PrismRuntime, PrismLauncherService, PrismAccountService, SettingsService, services modpacks/Vanilla, vues, CSS, vidéo ou tests service/controller préexistants. L’interface et les mises à jour des modpacks restent distinctes du cycle de vie Store.

## Retraits justifiés

Tous les chemins Java ci-dessous sont sous `src/main/java/be/cookies/studio/core/update/` ; les tests historiques sous `src/test/java/be/cookies/studio/core/update/`.

| Fichiers retirés du code actif | Motif et remplacement |
| --- | --- |
| Bootstrap.java | Superviseur d’activation/rollback de versions du programme remplacé par Windows/Store. Verrouillage utile remplacé par LauncherSession. |
| UpdateUi.java, ShutdownGate.java | Bandeau, compte à rebours et arrêt imposé pour l’updater supprimés ; aucune auto-activation restante. |
| UpdateService.java | Téléchargement/remplacement du programme interdit dans la version Store. |
| ReleaseHttp.java, ReleaseManifest.java, ReleaseVersion.java | HTTP/manifestes/signatures/versions spécifiques à l’ancien updater ; aucune utilisation par Prism ou modpacks. Versionnement MSIX contrôlé par Gradle/PowerShell. |
| UpdateFiles.java | Extraction/activation propres aux archives du programme. Fichier utilisateur préexistant **déplacé et conservé octet pour octet** dans tools/store/preserved/UpdateFiles.java.txt, hors compilation/package. |
| src/main/resources/update-keys.properties | Ancres Ed25519 du seul updater du programme ; plus aucun consommateur. |
| ReleaseHttpTest.java, ReleasePackagingTest.java, UpdateSecurityTest.java, UpdateServiceTest.java, WindowsUpdateSmokeTest.java | Tests du mécanisme retiré ; remplacés par contrôles MSIX, tests de verrous et tests du runtime livré. Aucun test modpack supprimé. |
| tools/update/Package.ps1 | Remplacé par tools/store/Package.ps1, app-image puis vrai MSIX. |
| tools/update/Install.ps1, Launch.vbs, Create-ReleaseArchive.ps1, SignRelease.java | Installation autonome, superviseur et ZIP/signatures du programme retirés. Rien de cela n’est distribué dans le MSIX. |
| docs/AUTO_UPDATE_WINDOWS.md | Déplacé sans modification dans docs/archive, identifié comme historique non applicable. |

Preuves : recherche ciblée des consommateurs avant retrait (seulement les deux entrées JavaFX), compilation/tests après retrait, contrôle automatique du contenu du JAR et de l’image avant packaging, aucune classe update/clé/compte/script historique dans le payload. Les références restantes de documentation et d’assertions négatives ne constituent pas des dépendants.

## Protection du travail antérieur

Branche `main` conservée ; aucun commit, changement de branche, reset, ajout à l’index ou opération distante. Diffs staged/unstaged initiaux et inventaire dans `build/msix-audit/`. Comparaison finale : index inchangé, copie UpdateFiles identique à l’original.

Modifications préexistantes non attribuables à cette migration et non écrasées : `.idea/.name`, `.idea/cssdialects.xml`, `AGENTS.md`, `Theme.java`, suppression déjà présente de WINDOWS_RELEASE_RUNBOOK.md et des ressources news, outils MontDino/preparer_montdino/ui-scale. Leurs décisions de conservation appartiennent au propriétaire. Les documents de mission locaux existaient déjà ; leurs versions d’avant mise à jour sont conservées dans `build/msix-audit/docs-before`.

## Résultats des validations

- Baseline : 109 JUnit, 0 échec/erreur, 3 ignorés.
- Après migration : 93 JUnit, 0 échec/erreur, 2 ignorés (InstanceImagesTest.actualCatalogBannersDownloadDecodeAndRemainCached et PrismRuntimeTest.installsOfficialRuntime, explicitement réseau/opt-in). Donc **91 exécutés avec succès**, plus **24 assertions PowerShell réussies**.
- `check packageMsix --offline --no-daemon` : BUILD SUCCESSFUL. Scripts PowerShell : parse AST PASS ; git diff --check PASS.
- MP4 réellement en lecture, six pages et fermeture sur le Java embarqué : PASS. Fallback sans vidéo sur copie isolée du JAR : PASS. Captures intro/paramètres inspectées. Sources, thèmes et tests modpack conservés.
- Le smoke a découvert puis permis de corriger le module jdk.management manquant dans l’ancien jlink ; les premières preuves avant correction sont obsolètes.
- JAR applicatif et EXE de l’artefact final identiques par SHA-256 à ceux du dernier smoke (la dernière reconstruction ne corrigeait que l’encodage du descriptif du manifeste).
- EXE natif : BLOCKED par Windows, le script de smoke complet retourne un échec à cette étape. Ce n’est pas un PASS natif. Le harness/agent natif est compilé mais son exécution ne peut pas être validée sur ce poste.
- Contrôle visuel externe : outil indisponible, erreur MXC 0x8007000F ; captures JavaFX du harness utilisées. Aucun réglage Windows changé.
- YAML : revue du fichier faite, exécution Actions non réalisée ; parseur PyYAML non disponible localement, aucune dépendance téléchargée pour le remplacer.

Rapports : build/test-results/test, build/reports/tests/test, build/store-tests, build/msix-audit/store-final-build.log, build/msix-audit/app-image-smoke-final.log et build/app-image-smoke/e1f29b5c-e06e-490c-86ee-ff6aba563a3a.

## Ce qui reste à valider par le propriétaire

Installation signée en VM, démarrage via menu Démarrer, virtualisation réelle AppData et visibilité Prism, installation/mise à jour/désinstallation sans perte, autre volume, compte Microsoft/jeu réel, Prism externe/SAC, WACK, conformité/licences/privacy et acceptation des capacités restreintes, certification et vraie mise à jour Store. Aucune identité Partner Center ne manque désormais ; les blocages sont l’exécution Windows et les validations nécessitant installation/réseau.

La règle « toutes les modifications dans le projet » exclut ici installation MSIX, import de certificat et modifications du système. Aucun certificat créé, aucune installation ou connexion tentée. Les commandes de build, la transition non destructive, le retour arrière et les étapes de soumission personnelle sont dans STORE_RELEASE_RUNBOOK.md. Ne pas annoncer « Store prêt » avant ces validations.
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
