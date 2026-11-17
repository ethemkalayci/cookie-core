# Cookie Core — instructions permanentes pour Codex

## Mission active
Migrer **Cookie Core** du déploiement autonome ZIP/PowerShell + auto-updater GitHub vers une **application Windows MSIX distribuée et mise à jour par le Microsoft Store**. Préserver intégralement le launcher Minecraft et son stockage utilisateur. La source de vérité est `docs/STORE_MSIX_MIGRATION.md`. Exécuter la mission, pas seulement produire un plan.

## Dépôt et stack vérifiés le 6 novembre 2026
- Dépôt : `ethemkalayci/cookie-core`, branche `main` (vérifier l'état réel au début de la session).
- Java 25 LTS ; JavaFX 25 (`controls`, `fxml`, `media`) ; Gradle Kotlin DSL + Wrapper 9.3.0 ; Gson 2.14.0 ; JUnit 6 ; Windows x64 en priorité.
- Entrée GUI : `be.cookies.studio.core.CookieCoreApp` ; entrée `jpackage` actuelle : `PackagedLauncher`.
- Ne pas remplacer la stack, l'architecture MVC + services ou les vues FXML. Ne pas altérer l'introduction MP4 et son mécanisme de secours.
- Données : `%LOCALAPPDATA%\\CookiesStudio\\CookieCore`, dont `settings.json`, `prism-data/`, `inventory/`, `operations/`, `prism-runtime-11.1.1/` ; dossier d'instances personnalisable.
- Prism Launcher officiel portable gère l'authentification Microsoft et le lancement ; `PrismRuntime` valide actuellement l'archive 11.1.1 par SHA-256.
- `InstanceManager` gère les opérations et mises à jour de modpacks, qui **ne doivent jamais être confondues** avec les mises à jour du programme.

## Non-négociable
1. **Aucune perte de données** : comptes Prism, mondes, sauvegardes, mods clients, shaders, resource packs, touches, thèmes, RAM, paramètres, répertoires personnalisés, instances et leurs sauvegardes.
2. **La version Store ne s'auto-remplace jamais** : aucun téléchargement/extraction/activation de nouvelles versions de `CookieCore.exe` depuis GitHub. Le Store possède le cycle de vie du package.
3. **Ne supprime pas l'ancienne installation Windows chez les joueurs**. Prévois une transition non destructive, détecte les collisions entre lanceurs, documente désinstallation manuelle et retour arrière.
4. **Ne touche pas au catalogue ni à la mise à jour des modpacks**, sauf correction strictement nécessaire à la compatibilité du package, accompagnée de tests.
5. Aucune modification de l'exécutable Prism officiel ni contournement d'authentification. Pas d'élévation, de désactivation Smart App Control/Defender ni de raccourci PowerShell `ExecutionPolicy Bypass` dans la distribution Store.
6. Aucun secret, certificat de signature privé, token, compte Minecraft ou `accounts.json` dans Git, les logs, les artifacts ou les prompts.
7. **Ne pas inventer l'identité MSIX** : `Identity Name`, `Publisher`, `PublisherDisplayName` doivent provenir de Partner Center → Cookie Core → Gestion du produit → Identité du produit. Si absents, prévoir une configuration documentée/paramétrable et signaler précisément ce blocage.
8. Ne pas déclarer « Store prêt », « approuvé » ou « Smart App Control compatible » sans preuves réelles. Le MSIX n'accorde pas automatiquement la confiance à `prismlauncher.exe` téléchargé séparément.
9. Test sur Windows x64 réel et, si possible, environnement propre ; MSIX de test signé localement uniquement pour la machine de test, MSIX de publication signé par Microsoft **après certification**.
10. **Suppression ciblée, jamais aveugle** : cartographier les références, modifier, faire passer les tests, puis supprimer les composants historiques sans consommateurs restants. Ne pas détruire d'historique Git ou de releases passées.

## Organisation et économie de tokens
- D'abord `git status`, inspection ciblée, puis `rg` sur les références pertinentes ; ne pas relire tout le dépôt à chaque étape.
- Travailler sur une branche dédiée. Ne pas réécrire des classes hors périmètre, ne pas changer les thèmes ou le design.
- Petites étapes vérifiables ; tester après chaque groupe de modifications. Utiliser les tests existants ; remplacer ceux liés à l'ancien updater par de vrais tests pertinents au MSIX.
- Documenter les décisions techniques essentielles, pas de narration de recherche ni de gros copier-coller de fichiers source.
- Pas de nouvelle dépendance majeure sans justification. Préférer `jpackage --type app-image` puis des outils MSIX Windows officiels et un `AppxManifest.xml` reproductible ; **`jpackage` ne sait pas produire directement de MSIX**.
- Pour toute suppression, décrire ce qui est supprimé, pourquoi, et les preuves d'absence de dépendants. Si un contrôle ne peut pas être exécuté, le déclarer explicitement.
- Mettre à jour `docs/CODEX_PROGRESS.md` après chaque phase ; ne jamais présenter une tâche non testée comme terminée.

## Lecture minimale
1. `docs/STORE_MSIX_MIGRATION.md` — exigences et périmètre.
2. `docs/PROJECT_MAP.md` — carte ciblée du dépôt.
3. `docs/STORE_TEST_MATRIX.md` — critères d'acceptation.
4. `docs/STORE_RELEASE_RUNBOOK.md` — publication et limites.

## Définition de « terminé »
Package MSIX Windows x64 reproductible et inspectable, démarrage JavaFX + média, préservation de toutes les fonctionnalités Minecraft, tests et diagnostics, absence effective de l'auto-updater du programme, instructions de publication Store et transition des anciennes installations. La **soumission et validation de Microsoft** restent des actions externes à ne jamais simuler.
