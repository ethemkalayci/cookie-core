param([Parameter(Mandatory)][string]$Version,[Parameter(Mandatory)][string]$Jdk)
$ErrorActionPreference='Stop'
if ($Version -notmatch '^(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})$') { throw 'Invalid release version' }
if (-not [Environment]::Is64BitOperatingSystem) { throw 'Windows x64 required' }
$out=Join-Path (Get-Location) "build\windows-$Version"
if(Test-Path $out){throw "Output already exists: $out (use a clean build or another version)"}
New-Item -ItemType Directory -Path $out | Out-Null
& "$Jdk\bin\jlink.exe" --add-modules java.se,jdk.crypto.ec,jdk.unsupported --strip-debug --no-header-files --no-man-pages --output "$out\runtime"
if($LASTEXITCODE){throw 'jlink failed'}
& "$Jdk\bin\jpackage.exe" --type app-image --name CookieCore --app-version $Version --input build/package-input --main-jar cookie-core.jar --main-class be.cookies.studio.core.PackagedLauncher --runtime-image "$out\runtime" --java-options '--enable-native-access=ALL-UNNAMED' --dest "$out\image"
if($LASTEXITCODE){throw 'jpackage failed'}
Set-Content -Encoding ascii -NoNewline -LiteralPath "$out\image\CookieCore\app\version.txt" -Value $Version
$bundle="$out\installer"
New-Item -ItemType Directory -Path "$bundle\payload" -Force | Out-Null
& "$PSScriptRoot\Create-ReleaseArchive.ps1" -Source "$out\image\CookieCore" -Destination "$out\CookieCore-$Version-windows-x64.zip" -Jdk $Jdk
Copy-Item "$out\image\CookieCore\*" "$bundle\payload" -Recurse
Copy-Item tools/update/Install.ps1,tools/update/Launch.vbs $bundle
Compress-Archive -Path "$bundle\*" -DestinationPath "$out\CookieCore-$Version-setup.zip"
Write-Host "Built: $out"



