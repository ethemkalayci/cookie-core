# PRIORITÉ 1 — Auto-mise à jour complète de Cookie Core sous Windows

**Statut initial : à implémenter.** Ce document est la spécification de travail pour Codex. **Ne pas** développer ici la mise à jour des modpacks ni celle de Prism.

## Résultat attendu
Un joueur installe Cookie Core **une seule fois** sur Windows x64. À l'ouverture suivante, si une release stable compatible plus récente existe, Cookie Core télécharge et vérifie la version, prépare son activation, ferme proprement le programme, active la version et redémarre, sans téléchargement ou réinstallation manuels. Aucun droit administrateur requis en fonctionnement normal d'une installation par utilisateur. En cas d'échec, la dernière version fonctionnelle doit rester utilisable.

L'auto-update d'une exécution par `gradlew run`, depuis l'IDE ou depuis les tests doit être **désactivé**. Les données existantes doivent rester intactes.

## Invariants non négociables
1. **Séparation :** données existantes sous `%LOCALAPPDATA%\CookiesStudio\CookieCore` inchangées ; binaires du launcher ailleurs sous un dossier utilisateur distinct ; jamais de suppression récursive traversant le dossier de données.
2. **Runtimes distincts :** runtime Java 25 packagé pour Cookie Core ≠ runtimes Minecraft ≠ runtime Prism portable. Ne pas dépendre d'un JDK installé chez les amis.
3. **Versions immuables :** préparer une version complète dans un nouveau dossier avant activation ; ne jamais patcher le binaire JavaFX en cours d'exécution.
4. **Bootstrap indépendant :** un processus suffisamment stable sélectionne/lance une version installée ; le processus de mise à jour continue après fermeture de l'app. Si l'architecture choisie est différente, justifier comment elle fonctionne sous Windows et reprend après crash.
5. **Validation avant bascule :** manifeste et plateforme valides, version strictement plus récente, téléchargement complet, empreinte, archive sécurisée, prérequis, espace disque, puis activation atomique ou à étapes récupérables.
6. **Authenticité :** un SHA-256 récupéré depuis la même source modifiable que l'archive vérifie l'intégrité, **pas** l'identité de l'éditeur. Concevoir et implémenter une vérification de provenance adaptée (p. ex. manifeste signé Ed25519 avec clé publique embarquée, clé privée hors dépôt, politique de signature/revocation). Bloquer les mises à jour dont l'authenticité ne peut pas être établie ; documenter le bootstrap initial de confiance.
7. **Récupération :** conserver l'ancienne version jusqu'à confirmation de lancement sain de la nouvelle (handshake de santé, démarrage effectif ou mécanisme équivalent). Rollback en cas d'installation interrompue ou d'échec confirmé ; éviter les boucles de redémarrage.
8. **Concurrence :** verrou par installation, interdiction de deux updaters simultanés ; ignorer les releases anciennes et les tentatives de downgrade non autorisées.
9. **Interface :** progression/états JavaFX sans bloquer le thread graphique ; comportement clairement visible en cas d'échec réseau ; démarrage hors ligne sur version locale utilisable.
10. **Respect de l'existant :** ne modifier ni comptes Microsoft/Prism, ni instances, ni thèmes, ni monde/sauvegardes, ni intro vidéo ; ne pas supprimer les fichiers qui ne sont pas connus comme appartenant exclusivement au programme.

## Conception attendue (à adapter après examen réel)

### 1. Distribution et démarrage
- Décider **une seule** stratégie concrète (de préférence distributions Windows versionnées + bootstrap stable), et la réaliser de bout en bout.
- Construire l'application avec le JDK 25 et la pile Gradle existante ; évaluer `jpackage` (app-image Windows + runtime inclus). Préférer les outils JDK et Gradle existants aux nouveaux plugins.
- Construire un premier installateur ou paquet d'installation **par utilisateur** qui crée le lanceur stable, les dossiers de versions et le raccourci nécessaire pour les lancements futurs ; documenter la seule installation initiale. Ne pas affirmer qu'une app-image ZIP représente à elle seule une installation automatique.
- Prévoir la mise à niveau du bootstrap : compatibilité entre anciens/nouveaux protocoles, ou remplacement indirect sûr depuis un processus tiers à une étape dédiée. Ne jamais effacer le seul démarrage fonctionnel pour essayer de l'actualiser.
- Normaliser la séparation entre le `version` Gradle, la version embarquée à l'exécution et les tags GitHub (`v1.0.0`, `v1.0.1`). Ne pas présenter `1.0-SNAPSHOT` comme une release stable.

### 2. Métadonnées de release
Créer un manifeste clairement versionné (format contrôlé, par exemple JSON), avec au minimum :
- `schemaVersion` ; `version` ; `channel` (`stable`) ; `platform`/`architecture` (`windows-x64`) ; référence d'artefact ; taille maximale et/ou taille attendue ; `sha256` ; `publishedAt` facultatif ; version/protocole minimum du bootstrap si nécessaire ; signature et identifiant de clé publique.
- Choisir des URL de Releases HTTPS vers `ethemkalayci/cookie-core`, ou des URLs strictement validées. Rejeter redirections vers schémas non HTTPS, domaines inconnus et chemins inattendus.
- Comparer les versions **numériquement**, pas lexicalement ; format stable explicite ; refuser `SNAPSHOT`, prereleases ou architectures non supportées dans le canal stable.
- Le manifeste doit être lu avec bornes de taille et validé avant toute opération destructrice. Prévoir la compatibilité des futures évolutions du schéma.

