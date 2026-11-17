# Validation MSIX Cookie Core — 15 novembre 2026

## Résultat
Package construit, non signé, non soumis et non certifié.
Chemin : C:/Users/kalay/Desktop/core/build/store-1.0.0/CookieCore-1.0.0.0-x64.msix
SHA-256 : 2432d07a9974c22062bb47e2e018c2992c4a2dfb60376301bc22712c94f9bc04
Taille : 42077464 octets.

## Diagnostic démontré
Le fichier initial contenait une parenthèse manquante après project.version.toString(. La première reproduction échouait donc à compiler le script Kotlin.
Après correction, la commande Windows PowerShell avec -PreleaseVersion=1.0.0 sans guillemets reproduit Failed requirement : releaseVersion vaut exactement "1" (longueur 1). Avec l’argument complet entre guillemets, releaseVersion vaut "1.0.0" (longueur 5) et packageInput réussit. La regex et son échappement sont corrects ; matches rejette 1 et accepte 1.0.0. Aucun assouplissement des validations. Diagnostic temporaire retiré.
Commande correcte : .\gradlew.bat packageInput "-PreleaseVersion=1.0.0"

## Modifications de cette intervention
- build.gradle.kts : restauration de la parenthèse fermante uniquement.
- tools/store/StorePackaging.ps1 : rétablissement de unvirtualizedResources, exigé par FileSystemWriteVirtualization=disabled. runFullTrust et les paramètres de virtualisation sont conservés.
- Documentation : rapport présent, note de reprise et instructions de commande actualisées.
- Branche locale fix/msix-20261115 ; aucun commit, push, accès distant GitHub ou changement métier.
- identity.json inchangé. Les nombreuses autres modifications Git étaient déjà présentes.

## Identité inspectée dans le MSIX extrait
Name : EthemKalayci.CookiesCore
Publisher : CN=43B35A8D-2010-45CD-B4B9-91E2CF17643B
PublisherDisplayName : Ethem Kalayci
Version : 1.0.0.0 ; architecture x64 ; Windows.Desktop minimum 10.0.19041.0.
Comparaison avec identity.json : PASS. Comparaison avec Partner Center : BLOCKED, navigateur indisponible (CreateProcessSecurityEnvironment 0x8007000F). L’ancienne identité CookieCore sans s n’a pas été réutilisée.

## Tests effectués
| Contrôle | Résultat |
|---|---|
| packageInput et packageMsix via Wrapper Gradle 9.3.0 | PASS |
| JDK Temurin 25.0.4.1 x64 | PASS |
| MakeAppx 10.0.26100.9457, SDK 10.0.26100.0 | PASS |
| Validation MakeAppx, extraction et comparaison SHA-256 de chaque fichier | PASS, 230 fichiers empaquetés |
| Test-Packaging.ps1 | PASS, 24 assertions |
| Suite JUnit réexécutée hors ligne avec profil factice | PASS, 91 réussis, 2 ignorés, 0 échec/erreur |
| JavaFX embarqué, MP4 puis shell et six écrans | PASS |
| Copie de test sans MP4 : secours et six écrans | PASS |
| Véritable CookieCore.exe, MP4, six écrans et fermeture | PASS, app-image non installée |
| JAR, classes requises, JavaFX Windows, FXML, CSS, vidéo et version | PASS via Test-AppImage |
| Ancien package 1.0.1.0 conservé | PASS, SHA-256 conforme à son fichier .sha256 existant |

Les tests JavaFX utilisent des profils factices et bloquent le réseau Java : les erreurs de catalogue hors ligne dans smoke.log sont attendues. Ils ne prouvent pas le fonctionnement réseau ou l’authentification réelle. Les tests JUnit ont recompilé les sources après génération du package ; aucune source métier n’a été modifiée pendant cette intervention.

## Vérifications restantes
- Comparer les trois champs d’identité avec la nouvelle fiche Partner Center et vérifier que la version 1.0.0.0 est admissible dans cette fiche.
- Faire examiner/justifier les capacités restreintes runFullTrust et unvirtualizedResources. Le changement de catégorie ne vaut pas approbation de capacité.
- Installation du MSIX signé sur environnement de test, WACK applicable et démarrage depuis le menu Démarrer : NOT RUN.
- Prism réel, connexion Microsoft, téléchargement/installation/lancement Minecraft et modpack réel : NOT RUN ; nécessite compte légitime et recette intégrée.
- Persistance et visibilité physique des données entre Cookie Core/Prism après installation, mise à jour et désinstallation MSIX : NOT RUN. À effectuer sur données factices.
- Certification et distribution Store : NOT RUN.

Aucune installation MSIX, importation de certificat ou modification de politique de sécurité effectuée. Aucun ancien package supprimé. Ancien MSIX 1.0.1.0 : b7a24612e9535f7f663f6edf38164de0112682142c7ed31e0c68b284c81839a0.

## Sources officielles consultées
- https://learn.microsoft.com/en-us/uwp/schemas/appxpackage/uapmanifestschema/element-desktop6-filesystemwritevirtualization : capacité unvirtualizedResources obligatoire.
- https://learn.microsoft.com/en-us/windows/msix/desktop/flexible-virtualization : visibilité et persistance des données AppData ; retirer la désactivation de virtualisation nécessite une analyse et des tests de stockage.
- https://learn.microsoft.com/en-us/windows/msix/package/sign-msix-package-guide : pour la distribution Microsoft Store, signature prise en charge par Microsoft ; package de soumission non signé admis. Ceci ne permet pas une installation locale ordinaire du package non signé.

## Preuves locales
build/msix-20261115/version-diagnostic.json
build/msix-20261115/package-build.log
build/msix-20261115/tests.log
build/msix-20261115/smoke.log et smoke/{media,fallback,native}
build/store-1.0.0/build-report.json et inspected/AppxManifest.xml
build/test-results/test


## Correction après téléversement Partner Center
La capture du propriétaire montre le rejet de Package/Properties/DisplayName=Cookie Core : nom non réservé ; la nouvelle fiche est Cookies Core. Les deux DisplayName du manifeste sont corrigés en Cookies Core. Nouveau package : build/store-1.0.0-name-fixed/CookieCore-1.0.0.0-x64.msix. MakeAppx, extraction et intégrité PASS ; 24 tests packaging PASS. SHA-256 : b65111ebb6d9ee5e5323a76cf4c67f5afc7d7b84df7c25538a1f4ba6c5994e8a. Ancien package conservé. Identité, version et capacités inchangées. Acceptation Store encore à vérifier.
