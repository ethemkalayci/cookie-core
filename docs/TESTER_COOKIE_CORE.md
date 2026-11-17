# Tester Cookie Core avant publication

## Le plus simple

Dans `C:\Users\kalay\Desktop\core`, double-cliquer sur **TESTER-COOKIE-CORE.cmd**. Le lanceur ouvre une console, exécute les contrôles hors ligne, ouvre temporairement des fenêtres JavaFX de test puis affiche le rapport dans le navigateur. Ne pas modifier le code pendant la validation.

Chaque passage crée son propre dossier `build/validation/DATE-IDENTIFIANT/` avec :
- `rapport.html` : résultats lisibles et 16 scénarios manuels ;
- `resultats.json` : résultats automatiques ;
- `package/` : **nouveau MSIX correspondant aux sources actuelles**, manifeste, empreinte SHA-256 et contenu extrait ;
- `junit/` et journaux : preuves de tests ;
- `smoke/` : captures des écrans, résultats média/fallback et essai EXE ;
- `fixtures/` : données purement synthétiques pour vérifier le comparateur.

`build/DERNIERE-VALIDATION.txt` donne le chemin du dernier rapport. Le package précédent sous build/store-1.0.0 reste intact ; si les sources ou le logo ont changé, il ne représente plus le dernier build. Pour une éventuelle soumission, identifier précisément le MSIX testé et son SHA-256 dans le rapport.

## Ce que le bouton fait automatiquement

1. Vérifie le JDK 25 x64 local, le SDK/MakeAppx et le cache du wrapper Gradle déjà présent.
2. Recompile et réexécute les tests JUnit hors ligne, y compris ceux des modpacks, paramètres, thèmes, Prism et verrous. Les tests réseau opt-in restent ignorés.
3. Contrôle identité/version/échappement XML/assets, construit une app-image et un MSIX neufs, valide et extrait le package, compare son contenu par SHA-256 et vérifie l’absence d’ancien updater et de comptes embarqués.
4. Teste le Java livré, la lecture MP4, le fallback sur une copie sans vidéo, les six écrans et la fermeture, avec profil factice et réseau bloqué. Il tente le véritable CookieCore.exe ; une protection Windows est signalée BLOQUÉ, jamais contournée.
5. Vérifie qu’un jeu de données synthétiques inchangé passe la comparaison et qu’une modification + une suppression volontaires sont détectées sur une autre copie.
6. Vérifie que les sources n’ont pas changé pendant le test et produit le rapport, même lorsqu’une étape échoue.

Les caches Gradle, fichiers temporaires et profils de test restent sous le projet. Les variables d’environnement modifiées sont restaurées en fin d’exécution. Aucune installation MSIX, confiance de certificat, opération Git distante, connexion Microsoft ni modification de vraie sauvegarde n’est effectuée.

Le code de sortie est 0 si les contrôles automatiques exécutés passent, 1 en cas d’échec, 2 en cas de blocage. **Un code 0 n’est pas une certification Store et ne valide pas les scénarios manuels non exécutés.**

## En ligne de commande

