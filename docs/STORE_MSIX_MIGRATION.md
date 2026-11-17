# Spécification : remplacer l'ancien système Windows par Microsoft Store MSIX

## Décision
Canal officiel de distribution de **Cookie Core** : Microsoft Store, format **MSIX x64**. Compte développeur individuel déjà créé, produit « Cookie Core » créé dans Partner Center, soumission toujours en brouillon. Microsoft Store signe le MSIX **après certification** et distribue les mises à jour du programme. Aucun certificat Authenticode payant ni licence open source requis pour ce scénario.

Le système GitHub `ZIP + Install.ps1 + Bootstrap + release.json/release.sig` n'est **plus** le moyen d'installer ou mettre à jour le programme. Le dépôt GitHub et GitHub Pages restent utiles pour code, CI, catalogues, contenus, modpacks et annonces.

## Frontière obligatoire : deux mécanismes distincts
| Domaine | Propriétaire du cycle de vie | Règle |
| --- | --- | --- |
| Application Cookie Core, son exécutable, runtime Java et ressources embarquées | Microsoft Store / MSIX | Ne jamais auto-modifier les fichiers installés |
| Instances Minecraft, mods, paramètres, shaders, sauvegardes, téléchargements des packs | Services existants de Cookie Core et Prism | Garder installation et mises à jour indépendantes, contrôle de conflits et données utilisateur |
| Prism portable téléchargé au premier démarrage | `PrismRuntime` existant | Auditer téléchargement, légalité, stockage, exécution et confiance Windows distinctement |

## Critères de réussite
1. `.msix` ou `.msixbundle` Windows x64 valide, reproductible par Gradle/CI Windows ; inclut Java 25 et JavaFX 25, sans JDK à installer chez le joueur.
2. `AppxManifest.xml` réel : identité Store EXACTE de Partner Center, version MSIX `MAJEUR.MINEUR.PATCH.0` (dernier composant réservé au Store, trois premiers dans 0–65535, premier non nul), architecture x64, cible Windows Desktop, logo et entrée du véritable exécutable ; modèle desktop full-trust (`packagedClassicApp` / `mediumIL`) seulement si nécessaire et autorisé par le schéma.
3. Packaging natif JavaFX à partir d'une **app-image jpackage**, puis outils MSIX officiels (Windows SDK `MakeAppx` ou MSIX Packaging Tool) ; pas de pseudo-MSIX, pas de dépendance Windows App SDK sans utilité démontrée.
4. Aucun updater autonome du programme : pas de `release.json`, `release.sig`, clé Ed25519, bandeau de redémarrage forcé, téléchargement de nouvelles images, superviseur `Bootstrap` ou installation par `Install.ps1` **dans la version Store**.
5. App JavaFX lance la GUI, intro vidéo et fallback, navigation, thèmes, téléchargements d'instances, ajout Vanilla, connexion Microsoft Prism et lancement Minecraft ; aucune régression fonctionnelle.
6. Aucun fichier utilisateur effacé lors d'installation, update, désinstallation guidée ou migration. Vérifier mondes, `prism-data/accounts.json`, `settings.json`, `inventory/`, `operations/`, répertoire d'instances configuré, paramètres graphiques, RAM et thème.
7. Un pipeline qui compile, teste, produit et publie **un artifact CI MSIX** ; ne soumet pas automatiquement une release publique sans accord ; diagnostic et hash SHA-256 de l'artifact.
8. Expliquer les limites vérifiées : Store peut refuser la certification ; la signature MSIX ne garantit pas que Windows accepte le binaire tiers Prism téléchargé ; mises à jour de Store non immédiates sur chaque poste.

## Ordre d'implémentation, sans gros chantier annexe
**Phase A — Audit et protection.** Vérifier l'état Git, cartographier toutes les dépendances à `update/`, les références aux chemins en écriture, les processus Prism et les tests ; identifier les valeurs Store à obtenir. Capturer une base de tests. Aucune suppression encore.

**Phase B — Première app-image puis MSIX local.** Conserver/reprendre le `jlink`/`jpackage` utile de `tools/update/Package.ps1`, retirer seulement la partie ZIP/setup historique. Construire un MSIX selon Microsoft Learn. Ne pas inventer `Publisher`; support paramétrable ou configuration depuis Partner Center. Fournir assets/icône valides en exploitant les ressources autorisées existantes ; documenter les dimensions exactes. Ne pas embarquer des comptes ou modpacks de test.

