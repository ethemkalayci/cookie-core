# Utilisation de la distribution Windows

## Construire

JDK 25 Windows x64 requis pour la construction seulement. Depuis la racine de cette copie :

```powershell
$env:JAVA_HOME='C:\Users\kalay\.jdks\temurin-25.0.4.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test
.\gradlew.bat packageWindows '-PreleaseVersion=1.0.0'
.\gradlew.bat packageWindows '-PreleaseVersion=1.0.1'
$env:COOKIE_CORE_WINDOWS_SMOKE='true'
.\gradlew.bat test --tests '*WindowsUpdateSmokeTest' '-PreleaseVersion=1.0.1'
Remove-Item Env:COOKIE_CORE_WINDOWS_SMOKE
```

La tâche refuse de remplacer un dossier `build/windows-VERSION` existant. Déplacer ce dossier dans une sauvegarde ou partir d'un checkout propre pour reconstruire. Sorties : ZIP d'installation, ZIP app-image et image décompressée. Java 25 est inclus, JavaFX reste dans les bibliothèques de l'application.

## Première installation

Obtenir `CookieCore-1.0.0-setup.zip` depuis la release officielle et vérifier sa provenance avant d'exécuter un script. Extraire le ZIP dans un dossier temporaire puis :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install.ps1
```

L'installation copie les binaires dans `%LOCALAPPDATA%\Programs\CookieCore`, crée le superviseur et le raccourci **Cookie Core** dans le menu Démarrer. Aucun administrateur ni JDK utilisateur nécessaire. Lancer ensuite ce raccourci (pas directement l'EXE d'une version). Le script refuse une destination existante ; une installation interrompue doit être conservée/déplacée avant réessai. Les données `%LOCALAPPDATA%\CookiesStudio\CookieCore` restent séparées.

Après affichage du shell, vérification distante en arrière-plan ; téléchargement puis vérification de signature, taille, SHA-256 et extraction. Une mise à jour prête déclenche un compte à rebours de 10 secondes, différé tant que les workers d'écriture existants sont actifs. « Plus tard » la reporte au prochain lancement. Le superviseur attend la sortie, journalise `previous`/`pending`/`current`, lance l'image suivante et attend jusqu'à 90 secondes le shell sain. En cas d'échec, la version précédente reste installée ; la candidate est marquée rejetée pour éviter une boucle. Les anciennes images ne sont pas supprimées automatiquement.

## Confiance et publication 1.0.1

Le dépôt ne contient **aucune clé de production**. Un build sans ancre publique démarre mais refuse toute mise à jour signée. Ne pas distribuer ce build comme une version avec mise à jour opérationnelle.

L'éditeur doit fournir une paire Ed25519 conservée dans son environnement de signature : clé publique X.509 DER en base64, clé privée PKCS#8 DER en base64. Aucune clé privée n'a été créée ou utilisée pour publier pendant cette mission. Les tests génèrent une paire éphémère en mémoire et ne modifient que des copies temporaires des JAR.

Configurer, avec autorisation explicite :
- variable GitHub `COOKIE_CORE_UPDATE_KEYS` : format properties `identifiant=BASE64_CLE_PUBLIQUE` (plusieurs lignes possibles) ;
- variable `COOKIE_CORE_KEY_ID` : identifiant utilisé pour signer ;
- secret d'environnement protégé `release` : `COOKIE_CORE_SIGNING_KEY` ;
- protection de l'environnement `release` avec approbation humaine, tags protégés et accès restreint.

L'ancre publique doit être identique dans l'installateur 1.0.0 et les versions suivantes. Le script `SignRelease.java` signe les octets exacts de `release.json` et vérifie que la clé publique correspond. La signature détachée est `release.sig` (base64 sans saut de ligne). Les URL sont strictement limitées au dépôt application et au CDN GitHub de release.

Créer/pousser `v1.0.1` seulement après autorisation. Le workflow de tag construit et teste sans signer/publier. Pour publier, exécuter manuellement **Windows distribution**, saisir le tag existant `v1.0.1`, cocher `publish`, puis approuver l'environnement protégé. La release stable contient l'app-image ZIP, le setup ZIP, `release.json`, `release.sig`. Aucune PR ne reçoit de secret. Le script refuse une clé absente ou divergente. Une release existante n'est pas écrasée.

Rotation : embarquer d'avance plusieurs ancres publiques dans le bootstrap initial. Le bootstrap protocole 1 est immuable ; une nouvelle clé non préembarquée, la révocation d'une clé compromise ou une mise à niveau de son runtime exige un nouvel installateur de confiance. Ne pas supprimer la seule installation saine : installer à côté puis changer le raccourci. Une mise à jour du programme ne peut pas étendre seule la confiance du bootstrap. Le setup initial n'est pas signé Authenticode : sa provenance doit être vérifiée par un canal de confiance indépendant.

## Validation et diagnostic

Le test Windows utilise deux vrais app-images, un LOCALAPPDATA temporaire, une signature de test en mémoire et le vrai service de téléchargement alimenté par un transport HTTP simulé, puis une candidate préparée localement. Il couvre la fermeture JavaFX, le superviseur indépendant, le redémarrage sain, la récupération d'une transaction interrompue et une candidate qui ne démarre pas. Il ne simule pas une publication GitHub : le transport distant de bout en bout reste à vérifier avec une release autorisée. Résultat écrit dans `build/windows-smoke-evidence.txt`, rapports JUnit dans `build/test-results/test`.

Fichiers de diagnostic de l'installation : `application.log`, `current`, `previous`, `pending`, `confirmed`, `notice`, `rejected-VERSION`. Ne jamais éditer ces fichiers pendant que le programme/superviseur fonctionne. Une candidate rejetée ne sera pas retentée automatiquement ; publier une version supérieure corrigée. Après crash du superviseur avec l'application encore ouverte, fermer celle-ci puis relancer le raccourci.

Limites à conserver visibles : protocole/bootstrap immuable ; stockage des versions et des stagings échoués sans nettoyage automatique ; fermeture sûre couplée aux noms des workers recensés (ajouter un nouveau worker d'écriture à `ShutdownGate` lors de toute évolution) ; pas d'Authenticode ; aucune publication ni clé de production configurée dans cette session.


Preuve de première installation en isolation (commande exécutée ici) :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build\windows-1.0.0\installer\Install.ps1 -Destination "$PWD\build\initial-install-check\installation" -StartMenuDirectory "$PWD\build\initial-install-check\menu"
```

Le raccourci isolé a été inspecté et `Launch.vbs` exécuté avec LOCALAPPDATA temporaire : le superviseur a écrit `confirmed=1.0.0` après affichage réel du shell. Les processus de cette seule installation de test ont ensuite été fermés. Pour retester, choisir une nouvelle destination vide.
