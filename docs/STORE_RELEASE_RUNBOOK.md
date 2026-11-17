> **15 novembre 2026 — nouvelle fiche :** consulter MSIX_VALIDATION_20261115.md. Identité actuelle : EthemKalayci.CookiesCore (avec s). Les identifiants et liens produit du 7 novembre ci-dessous sont historiques ; ne pas les réutiliser pour la nouvelle soumission. Sous Windows PowerShell, entourer tout argument de version de guillemets : `"-PreleaseVersion=1.0.0"`. Le nouveau package est dans build/store-1.0.0 ; ne pas relancer le packaging sur ce dossier existant sans prévoir une nouvelle destination (le script refuse de l’écraser).

# Cookie Core — build local, vérification et publication Microsoft Store
> **Actualisation de validation :** le kit `TESTER-COOKIE-CORE.cmd` a reconstruit les sources actuelles et validé aussi le lancement natif. Dernier rapport : `build/validation/20261107-205821-ba0754/rapport.html`. Le nouveau MSIX est dans son sous-dossier `package/`, SHA-256 `f9bdcba0af29b7fe2492395ec743c5fd7f0c040964313af7c34cb85fb1f7fad9`. Le package et le blocage cités dans la livraison initiale ci-dessous sont conservés comme historique. L’installation MSIX et la recette Store ne sont toujours pas validées. Voir `TESTER_COOKIE_CORE.md` pour relancer tous les contrôles et remplir les 16 fiches de recette.

## Livrable du 07/11/2026

`build/store-1.0.0/CookieCore-1.0.0.0-x64.msix` : package réel, non signé, 42,7 Mo environ. Manifeste inspectable dans `build/store-1.0.0/image/CookieCore/AppxManifest.xml`, contenu extrait dans `inspected/`, SHA-256 et rapport JSON à côté du MSIX.

SHA-256 : `88e5e1c5b1ef646d90779df4c1640e7d8e473dee585a9fc0c3cb9d9d85fced2a`.

Identité fournie par le propriétaire, conservée dans `tools/store/identity.json` :
- Name : `EthemKalayci.CookieCore`
- Publisher : `CN=43B35A8D-2010-45CD-B4B9-91E2CF17643B`
- PublisherDisplayName : `Ethem Kalayci`
- PFN attendu fourni : `EthemKalayci.CookieCore_emh8npz9rmnfe` ; Store ID : `9NK2PW31JCF7`.

Ce package n’est pas certifié ni publié. Le lancement direct de l’EXE non signé a été bloqué par une stratégie Windows ; aucun réglage de sécurité n’a été changé. L’acceptation du MSIX par MakeAppx n’est pas une certification Store.

## Reproduire dans ce dossier, hors ligne

Ouvrir PowerShell dans `C:\Users\kalay\Desktop\core`. JDK 25 x64 et Windows SDK (MakeAppx x64) requis. Les caches locaux déjà copiés pour cette session sont dans `build/local-gradle`. Ne pas lancer `clean` : il supprimerait aussi caches, rapports et artefacts de cette intervention.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-25.0.4.1"
$env:GRADLE_USER_HOME = "$PWD\build\local-gradle"
$env:TEMP = "$PWD\build\local-temp"
$env:TMP = $env:TEMP
# LOCALAPPDATA est remplacé pour les tests, puis restauré dans finally.
$originalLocalAppData = $env:LOCALAPPDATA
$originalJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:LOCALAPPDATA = "$PWD\build\test-profile"
    $env:JAVA_TOOL_OPTIONS = '-Djava.net.useSystemProxies=false -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Dhttp.nonProxyHosts=localhost|127.*'
    .\gradlew.bat check --offline --no-daemon '-Dorg.gradle.java.installations.auto-download=false'
    if ($LASTEXITCODE) { throw 'Tests failed' }
} finally {
    $env:LOCALAPPDATA = $originalLocalAppData
    $env:JAVA_TOOL_OPTIONS = $originalJavaOptions
}
```

Le build exécuté avec succès est `gradlew.bat check packageMsix --offline --no-daemon -Dorg.gradle.java.installations.auto-download=false`, dans le même environnement isolé. `packageWindowsAppImage` construit seulement l’image. Les tâches refusent d’écraser `build/store-VERSION` : conserver ou déplacer personnellement ce dossier avant une reconstruction de même version. Une nouvelle version peut être préparée avec :

```powershell
.\gradlew.bat check packageMsix --offline --no-daemon -PreleaseVersion=1.0.1
```

La commande 1.0.1 est une instruction pour la prochaine version, pas une mise à jour Store déjà testée. Les valeurs d’identité peuvent être remplacées explicitement via `-PstoreIdentityFile=C:\chemin\identity.json`. Aucune valeur fictive n’est générée. La version est strictement MAJEUR.MINEUR.PATCH, major 1..65535, minor/patch 0..65535 ; le MSIX ajoute `.0`.

Les scripts de build utilisent RemoteSigned uniquement pour leur processus PowerShell, sans modification de politique système. Aucun script PowerShell, agent de test ou outil Gradle n’est inclus dans le MSIX.

## Test du runtime et de l’exécutable

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File tools/store/Test-AppImage.ps1 -Jdk $env:JAVA_HOME
```

