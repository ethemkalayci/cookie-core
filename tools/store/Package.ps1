param(
 [Parameter(Mandatory)][string]$Version,
 [Parameter(Mandatory)][string]$Jdk,
 [ValidateSet('AppImage','Msix')][string]$Mode = 'Msix',
 [string]$IdentityFile = '',
 [string]$MakeAppx = '',
 [string]$OutputDirectory = ''
)
. "$PSScriptRoot\StorePackaging.ps1"
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (!$IdentityFile) { $IdentityFile = Join-Path $PSScriptRoot 'identity.json' }
$msixVersion = Get-MsixVersion $Version
if (![Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { throw 'Windows x64 required' }
if ($Mode -eq 'Msix') { $identity = Read-StoreIdentity $IdentityFile; $packer = Find-MakeAppx $MakeAppx }
$jdkInfo = Get-Content -Raw -LiteralPath "$Jdk\release"
if ($jdkInfo -notmatch 'JAVA_VERSION="25[.\"]' -or $jdkInfo -notmatch 'OS_ARCH="(amd64|x86_64)"') { throw 'JDK 25 x64 required' }
$out = if ($OutputDirectory) { [IO.Path]::GetFullPath($OutputDirectory) } else { Join-Path $root "build\store-$Version" }
if (!$out.StartsWith((Join-Path $root 'build') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must remain inside project build directory' }
if (Test-Path -LiteralPath $out) { throw "Output exists: $out. Preserve/review it, then move it manually before rebuilding." }
$inputDir = Join-Path $root 'build\package-input'
if (!(Test-Path "$inputDir\cookie-core.jar")) { throw 'Run Gradle packageInput first' }
[IO.Directory]::CreateDirectory($out) | Out-Null
& "$Jdk\bin\jlink.exe" --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.management --strip-debug --no-header-files --no-man-pages --output "$out\runtime"
if ($LASTEXITCODE) { throw 'jlink failed' }
& "$Jdk\bin\jpackage.exe" --type app-image --name CookieCore --app-version $Version --vendor 'Cookies Studio' --input $inputDir --main-jar cookie-core.jar --main-class be.cookies.studio.core.PackagedLauncher --runtime-image "$out\runtime" --java-options '--enable-native-access=ALL-UNNAMED' --dest "$out\image" --temp "$out\jpackage-temp"
if ($LASTEXITCODE) { throw 'jpackage failed' }
$image = Join-Path $out 'image\CookieCore'
foreach ($file in @('CookieCore.exe','app\cookie-core.jar','runtime\bin\java.exe','runtime\bin\server\jvm.dll')) {
    if (!(Test-Path -LiteralPath (Join-Path $image $file))) { throw "Incomplete app-image: $file" }
}
Test-AppImage $image $Version
if ($Mode -eq 'AppImage') { Write-Host "Built app-image: $image"; return }
Write-StoreManifest "$image\AppxManifest.xml" $Version $identity
New-StoreAssets "$root\src\main\resources\images\logo\logo-core.png" "$image\Assets"
$package = Join-Path $out "CookieCore-$msixVersion-x64.msix"
& $packer pack /d $image /p $package /o /h SHA256
if ($LASTEXITCODE) { throw 'MakeAppx validation/pack failed' }
& $packer unpack /p $package /d "$out\inspected" /o
if ($LASTEXITCODE) { throw 'MakeAppx inspection failed' }
# Compare every payload byte, not only the XML header.
foreach ($file in Get-ChildItem -LiteralPath $image -File -Recurse) {
    $relative = $file.FullName.Substring($image.Length + 1)
    $unpacked = Join-Path "$out\inspected" $relative
    if (!(Test-Path -LiteralPath $unpacked) -or (Get-PayloadHash $file.FullName) -ne (Get-PayloadHash $unpacked)) { throw "Payload mismatch: $relative" }
}
$hash = (Get-PayloadHash $package)
[IO.File]::WriteAllText("$package.sha256", "$hash  $([IO.Path]::GetFileName($package))`n")
[ordered]@{ Version=$msixVersion; Architecture='x64'; Identity=$identity; SHA256=$hash; MakeAppx=$packer; Jdk=$Jdk; PayloadVerified=$true; Signed=$false; StoreCertified=$false } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 "$out\build-report.json"
Write-Host "Built and unpack-verified (unsigned): $package"