**Phase C — Désactivation et retrait ciblé de l'ancien updater.** Supprimer appel `UpdateUi.attach` dans `CookieCoreApp`, revoir `PackagedLauncher` sans casser le point d'entrée JavaFX ; retirer le superviseur et les composants spécialisés de `src/main/java/.../update` après preuve de l'absence de consommateurs. Remplacer les tâches Gradle et workflow GitHub par tâches/store CI. Retirer le secret `COOKIE_CORE_SIGNING_KEY`, variables `COOKIE_CORE_UPDATE_KEYS` / `COOKIE_CORE_KEY_ID` et ancres publiques uniquement **après** vérification qu'elles ne sont pas utilisées ailleurs ; ne jamais imprimer ces valeurs. Supprimer les scripts et la documentation historique après mise en place des remplaçants. NE PAS altérer `InstanceManager` ni les méthodes `prepareUpdate`/`finishUpdate` des modpacks.

**Phase D — Vérifications Windows locales.** App-image, manifeste, MSIX inspection, installation signée avec certificat **de test** dans machine isolée, démarrage réel, tests régression. Dépister virtualisation AppData, permissions, lancement Prism et collision avec ancienne installation. Pas de recommandation de désactiver Smart App Control.

**Phase E — Validation Store.** Exécuter les tests de `STORE_TEST_MATRIX.md`, produire package et rapport. La publication Microsoft requiert l'action du propriétaire sur Partner Center. Tester ensuite une installation / mise à jour **effectivement distribuée par Microsoft** (flight/test privé adapté) : un certificat de test ne démontre pas la signature Microsoft ni le comportement final de SAC.

**Phase F — Nettoyage final.** Quand B à E sont validées, retirer références inutiles, tests historiques remplacés, docs périmées, scripts et variantes de release non supportées ; tenir à jour `CODEX_PROGRESS.md` et `STORE_RELEASE_RUNBOOK.md`. Proposer un PR/commit lisible. Ne pas effacer anciennes releases GitHub ou dossiers des utilisateurs.

## Idées de conception sans obligation de surarchitecture
- Pas besoin d'un client API Microsoft Store pour le simple fait de recevoir les updates ; Windows / Store les distribuent. Si Cookie Core veut afficher son numéro de version, garder une ressource légère sans réintroduire un service de téléchargement.
- Pour une instance unique de GUI, préférer un mécanisme simple et explicite indépendant du superviseur historique **uniquement si nécessaire** ; tester plutôt que préserver une dépendance à `UpdateUi`.
- Ne pas migrer les données vers `WindowsApps`; une solution de persistance requiert une vérification avec le modèle MSIX/Win32 et le processus externe Prism.
- Si un choix de packaging ne permet pas de lancer Prism correctement ou viole des règles Store, documenter le blocage et proposer le correctif minimal, pas un faux succès.

## Suppressions à confirmer par analyse des références
- `src/main/java/be/cookies/studio/core/update/*` ; ne conserver une classe que si usage indépendant, justifié et renommé hors `update`.
- `tools/update/{Install.ps1,Launch.vbs,Create-ReleaseArchive.ps1,SignRelease.java,Package.ps1}` ; remplacer `Package.ps1` par packaging MSIX, pas seulement le supprimer.
- `src/main/resources/update-keys.properties` ; seulement si plus d'utilité.
- `src/test/java/be/cookies/studio/core/update/*` ; remplacer par tests équivalents réels plutôt que laisser des tests morts.
- `docs/WINDOWS_RELEASE_RUNBOOK.md` et, s'il existe, `docs/AUTO_UPDATE_WINDOWS.md` ; remplacer par les docs Store.
- `.github/workflows/windows-release.yml` : modifier/remplacer, ne pas supprimer le CI de publication sans successeur.
- `build.gradle.kts` : supprimer tâche `packageWindows`, étapes ZIP/Ed25519 spécifiques ; préserver tests, JavaFX, versionnement utile et `jpackage` app-image.

## Liens officiels à vérifier à l'implémentation
- MSIX manuel : https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-manual-conversion
- Package Identity Partner Center : https://learn.microsoft.com/en-us/windows/apps/publish/view-app-identity-details
- MSIX et containerisation : https://learn.microsoft.com/en-us/windows/msix/msix-containerization-overview
- Signature Store : https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options
- Java 25 `jpackage` : https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html