Le test ouvre temporairement la GUI avec profil factice, proxy Java local fermé et PATH sans JDK. Il compile un harness hors package, observe la lecture MP4, le passage au shell, les six pages, puis le fallback d’une **copie** du JAR sans vidéo. Il tente ensuite le vrai `CookieCore.exe` avec agent de test externe ; le point d’entrée et les octets de l’app-image livrée restent inchangés. Un échec ou un blocage natif fait échouer la commande. `-SkipNative` permet de reproduire seulement les deux tests runtime ; le test natif reste alors explicitement NOT RUN. Captures et résultats : `build/app-image-smoke/<identifiant>/`.

Les erreurs de connexion au proxy fermé sont attendues pour les catalogues/annonces : elles vérifient le démarrage hors ligne et ne prouvent pas le fonctionnement en ligne. Le harness ne connecte aucun compte et ne télécharge pas Prism.

## Manifeste, capacités et données

Windows Desktop minimum 10.0.19041.0, x64, `packagedClassicApp` / `mediumIL`, `runFullTrust`. Java/JavaFX, runtime média, CSS/FXML et logo sont embarqués. Le runtime inclut `jdk.management`, nécessaire aux réglages de mémoire.

`desktop6:FileSystemWriteVirtualization=disabled` et `unvirtualizedResources` sont demandés pour conserver les données physiques sous `%LOCALAPPDATA%\CookiesStudio\CookieCore`, visibles de manière cohérente par Java et Prism externe et non capturées dans le stockage virtualisé supprimé avec le package. Préparer cette justification dans Partner Center ; cette capacité restreinte peut nécessiter une approbation. Ne pas la retirer pour faire passer une soumission sans revoir et tester la persistance.

Les dossiers personnalisés, settings/backup, comptes Prism, inventaires, opérations, caches, mods et sauvegardes ne sont ni déplacés ni supprimés. Cela reste à démontrer sur un **MSIX installé**, y compris désinstallation ; le succès des tests unitaires n’en est pas une preuve.

## Transition et retour arrière

1. Fermer l’ancien Cookie Core et attendre la fin des opérations Minecraft/modpacks. Sauvegarder localement l’intégralité du dossier de données et les dossiers d’instances personnalisés. Ne jamais joindre comptes/tokens aux diagnostics.
2. Conserver l’ancienne installation `%LOCALAPPDATA%\Programs\CookieCore` et son raccourci pour le retour arrière. La nouvelle version ne les efface pas.
3. Le nouveau lanceur prend `launcher.lock` dans les données et réserve `bootstrap.lock` / `app.lock` de l’ancienne installation standard quand son marqueur `current` existe. Une collision affiche une invitation à fermer l’autre version ; aucun processus n’est tué.
4. Limite : une ancienne installation située dans un emplacement personnalisé ou lancée hors de son superviseur n’est pas couverte par ces verrous historiques. La fermer manuellement avant de lancer la version Store. Ne jamais utiliser simultanément deux versions sur les mêmes données.
5. Vérifier les fichiers physiques vus par Cookie Core et Prism, tous les comptes factices/instances, thèmes et réglages avant de retirer personnellement l’ancien raccourci. Il n’existe aucune suppression automatique de l’ancien dossier.
6. Retour arrière : fermer la version Store, garder les sauvegardes, relancer personnellement l’ancienne installation. Tester d’abord sur une copie, ne pas fusionner aveuglément des comptes ou sauvegardes. Ne pas désinstaller le MSIX sur les données réelles avant le test de persistance de la matrice.

