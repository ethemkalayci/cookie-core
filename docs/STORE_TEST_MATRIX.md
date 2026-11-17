# Matrice d'acceptation Cookie Core — Store MSIX

## Règles
Chaque test : **PASS / FAIL / BLOCKED / NOT RUN** + commande ou manipulation, environnement, logs/artefacts, date et résultat. Ne pas déclarer « PASS » si test non exécuté. Le résultat doit inclure le SHA-256 du MSIX final. Les tests destructifs se font uniquement sur une copie isolée des données. Ne jamais fournir de vrais secrets ou `accounts.json` dans les logs.

## Construction et identité MSIX
- [ ] `./gradlew.bat test` passe sur Windows avec JDK 25 et Gradle Wrapper ; les tests qui couvrent les modpacks restent intacts.
- [ ] Build app-image Windows x64 et exécution GUI avec runtime embarqué ; sans JDK système requis.
- [ ] MSIX réel produit et inspecté par outils Windows ; contenu (EXE, app/, runtime/, JavaFX, vidéo, CSS/FXML) et manifeste conformes.
- [ ] `Identity Name`, `Publisher`, `PublisherDisplayName` correspondent au produit **Cookie Core** réservé dans Partner Center ; version MSIX à quatre composantes (`MAJEUR.MINEUR.PATCH.0`, dernier composant zéro), monotone entre releases.
- [ ] Aucune clé/signature de mise à jour Ed25519 de programme embarquée par erreur, aucun ancien `Install.ps1`, `Launch.vbs`, bootstrap et boucle ZIP/replace.
- [ ] Le package n'exige pas l'installation de Gradle, IntelliJ, JDK ou Prism système.

## Application fonctionnelle
- [ ] Démarrage via menu Démarrer (app empaquetée), et sortie propre.
- [ ] Intro MP4 `CoreIntro.mp4` fonctionnelle et retour à l'écran principal si média absent/illisible.
- [ ] Écrans Accueil / Instances / Vanilla / Téléchargements / Paramètres / Compte ; CSS et 8 thèmes personnalisables sans régression.
- [ ] Paramètres de RAM Minecraft et dossier personnalisé survivent au redémarrage.
- [ ] Catalogue distant et annonces : affichage et cache hors ligne.
- [ ] Première connexion Prism/Microsoft via son UI officielle ; pseudonyme et lancement d'un jeu légitime.
- [ ] Runtime Prism officiel téléchargé depuis son URL et vérifié SHA-256 ; lancement réel du `prismlauncher.exe` externe depuis la version MSIX. Si Windows le bloque, enregistrer le blocage sans contourner la protection.
- [ ] Installation d'une instance officielle et d'une instance personnelle, lancement, suivi de processus et erreurs selon capacités préexistantes.
- [ ] Modpack : test `prepareUpdate` / `finishUpdate`, conflits, conserve les mondes, mods ajoutés par l'utilisateur, shaders, resourcepacks, touches, profil Prism et RAM. Test restauration.
- [ ] Opérations longues non bloquantes sur JavaFX Application Thread.

## Persistance et compatibilité (priorité absolue)
Préparer des **données factices**, sauvegarder des empreintes avant/après :
- [ ] `%LOCALAPPDATA%\\CookiesStudio\\CookieCore\\settings.json` et `settings.previous.json`.
- [ ] `prism-data/accounts.json` factice (jamais de vrai jeton dans le test) et instances/saves.
- [ ] `inventory/`, `operations/`, `prism-runtime-11.1.1/`, caches, images et données de progression.
- [ ] Répertoire d'instances sur un autre disque (si l'environnement de test en dispose).
- [ ] MSIX fresh install et réinstallation sur compte utilisateur standard ; évaluer explicitement l'écriture/lecture physique et la virtualisation AppData.
- [ ] Processus enfant Prism voit les mêmes fichiers et comptes que Cookie Core.
- [ ] Update MSIX de version A vers B : aucun changement non demandé des données.
- [ ] Désinstallation MSIX : documenter précisément les données conservées/perdues selon emplacement ; ne pas promouvoir une opération destructive comme sûre.
- [ ] Ancienne installation `%LOCALAPPDATA%\\Programs\\CookieCore` présente : la nouvelle version ne l'efface ni n'écrase ses fichiers ; aucun conflit de superviseurs, aucune collision de raccourcis silencieuse ; procédure manuelle documentée.

