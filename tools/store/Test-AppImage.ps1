param([string]$Version='1.0.0',[Parameter(Mandatory)][string]$Jdk,[switch]$SkipNative,[string]$ImageDirectory,[string]$EvidenceDirectory)
. "$PSScriptRoot\StorePackaging.ps1"
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$image = if ($ImageDirectory) { [IO.Path]::GetFullPath($ImageDirectory) } else { Join-Path $root "build\store-$Version\image\CookieCore" }
Test-AppImage $image $Version
$out = if ($EvidenceDirectory) { [IO.Path]::GetFullPath($EvidenceDirectory) } else { Join-Path $root ('build\app-image-smoke\' + [guid]::NewGuid().ToString()) }
if (!$out.StartsWith((Join-Path $root 'build') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence must remain inside project build directory' }
if (Test-Path -LiteralPath $out) { throw 'Evidence directory already exists; use a new directory' }
[IO.Directory]::CreateDirectory("$out\classes") | Out-Null
& "$Jdk\bin\javac.exe" -encoding UTF-8 -cp "$image\app\*" -d "$out\classes" "$PSScriptRoot\AppImageSmoke.java"
if ($LASTEXITCODE) { throw 'Smoke harness compilation failed' }
# All app writes stay in synthetic per-run directories. Proxies prevent network access.
# No identity, token, personal instance or real account is copied into this test.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fallbackJar = Join-Path $out 'without-intro.jar'
Copy-Item -LiteralPath "$image\app\cookie-core.jar" -Destination $fallbackJar
$zip = [IO.Compression.ZipFile]::Open($fallbackJar,[IO.Compression.ZipArchiveMode]::Update)
try { $zip.GetEntry('videos/CoreIntro.mp4').Delete() } finally { $zip.Dispose() }
foreach ($fallback in @($false,$true)) {
    $mode = if ($fallback) { 'fallback' } else { 'media' }
    $profile = Join-Path $out $mode
    [IO.Directory]::CreateDirectory("$profile\temp") | Out-Null
    $mainJar = if ($fallback) { $fallbackJar } else { "$image\app\cookie-core.jar" }
    $deps = @(Get-ChildItem -LiteralPath "$image\app" -Filter '*.jar' | Where-Object Name -ne 'cookie-core.jar' | ForEach-Object FullName)
    $classpath = (@("$out\classes",$mainJar)+$deps) -join ';'
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = "$image\runtime\bin\java.exe"
    $start.UseShellExecute = $false
    $start.WorkingDirectory = $profile
    $start.Arguments = '--enable-native-access=ALL-UNNAMED -Djava.net.useSystemProxies=false -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 "-Djava.io.tmpdir=' + "$profile\temp" + '" "-Duser.home=' + $profile + '" -cp "' + $classpath + '" AppImageSmoke "' + $profile + '" ' + $fallback.ToString().ToLowerInvariant()
    foreach ($key in @('LOCALAPPDATA','APPDATA','USERPROFILE')) { $start.EnvironmentVariables[$key]=$profile }
    foreach ($key in @('TEMP','TMP')) { $start.EnvironmentVariables[$key]="$profile\temp" }
    $start.EnvironmentVariables['PATH']="$env:SystemRoot\System32"
    $start.EnvironmentVariables.Remove('JAVA_HOME'); $start.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS')
    $process = [Diagnostics.Process]::Start($start)
    if (!$process.WaitForExit(60000)) { $process.Kill(); throw 'Isolated smoke JVM timed out' }
    if ($process.ExitCode) { throw "Packaged runtime $mode smoke failed" }
    Get-Content "$profile\result.txt"
}
if ($SkipNative) { Write-Host "NOT RUN: native EXE (explicit -SkipNative); runtime evidence: $out"; return }
# Run the real, unmodified jpackage executable with a test-only JVM agent.
[IO.File]::WriteAllText("$out\agent.mf", "Premain-Class: AppImageSmoke`n`n")
& "$Jdk\bin\jar.exe" --create --file "$out\smoke-agent.jar" --manifest "$out\agent.mf" -C "$out\classes" .
if ($LASTEXITCODE) { throw 'Smoke agent packaging failed' }
$profile = Join-Path $out 'native'
[IO.Directory]::CreateDirectory("$profile\temp") | Out-Null
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = "$image\CookieCore.exe"
$start.UseShellExecute = $false
$start.WorkingDirectory = $profile
foreach ($key in @('LOCALAPPDATA','APPDATA','USERPROFILE')) { $start.EnvironmentVariables[$key]=$profile }
foreach ($key in @('TEMP','TMP')) { $start.EnvironmentVariables[$key]="$profile\temp" }
$start.EnvironmentVariables['PATH']="$env:SystemRoot\System32"
$start.EnvironmentVariables.Remove('JAVA_HOME')
$start.EnvironmentVariables['JAVA_TOOL_OPTIONS']='"-javaagent:' + "$out\smoke-agent.jar=$profile" + '" "-Duser.home=' + $profile + '" "-Djava.io.tmpdir=' + "$profile\temp" + '" -Djava.net.useSystemProxies=false -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9'
try { $process = [Diagnostics.Process]::Start($start) }
catch {
    [IO.File]::WriteAllText("$profile\native-result.txt", "BLOCKED: native process could not start. " + $_.Exception.Message)
    throw
}
if (!$process.WaitForExit(60000)) { $process.Kill(); throw 'Native smoke timed out' }
if ($process.ExitCode -or !(Test-Path "$profile\native-result.txt") -or (Get-Content -Raw "$profile\native-result.txt") -notmatch '^PASS:') { throw 'Native launcher smoke failed' }
Get-Content "$profile\native-result.txt"
Write-Host "Smoke evidence: $out"