Depuis le dossier du projet :

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File tools/store/Validate-Local.ps1 -OpenReport
```

Pour sélectionner un autre JDK local : ajouter `-Jdk "C:\chemin\jdk-25"`. Pour construire une autre version stable : ajouter `-Version 1.0.1`. Pour ne refaire que les essais runtime quand le blocage EXE est déjà documenté : ajouter `-SkipNative` ; le test natif sera alors explicitement NON EXÉCUTÉ.

Les scripts utilisent RemoteSigned limité au processus ; ils ne changent pas la stratégie de sécurité du poste. S’ils sont bloqués par une stratégie de l’organisation, noter le blocage et demander une validation sur une machine de test appropriée ; ne pas désactiver les protections.

Sur une autre machine, JDK 25 x64, SDK Windows et cache Gradle local complet sont des prérequis. Le lanceur n’installe ni ne télécharge ces outils. Ne pas lancer `gradlew clean` sans sauvegarder les rapports : `build/` contient aussi le cache local utilisé pendant cette intervention.

## Compléter les tests qui nécessitent Windows installé et le réseau

Le rapport contient 16 fiches : installation signée, démarrage depuis le menu Démarrer, thèmes/RAM/chemins, Prism, connexion Microsoft, Vanilla, mise à jour de modpacks, coupure réseau, coexistence de l’ancienne version, stockage physique, update MSIX A → B, désinstallation/réinstallation, retour arrière, WACK, certification et vraie mise à jour Store.

Effectuer ces essais personnellement sur une VM Windows x64 propre ou un compte de test sans données personnelles. Les actions d’installation et de signature écrivent hors du projet et ne sont donc pas lancées automatiquement. Utiliser un package Store de test ou une copie signée avec un certificat de test adapté à la VM, conformément au runbook. Aucun certificat de test ne doit être inclus dans une publication.

Pour chaque fiche, choisir RÉUSSI / ÉCHEC / BLOQUÉ / NON EXÉCUTÉ et écrire une observation : date, version Windows, opération, résultat, emplacement d’une capture ou d’un journal. Les notes sont enregistrées dans le navigateur si possible ; **Exporter mon bilan JSON** permet de les conserver. L’import accepte seulement un bilan de la même exécution et du même SHA-256 : les résultats automatiques ne peuvent pas être remplacés par cet import.

Ne pas inscrire de jeton, mot de passe ou contenu de accounts.json. Les vrais comptes restent exclusivement dans l’interface officielle de Prism. Rien n’est envoyé depuis ce rapport HTML ; il fonctionne hors ligne.

## Comparer les données avant et après une opération MSIX

Le comparateur est indépendant de l’installation : il prouve uniquement si les fichiers de référence ont changé ou disparu. Il faut l’exécuter avant et après l’opération réelle pour valider la persistance.

1. Sur la **machine de test**, préparer un véritable profil de test et des instances jetables. Les fichiers `fixtures/` livrés par le lanceur sont des sentinelles ; ils ne constituent ni des mods ni un monde Minecraft jouables et ne doivent pas remplacer un profil fonctionnel.
2. Fermer Cookie Core, Prism et le jeu. Dans le dossier **exclusivement de test** à contrôler, créer le fichier `.cookiecore-test-data` contenant exactement `SYNTHETIC COOKIE CORE VALIDATION DATA`. Ce marqueur est un garde-fou, pas une sauvegarde. Ne jamais le créer dans les vraies données du joueur.
3. Depuis le projet présent sur cette machine, capturer la référence :

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File tools/store/Test-DataPreservation.ps1 -Mode Baseline -DataRoot "C:\chemin\donnees-de-test" -Snapshot "$PWD\build\recette-msix\avant-update.json"
```

4. Effectuer personnellement l’installation, mise à jour ou désinstallation sur la VM. Ne pas relancer l’application avant la première comparaison afin de distinguer les changements de packaging des écritures normales de l’application :

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File tools/store/Test-DataPreservation.ps1 -Mode Compare -DataRoot "C:\chemin\donnees-de-test" -Snapshot "$PWD\build\recette-msix\avant-update.json"
```

5. Refaire une capture distincte pour chaque scénario. Le script refuse d’écraser un état de référence existant. Le fichier de comparaison est écrit à côté du snapshot avec un identifiant unique.
6. Pour des instances sur un autre volume, marquer et contrôler aussi ce dossier de test avec un snapshot séparé. Refaire les vérifications après relance du jeu, et expliquer les changements attendus au lieu de les ignorer silencieusement.

RÉUSSI = tous les fichiers de référence existent et ont exactement la même empreinte. Les nouveaux fichiers sont listés séparément. `launcher.lock` est exclu car c’est un fichier de synchronisation sans données joueur. Fichier modifié/manquant = ÉCHEC, à examiner. Une absence de racine, de marqueur ou une jonction/lien symbolique fait refuser le test ; cela ne vaut jamais preuve de conservation. Le script ne lit pas le contenu des comptes dans les logs : il calcule seulement les empreintes.

## Avant publication

Ne publier qu’après examen des échecs/blocages et des fiches manuelles pertinentes. Un MSIX créé par MakeAppx, un EXE qui démarre hors package et une validation de Microsoft sont trois preuves différentes. Le Store doit encore accepter notamment `runFullTrust`, `unvirtualizedResources` et le comportement de Prism externe. La procédure de transition, de signature de test et de soumission personnelle reste dans STORE_RELEASE_RUNBOOK.md.
## Vérification effectuée pendant la préparation du kit

Le parcours complet du 07/11/2026 a produit `build/validation/20261107-205821-ba0754/rapport.html` : 12 contrôles PASS, 2 domaines NON EXÉCUTÉS. Les 91 tests JUnit exécutés et les 24 assertions de packaging passent. Le runtime, la vidéo, le fallback, les six écrans et le véritable EXE passent également sur cet essai. Installation signée, données après install/update/uninstall, Prism réel et Store restent à vérifier personnellement.

Test développeur facultatif du rapport (Node local, aucune dépendance à installer) :

```powershell
node tools/store/tests/report.test.cjs build/validation/20261107-205821-ba0754/rapport.html
```

Ce test vérifie l’affichage des fiches, l’export avec observations, l’import limité au bon package et la conservation des preuves automatiques.
