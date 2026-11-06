param(
    [string]$Destination = (Join-Path $env:LOCALAPPDATA 'Programs\CookieCore'),
    [string]$StartMenuDirectory = [Environment]::GetFolderPath('Programs')
)
$ErrorActionPreference='Stop'
$Destination=[IO.Path]::GetFullPath($Destination)
$data=[IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'CookiesStudio\CookieCore'))
if($Destination.StartsWith($data,[StringComparison]::OrdinalIgnoreCase)){throw 'Installation in user data is forbidden'}
if(Test-Path $Destination){throw 'Destination exists. Choose an empty installation path; do not overwrite an installation.'}
for($p=Split-Path $Destination; $p; $p=Split-Path $p){if((Get-Item -LiteralPath $p -ErrorAction SilentlyContinue).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Reparse path forbidden'}}
$version=(Get-Content "$PSScriptRoot\payload\app\version.txt" -Raw).Trim()
if($version -notmatch '^(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})$'){throw 'Invalid version'}
New-Item -ItemType Directory -Path "$Destination\versions\$version" -Force | Out-Null
Copy-Item "$PSScriptRoot\payload\*" "$Destination\versions\$version" -Recurse
New-Item -ItemType Directory -Path "$Destination\bootstrap" | Out-Null
Copy-Item "$PSScriptRoot\payload\runtime","$PSScriptRoot\payload\app" "$Destination\bootstrap" -Recurse
Copy-Item "$PSScriptRoot\Launch.vbs" $Destination
Set-Content -Encoding ascii -NoNewline -LiteralPath "$Destination\current" -Value $version
New-Item -ItemType Directory -Force -Path $StartMenuDirectory | Out-Null
$shell=New-Object -ComObject WScript.Shell
$link=$shell.CreateShortcut((Join-Path $StartMenuDirectory 'Cookie Core.lnk'))
$link.TargetPath=Join-Path $env:WINDIR 'System32\wscript.exe'
$link.Arguments='"'+$Destination+'\Launch.vbs"'
$link.WorkingDirectory=$Destination
$link.IconLocation="$Destination\versions\$version\CookieCore.exe,0"
$link.Save()
Write-Host "Installed $version at $Destination. Start Cookie Core from the Start menu."
