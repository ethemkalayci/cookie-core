param([string]$Version='1.0.0',[string]$Jdk,[switch]$OpenReport,[switch]$SkipNative)
. "$PSScriptRoot\StorePackaging.ps1"
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$null = Get-MsixVersion $Version
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,6)
$run = Join-Path $root "build\validation\$runId"
[IO.Directory]::CreateDirectory($run) | Out-Null
$checks = [Collections.Generic.List[object]]::new()
$manualItems = Get-Content -Raw -Encoding UTF8 "$PSScriptRoot\validation-manual.json" | ConvertFrom-Json
$manualList = @(); foreach ($item in $manualItems) { $manualList += $item }
$state = [ordered]@{Schema=1;RunId=$runId;When=[DateTimeOffset]::Now.ToString('o');Version=$Version;Directory=$run;Package='';SHA256='';Checks=$checks;Manual=$manualList}
$envBefore=@{}; $savedLocation=Get-Location; $runLock=$null
function Save-Report {
    $json = $state | ConvertTo-Json -Depth 12
    [IO.File]::WriteAllText("$run\resultats.json",$json,[Text.UTF8Encoding]::new($false))
    $template = [IO.File]::ReadAllText("$PSScriptRoot\validation-report.html")
    $safeJson = $json.Replace('<','\u003c').Replace('>','\u003e').Replace('&','\u0026')
    [IO.File]::WriteAllText("$run\rapport.html",$template.Replace('__REPORT_JSON__',$safeJson),[Text.UTF8Encoding]::new($false))
}
function Add-Check([string]$Title,[string]$Status,[string]$Detail,[string]$Log='') {
    if ($Status -notin @('PASS','FAIL','BLOCKED','NOT_RUN')) { throw 'Invalid result status' }
    $checks.Add([ordered]@{Title=$Title;Status=$Status;Detail=$Detail;Log=$Log})
    Write-Host "[$Status] $Title - $Detail"
    Save-Report
}
function Run-Logged([string]$Executable,[string[]]$Arguments,[string]$Log) {
    $oldPreference=$ErrorActionPreference
    try {
        # Windows PowerShell wraps native stderr as ErrorRecord; use the process exit code.
        $ErrorActionPreference='Continue'
        & $Executable @Arguments *> (Join-Path $run $Log)
        $code=$LASTEXITCODE
        if ($null -eq $code) { return 1 }
        return [int]$code
    } finally { $ErrorActionPreference=$oldPreference }
}
function Source-Hashes {
    $map=@{}
    foreach ($file in Get-ChildItem -LiteralPath "$root\src" -File -Recurse) { $map[$file.FullName.Substring($root.Length+1)]=Get-PayloadHash $file.FullName }
    foreach ($name in @('build.gradle.kts','settings.gradle.kts','tools/store/identity.json','tools/store/Package.ps1','tools/store/StorePackaging.ps1')) { $map[$name]=Get-PayloadHash (Join-Path $root $name) }
    return $map
}
Save-Report
try {
    try { $runLock=[IO.File]::Open((Join-Path $root 'build\validation.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None) }
    catch { Add-Check 'Exécution exclusive' 'BLOCKED' 'Une autre validation est en cours. Relancer après sa fin.'; throw 'Validation already running' }
    Set-Location -LiteralPath $root
    $before=Source-Hashes
    $before | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$run\sources-avant.json" -Encoding UTF8
    if (!$Jdk) {
        $candidates=@()
        if ($env:JAVA_HOME) { $candidates+=$env:JAVA_HOME }
        if (Test-Path -LiteralPath "$env:USERPROFILE\.jdks") { $candidates+=@(Get-ChildItem -LiteralPath "$env:USERPROFILE\.jdks" -Directory | ForEach-Object FullName) }
        foreach ($candidate in $candidates) {
            if (Test-Path -LiteralPath "$candidate\release") {
                $release=Get-Content -Raw -LiteralPath "$candidate\release"
                if ($release -match 'JAVA_VERSION="25[.\"]' -and $release -match 'OS_ARCH="(amd64|x86_64)"') { $Jdk=$candidate; break }
            }
        }
    }
    $jdkReady=$false
    if ($Jdk -and (Test-Path -LiteralPath "$Jdk\bin\javac.exe")) {
        $release=Get-Content -Raw -LiteralPath "$Jdk\release"
        $jdkReady=$release -match 'JAVA_VERSION="25[.\"]' -and $release -match 'OS_ARCH="(amd64|x86_64)"'
    }
    if ($jdkReady) { Add-Check 'JDK 25 x64' 'PASS' $Jdk }
    else { Add-Check 'JDK 25 x64' 'BLOCKED' 'Fournir -Jdk vers un JDK 25 x64 installé. Aucun téléchargement automatique.' }
    $sdkReady=$false
    try { $sdk=Find-MakeAppx ''; $sdkReady=$true; Add-Check 'Windows SDK / MakeAppx' 'PASS' $sdk }
    catch { Add-Check 'Windows SDK / MakeAppx' 'BLOCKED' $_.Exception.Message }
    $gradleReady=@(Get-ChildItem -Path "$root\build\local-gradle\wrapper\dists\gradle-9.3.0-bin\*\gradle-9.3.0\bin\gradle.bat" -ErrorAction SilentlyContinue).Count -gt 0
    if ($gradleReady) { Add-Check 'Gradle local sans téléchargement' 'PASS' 'Wrapper 9.3.0 déjà présent dans build/local-gradle ; --offline imposé.' }
    else { Add-Check 'Gradle local sans téléchargement' 'BLOCKED' 'Cache build/local-gradle absent. Copier un cache local disponible avant de relancer ; aucun téléchargement lancé.' }
    foreach ($key in @('JAVA_HOME','GRADLE_USER_HOME','TEMP','TMP','LOCALAPPDATA','APPDATA','JAVA_TOOL_OPTIONS','COOKIE_CORE_WINDOWS_SMOKE','COOKIECORE_IMAGE_SMOKE')) { $envBefore[$key]=[Environment]::GetEnvironmentVariable($key,'Process') }
    [IO.Directory]::CreateDirectory("$run\temp") | Out-Null
    [IO.Directory]::CreateDirectory("$run\profile") | Out-Null
    $env:JAVA_HOME=$Jdk; $env:GRADLE_USER_HOME="$root\build\local-gradle"
    $env:TEMP="$run\temp"; $env:TMP=$env:TEMP; $env:LOCALAPPDATA="$run\profile"; $env:APPDATA=$env:LOCALAPPDATA
    $env:COOKIE_CORE_WINDOWS_SMOKE='false'; $env:COOKIECORE_IMAGE_SMOKE='false'
    $env:JAVA_TOOL_OPTIONS='-Djava.net.useSystemProxies=false -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Dhttp.nonProxyHosts=localhost|127.* "-Duser.home=' + "$run\profile" + '" "-Djava.io.tmpdir=' + "$run\temp" + '"'
    $ps="$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $psArgs=@('-NoProfile','-ExecutionPolicy','RemoteSigned','-File')
    $buildReady=$false
    if ($jdkReady -and $gradleReady) {
        Write-Host 'Compilation et suite de tests hors ligne...'
        $code=Run-Logged "$root\gradlew.bat" @('test','packageInput','--offline','--no-daemon','--rerun-tasks','-Dorg.gradle.java.installations.auto-download=false',"-PreleaseVersion=$Version") 'gradle.log'
        if ($code -eq 0) {
            $total=0; $failed=0; $skipped=0
            $xmlFiles=@(Get-ChildItem -LiteralPath "$root\build\test-results\test" -Filter 'TEST-*.xml')
            foreach ($file in $xmlFiles) { [xml]$xml=Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName; $total+=[int]$xml.testsuite.tests; $failed+=[int]$xml.testsuite.failures+[int]$xml.testsuite.errors; $skipped+=[int]$xml.testsuite.skipped }
            Copy-Item -LiteralPath "$root\build\test-results\test" -Destination "$run\junit" -Recurse
            if ($total -gt 0 -and $failed -eq 0) { $buildReady=$true; Add-Check 'Compilation et tests JUnit' 'PASS' "$($total-$skipped) réussis ; $skipped ignorés sur $total. Résultats fraîchement exécutés." 'gradle.log' }
            else { Add-Check 'Compilation et tests JUnit' 'FAIL' 'Rapports absents ou tests en échec.' 'gradle.log' }
        } else { Add-Check 'Compilation et tests JUnit' 'FAIL' "Gradle retourne $code ; voir le journal (cache manquant possible en mode hors ligne)." 'gradle.log' }
    } else { Add-Check 'Compilation et tests JUnit' 'NOT_RUN' 'Prérequis local manquant.' }
    $code=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Test-Packaging.ps1")) 'packaging-tests.log'
    if ($code -eq 0) { Add-Check 'Versions, identité, manifeste et assets' 'PASS' 'Tests de packaging positifs et négatifs réussis.' 'packaging-tests.log' }
    else { Add-Check 'Versions, identité, manifeste et assets' 'FAIL' "Code $code." 'packaging-tests.log' }
    $packageReady=$false
    if ($buildReady -and $sdkReady) {
        Write-Host 'Construction du MSIX de cette validation...'
        $code=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Package.ps1",'-Version',$Version,'-Jdk',$Jdk,'-IdentityFile',"$PSScriptRoot\identity.json",'-OutputDirectory',"$run\package")) 'package.log'
        if ($code -eq 0) {
            $build=Get-Content -Raw -Encoding UTF8 "$run\package\build-report.json" | ConvertFrom-Json
            $state.Package=Join-Path "$run\package" "CookieCore-$(Get-MsixVersion $Version)-x64.msix"
            $state.SHA256=Get-PayloadHash $state.Package
            if ($state.SHA256 -cne $build.SHA256) { throw 'New package hash mismatch' }
            $packageReady=$true
            Add-Check 'MSIX neuf, schéma, extraction et intégrité' 'PASS' 'MakeAppx pack/unpack et comparaison de chaque fichier ; JAR contrôlé sans ancien updater ni comptes.' 'package.log'
            Add-Check 'Signature et installation MSIX' 'NOT_RUN' 'Package non signé. Signature, confiance et installation à effectuer personnellement sur une machine de test.'
        } else { Add-Check 'Construction et inspection MSIX' 'FAIL' "Code $code. Le package précédent reste intact." 'package.log' }
    } else { Add-Check 'Construction et inspection MSIX' 'NOT_RUN' 'Compilation ou SDK non disponible.' }
    if ($packageReady) {
        Write-Host 'Tests JavaFX : des fenêtres de test vont sʼouvrir puis se fermer.'
        $arguments=$psArgs+@("$PSScriptRoot\Test-AppImage.ps1",'-Version',$Version,'-Jdk',$Jdk,'-ImageDirectory',"$run\package\image\CookieCore",'-EvidenceDirectory',"$run\smoke")
        if ($SkipNative) { $arguments+='-SkipNative' }
        $code=Run-Logged $ps $arguments 'smoke.log'
        foreach ($mode in @('media','fallback')) {
            $result="$run\smoke\$mode\result.txt"
            if ((Test-Path -LiteralPath $result) -and (Get-Content -Raw -LiteralPath $result) -match '^PASS:') { Add-Check "Runtime JavaFX / $mode / six écrans" 'PASS' 'Test sur le runtime du package ; données factices ; réseau bloqué.' "smoke/$mode/result.txt" }
            else { Add-Check "Runtime JavaFX / $mode" 'FAIL' 'Aucune preuve de réussite pour cette phase.' 'smoke.log' }
        }
        $native="$run\smoke\native\native-result.txt"
        if ($SkipNative) { Add-Check 'Véritable CookieCore.exe' 'NOT_RUN' 'Option -SkipNative explicitement demandée.' }
        elseif (Test-Path -LiteralPath $native) {
            $text=Get-Content -Raw -LiteralPath $native
            if ($text -match '^PASS:' -and $code -eq 0) { Add-Check 'Véritable CookieCore.exe' 'PASS' $text 'smoke/native/native-result.txt' }
            elseif ($text -match '^BLOCKED:') { Add-Check 'Véritable CookieCore.exe' 'BLOCKED' $text 'smoke/native/native-result.txt' }
            else { Add-Check 'Véritable CookieCore.exe' 'FAIL' $text 'smoke.log' }
        } else { Add-Check 'Véritable CookieCore.exe' 'NOT_RUN' "Pas de preuve native ; script terminé avec le code $code." 'smoke.log' }
    } else { Add-Check 'Runtime JavaFX et EXE natif' 'NOT_RUN' 'Le package neuf nʼa pas pu être construit.' }
    # A synthetic baseline plus a deliberately damaged copy verifies the data checker itself.
    $fixture="$run\fixtures\data"
    foreach ($directory in @('prism-data','inventory','operations','prism-runtime-11.1.1','instances\Test\.minecraft\saves\ValidationWorld','instances\Test\.minecraft\mods','instances\Test\.minecraft\shaderpacks','instances\Test\.minecraft\resourcepacks')) { [IO.Directory]::CreateDirectory((Join-Path $fixture $directory)) | Out-Null }
    [IO.File]::WriteAllText("$fixture\.cookiecore-test-data",'SYNTHETIC COOKIE CORE VALIDATION DATA')
    [IO.File]::WriteAllText("$fixture\settings.json",'{"validationSentinel":"settings-before-update"}')
    [IO.File]::WriteAllText("$fixture\settings.previous.json",'{"validationSentinel":"settings-backup"}')
    [IO.File]::WriteAllText("$fixture\prism-data\accounts.json",'{"accounts":[]}')
    foreach ($directory in Get-ChildItem -LiteralPath $fixture -Directory -Recurse) { [IO.File]::WriteAllText((Join-Path $directory.FullName 'validation-sentinel.txt'),'Synthetic preservation sentinel; not a real Minecraft save or mod.') }
    $snapshot="$run\fixtures\baseline.json"
    $baselineCode=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Test-DataPreservation.ps1",'-Mode','Baseline','-DataRoot',$fixture,'-Snapshot',$snapshot)) 'data-baseline.log'
    $compareCode=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Test-DataPreservation.ps1",'-Mode','Compare','-DataRoot',$fixture,'-Snapshot',$snapshot)) 'data-compare.log'
    if ($baselineCode -eq 0 -and $compareCode -eq 0) { Add-Check 'Comparateur de données : fichiers inchangés' 'PASS' 'Empreintes des fichiers synthétiques identiques ; aucune donnée personnelle lue.' 'data-compare.log' }
    else { Add-Check 'Comparateur de données : fichiers inchangés' 'FAIL' 'La capture/comparaison de référence échoue.' 'data-compare.log' }
    $damaged="$run\fixtures\damaged"
    Copy-Item -LiteralPath $fixture -Destination $damaged -Recurse
    $damagedSnapshot="$run\fixtures\damaged-baseline.json"
    $baselineCode=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Test-DataPreservation.ps1",'-Mode','Baseline','-DataRoot',$damaged,'-Snapshot',$damagedSnapshot)) 'data-negative-baseline.log'
    [IO.File]::WriteAllText("$damaged\settings.json",'intentional test corruption')
    Remove-Item -LiteralPath "$damaged\inventory\validation-sentinel.txt"
    $negativeCode=Run-Logged $ps ($psArgs+@("$PSScriptRoot\Test-DataPreservation.ps1",'-Mode','Compare','-DataRoot',$damaged,'-Snapshot',$damagedSnapshot)) 'data-negative.log'
    $negativeFiles=@(Get-ChildItem -LiteralPath "$run\fixtures" -Filter 'damaged-baseline.json.comparison-*.json')
    if ($baselineCode -eq 0 -and $negativeCode -eq 1 -and $negativeFiles.Count -eq 1) {
        $negative=Get-Content -Raw -Encoding UTF8 -LiteralPath $negativeFiles[0].FullName | ConvertFrom-Json
        if ($negative.Status -eq 'FAIL' -and @($negative.Changed).Count -eq 1 -and @($negative.Missing).Count -eq 1) { Add-Check 'Comparateur : corruption et perte détectées' 'PASS' 'Une modification et une suppression volontaires détectées sur une copie jetable.' 'data-negative.log' }
        else { Add-Check 'Comparateur : corruption et perte détectées' 'FAIL' 'Résultat différent des deux altérations attendues.' 'data-negative.log' }
    } else { Add-Check 'Comparateur : corruption et perte détectées' 'FAIL' 'Le contrôle négatif ne détecte pas correctement les pertes.' 'data-negative.log' }
    $after=Source-Hashes; $changed=@()
    foreach ($name in $before.Keys) { if (!$after.ContainsKey($name) -or $before[$name] -cne $after[$name]) { $changed+=$name } }
    foreach ($name in $after.Keys) { if (!$before.ContainsKey($name)) { $changed+=$name } }
    if ($changed.Count) { Add-Check 'Sources stables pendant la validation' 'BLOCKED' ('Sources modifiées pendant le test ; relancer pour un résultat cohérent : '+($changed -join ', ')) }
    else { Add-Check 'Sources stables pendant la validation' 'PASS' 'Sources et configuration inchangées entre début et fin de cette exécution.' }
    Add-Check 'Installation, Prism réel et Store' 'NOT_RUN' '16 scénarios guidés à renseigner dans le rapport ; aucun accès distant ni installation automatique.'
} catch {
    if (!@($checks | Where-Object { $_.Title -eq 'Exécution exclusive' -and $_.Status -eq 'BLOCKED' }).Count) { Add-Check 'Exécution du validateur' 'FAIL' $_.Exception.Message }
} finally {
    foreach ($key in $envBefore.Keys) { [Environment]::SetEnvironmentVariable($key,$envBefore[$key],'Process') }
    Set-Location $savedLocation
    if ($null -ne $runLock) { $runLock.Dispose() }
    Save-Report
    [IO.File]::WriteAllText((Join-Path $root 'build\DERNIERE-VALIDATION.txt'),"$run\rapport.html",[Text.UTF8Encoding]::new($false))
    Write-Host "Rapport : $run\rapport.html"
    if ($OpenReport) {
        try { $open=[Diagnostics.ProcessStartInfo]::new("$run\rapport.html"); $open.UseShellExecute=$true; [Diagnostics.Process]::Start($open) | Out-Null }
        catch { Write-Host 'Ouverture automatique indisponible : ouvrir le fichier rapport.html manuellement.' }
    }
}
if (@($checks | Where-Object Status -eq 'FAIL').Count) { exit 1 }
if (@($checks | Where-Object Status -eq 'BLOCKED').Count) { exit 2 }
exit 0