## Publication, sécurité, Windows
- [ ] Tests CI sans certificats de production : certificat éphémère/local seulement pour installer sur machine de test contrôlée, jamais en Trusted Root global à tort.
- [ ] Tests d'installation via Microsoft Store (flight/test de publication) quand disponibles : signature par Microsoft, lancement depuis Store, update réellement distribuée et redémarrage.
- [ ] Sur Windows 11 avec Smart App Control activé, installer/lancer la version Store approuvée ; vérifier Cookie Core **et séparément Prism**. Aucun contournement de politique.
- [ ] Conditions Microsoft Store pour téléchargement de composant externe Prism, licences/attributions et descriptions/privacy vérifiées.
- [ ] Version et packaging contrôlés ; pas de push automatique public sans validation manuelle.
- [ ] Rapports conservés en artifact CI avec logs sensibles expurgés.

## Rapport final Codex
Pour chaque test manquant, écrire explicitement BLOCKED/NOT RUN avec raison, pas « validé ». Séparer : **package construit**, **tests locaux**, **package soumis**, **certification réussie** et **mise à jour Store prouvée**. Ces cinq statuts ne sont pas interchangeables.

## Résultats locaux — 07/11/2026 (fait foi sur les cases de recette ci-dessus)

Environnement : Windows x64 existant, JDK Temurin 25.0.4.1, Gradle 9.3.0, JavaFX 25, SDK/MakeAppx 10.0.26100.0 ; profil factice et caches sous le projet, réseau Java bloqué par proxy local fermé.

| Contrôle | Statut | Preuve / limite |
| --- | --- | --- |
| Baseline avant migration | PASS | build/msix-audit/baseline.log et baseline-results : 109 tests, 0 échec, 3 ignorés |
| Suite après migration | PASS | build/test-results/test et build/reports/tests/test : 93 tests, 0 échec/erreur, 2 ignorés (tests réseau opt-in) |
| Tests services/contrôleurs/modpacks préexistants conservés | PASS | Aucune modification de leurs sources/tests ; InstanceLifecycleTest et tests Prism/settings/thèmes exécutés dans la suite |
| Validation identité/version/échappement/assets | PASS | 24 assertions, build/store-tests/*/result.txt |
| Génération jpackage + MSIX avec identité réelle | PASS | build/msix-audit/store-final-build.log, build/store-1.0.0/build-report.json |
| Schéma + extraction MakeAppx + payload SHA-256 | PASS | pack/unpack réussis ; comparaison de chaque fichier de l’image ; manifeste sous image/CookieCore |
| Absence updater, scripts d’installation, comptes dans package | PASS | Test-AppImage exécuté pendant packageMsix ; aucun package update ni ancres Ed25519 dans le JAR |
| MP4 réelle puis shell, six pages | PASS | Runtime Java livré, profil factice ; captures et media/result.txt sous build/app-image-smoke/e1f29b5c-e06e-490c-86ee-ff6aba563a3a |
| Fallback MP4 absent, six pages | PASS | Copie isolée du JAR sans vidéo ; fallback/result.txt au même emplacement |
| Paramètres RAM sur runtime jlink | PASS | jdk.management ajouté après détection d’une erreur initiale ; contrôles de navigation renforcés, écran inspecté |
| Lancement réel de CookieCore.exe | BLOCKED | Test natif tenté : « Une stratégie de contrôle d’application a bloqué ce fichier », build/msix-audit/app-image-smoke-final.log. Aucun contournement |
| Verrou GUI, superviseur historique, enfant orphelin, sentinelles factices | PASS | 3 tests LauncherSessionTest ; paramètres/comptes factices inchangés, verrou relâché |
| Installation/démarrage via menu Démarrer du MSIX signé | NOT RUN | Pas de signature de test/confiance/import/installation ; écrirait hors projet |
| Persistance install/update/uninstall MSIX et virtualisation physique | NOT RUN | À vérifier dans VM, y compris visibilité depuis Prism et autre volume |
| Prism externe réel / connexion Microsoft / lancement Minecraft | NOT RUN | Nécessite téléchargement ou données/compte utilisateur ; hors périmètre hors ligne |
| Catalogue et annonces en ligne | NOT RUN | Hors ligne seulement ; URL et logique conservées |
| Mise à jour d’un modpack réel sur installation MSIX | NOT RUN | Tests transactionnels factices existants PASS, recette réelle encore requise |
| Workflow GitHub Actions | NOT RUN | Fichier préparé uniquement ; aucun accès ni exécution distante |
| WACK / certification / signature Microsoft / mise à jour Store / SAC avec Prism | NOT RUN | Dépend d’installation isolée et/ou des actions du propriétaire/Store |

SHA-256 MSIX final : `88e5e1c5b1ef646d90779df4c1640e7d8e473dee585a9fc0c3cb9d9d85fced2a`.

Le premier smoke avant correction de jdk.management était insuffisant et ses anciens fichiers result.txt ne valent pas validation. Les preuves finales ci-dessus remplacent ces résultats. Le script complet de smoke termine en échec au test natif bloqué ; ses deux phases runtime précédentes ont réussi. La revalidation avec -SkipNative n’est jamais une preuve de lancement natif.

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