### 3. Vérification / téléchargement
- Au démarrage réel **après que l'interface est utilisable**, planifier une vérification en arrière-plan. Ne pas retarder indéfiniment l'interface, ni casser `CookieCoreApp`/l'intro MP4.
- Timeouts de connexion/lecture, codes HTTP, tailles limites, erreurs DNS, annulation, nettoyage des téléchargements partiels.
- Téléchargement dans un staging sur le volume cible. Contrôler longueur, SHA-256 et authenticité du manifeste **avant d'autoriser l'installation**.
- Extraction défensive : Zip Slip, traversée `../`, chemins absolus, points de jonction/symlinks, entrées dupliquées, noms Windows réservés, tailles décompressées, collisions de casse, fichiers spéciaux, etc.
- Ne jamais désactiver la sécurité pour faire passer un test. Rejeter les formats inattendus plutôt que deviner.

### 4. Activation et récupération
- Le processus JavaFX demande une activation, ne remplace pas ses propres fichiers.
- Bootstrap/updater attend la disparition du processus ciblé ; pas de `taskkill` global, pas de fermeture de Prism/Minecraft ; délai borné et message utile.
- Version de destination d'abord complète et vérifiée ; point de bascule atomique si supporté, sinon journal d'étapes et reprise idempotente au prochain lancement.
- Conserver l'ancienne version comme secours ; confirmer le démarrage sain avant suppression différée des anciennes versions, en ne supprimant que les artefacts gérés par Cookie Core.
- Si une vérification de lancement sain ne peut être automatisée, ne pas proclamer un rollback automatique complet ; expliquer la limite et fournir un mécanisme de secours reproductible.
- Éviter les doubles instances et conflits de version pendant la bascule.

### 5. Interface utilisateur
- Fournir des états : « recherche », « à jour », « téléchargement (progression) », « vérification », « prêt à redémarrer », « installation », « erreur », « retour à la version précédente ».
- Choisir une intégration minimale mais visible dans `ShellController` ou l'écran existant approprié, **sans** réécrire tout `downloads-view.fxml` (actuellement simple placeholder) ou l'ensemble du design.
- L'utilisateur peut continuer à utiliser son ancienne version si la vérification distante échoue, sauf politique de sécurité explicitement justifiée. Ne pas fermer sans version locale viable.

### 6. Pipeline GitHub Actions
- Ajouter dans `.github/workflows/` un build reproductible Windows x64 de la distribution ; JDK 25, Gradle Wrapper, tests pertinents, `jpackage` et artefacts.
- Version issue d'un tag explicite ; rejeter divergence entre tag, version Gradle et manifeste.
- Générer les SHA-256 et métadonnées de publication ; implémenter le mécanisme de signature retenu. **Clés privées exclusivement dans les secrets GitHub ou environnement de signature sécurisé, jamais dans le dépôt**.
- Séparer préparation/test et publication : `workflow_dispatch`/tag avec permissions minimales. Pas de publication automatique depuis une PR non fiable, ni d'exposition des secrets aux PR externes.
- Si des secrets ou permissions GitHub manquent, créer la configuration et un mode de test local, expliquer précisément les valeurs à fournir ; **ne pas** créer de fausses clés ni prétendre avoir publié une release.

## Séquence d'implémentation conseillée (le moins d'aller-retours possible)
1. Auditer les fichiers ciblés : `build.gradle.kts`, `CookieCoreApp`, `ShellController`, `SettingsService`, chemins, tests, `gradle/wrapper`, workflows présents/absents.
2. Fixer version de build et distribution initiale Windows + bootstrap ; installer **une seule** mise en œuvre cohérente (pas plusieurs prototypes).
3. Implémenter manifeste signé, vérification, téléchargement, préparation et suivi d'état ; tests unitaires sans réseau réel.
4. Implémenter switch de version, redémarrage/rollback, tests de panne et reprise.
5. Raccorder l'UI après son affichage (préserver intro), puis les workflows de packaging/publication.
6. Exécuter tests ciblés puis `test` général ; valider sur Windows si un environnement Windows est accessible. Mettre à jour `docs/CODEX_PROGRESS.md` régulièrement.

## Tests et preuves d'acceptation
- Version locale = distante ; ancienne release ; release plus récente ; versions non conformes ; canal/OS/architecture incompatibles.
- GitHub inaccessible, timeout, HTTP non-200 ; application locale utilisable en mode hors ligne.
- Réponse trop volumineuse, téléchargement tronqué, checksum incorrect, signature invalide, redirection dangereuse, ZIP path traversal / ZIP bomb.
- Deux tentatives simultanées ; fichier verrouillé ; crash entre chaque phase de bascule ; restauration de la version saine.
- Conservation après migration de `settings.json`, `settings.previous.json`, `prism-data/accounts.json`, `inventory/`, `operations/`, `prism-runtime-*`, dossiers d'instances personnalisés ; aucune lecture/écriture dans les mondes.
- Mode IDE : aucune fermeture/redémarrage automatique. `gradlew.bat test` doit fonctionner sur JDK 25 ; tests JavaFX uniquement si environnement graphique disponible.
- Test manuel Windows x64 : installer `1.0.0`, préparer une release de test signée `1.0.1` accessible à une installation de test, démarrer, constater download + redémarrage + version réellement changée, puis simuler une mise à jour cassée et constater récupération. Ne pas confondre scénario **documenté** et scénario **exécuté**.
- Une release réelle sur GitHub et des secrets de signature nécessitent une autorisation de l'utilisateur ; préparer plutôt que publier sans permission.

## Critère de livraison
**L'auto-update du launcher seulement** est intégré de bout en bout (incluant bootstrap, distribution et pipeline) avec tests et documents de mise en route ; sinon indiquer précisément les jalons non terminés dans `docs/CODEX_PROGRESS.md`, avec preuves et prochaines actions. Pas de « terminé » sur la seule base de classes compilées.