## Installation de test et certification — à faire personnellement

Non exécutées ici : l’installation MSIX et la confiance de certificat écrivent hors du projet et sortent du périmètre 100 % local au dossier. Aucun certificat ou clé privée n’a été créé ni importé.

Sur une VM Windows x64 propre, avec données factices : copier le MSIX, signer une **copie de test** avec un certificat de test dont le Subject correspond exactement au Publisher, utiliser les outils Windows officiels, et limiter la confiance à cette VM. Ne pas livrer cette copie comme package Store, ne pas importer une racine globale ni désactiver Defender/SAC. Suivre les instructions officielles de signature indiquées ci-dessous, puis installer cette copie avec Add-AppxPackage et relever le PFN installé. Exécuter la matrice avant toute utilisation réelle.

Tester version A puis B avec la même identité : paramètres et sauvegardes factices avant/après, visibilité Prism, répertoire sur autre volume, désinstallation. Le MSIX ne signe pas le `prismlauncher.exe` téléchargé séparément ; son acceptation par Windows est une vérification distincte.

## Publication personnelle et revue Git

1. Examiner `git status --short`, `git diff --stat`, `git diff` et les nouveaux fichiers (les fichiers non suivis n’apparaissent pas dans le diff classique). Ne pas inclure automatiquement les anciennes modifications utilisateur listées dans STORE_MIGRATION_REPORT.md.
2. Examiner le MSIX, son manifeste, son SHA-256, les rapports et les blocages. Le propriétaire choisit seul les fichiers à conserver, le commit et le push ; aucune commande distante n’a été exécutée ici.
3. Le workflow local `windows-release.yml` est manuel (`workflow_dispatch`) : tests, package non signé, artifacts et diagnostics ; aucun tag trigger, secret Ed25519, gh release, étape de soumission Store ou écriture du dépôt. Il reste non exécuté.
4. Dans Partner Center, ouvrir le produit `9NK2PW31JCF7`, vérifier les trois champs d’identité contre le manifeste, choisir une version supérieure aux versions déjà réservées, puis téléverser personnellement le MSIX non signé pour la soumission.
5. Compléter confidentialité, classification, captures, descriptions, licences/attributions de Java/JavaFX/Prism, explication de `runFullTrust` et `unvirtualizedResources`, comportement du composant Prism téléchargé. Rien n’a été vérifié en ligne dans cette session.
6. Lire les résultats de certification. Le Store signe et distribue après acceptation ; ne pas annoncer « approuvé » avant preuve. Valider ensuite une installation et une vraie mise à jour distribuées par le Store, y compris Prism sous SAC activé.
7. Seulement après revue, supprimer personnellement les anciens secrets/variables GitHub s’ils n’ont aucun autre usage. Ne pas supprimer les anciennes releases nécessaires aux anciens utilisateurs.

## Sources officielles conservées, non consultées en ligne pendant cette session

Les schémas XSD et MakeAppx du SDK 10.0.26100.0 installé ont servi à la validation locale. Les politiques actuelles de certification devront être vérifiées par le propriétaire :
- https://learn.microsoft.com/en-us/windows/apps/publish/view-app-identity-details
- https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-manual-conversion
- https://learn.microsoft.com/en-us/windows/msix/msix-containerization-overview
- https://learn.microsoft.com/en-us/windows/msix/package/sign-msix-package-guide
- https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options