Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Get-MsixVersion([string]$Version) {
    if ($Version -cnotmatch '^[1-9][0-9]{0,4}\.(0|[1-9][0-9]{0,4})\.(0|[1-9][0-9]{0,4})$') { throw 'Expected stable MAJOR.MINOR.PATCH, major > 0' }
    foreach ($part in $Version.Split('.')) { if ([int]$part -gt 65535) { throw 'MSIX version component exceeds 65535' } }
    return "$Version.0"
}
function Read-StoreIdentity([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Partner Center identity JSON is required (Name, Publisher, PublisherDisplayName)' }
    $identity = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
    foreach ($key in @('Name','Publisher','PublisherDisplayName')) {
        if (!$identity.PSObject.Properties[$key] -or [string]::IsNullOrWhiteSpace($identity.$key)) { throw "Missing Partner Center $key" }
        if ($identity.$key -ne $identity.$key.Trim()) { throw "Unexpected whitespace in $key" }
    }
    if ($identity.Name -cnotmatch '^[A-Za-z0-9.-]{3,50}$') { throw 'Invalid package identity Name' }
    if ($identity.Publisher -notmatch '^CN=') { throw 'Publisher must be the exact Partner Center distinguished name' }
    return $identity
}
function Write-StoreManifest([string]$Destination, [string]$Version, $Identity) {
    $msixVersion = Get-MsixVersion $Version
    $name = [Security.SecurityElement]::Escape($Identity.Name)
    $publisher = [Security.SecurityElement]::Escape($Identity.Publisher)
    $display = [Security.SecurityElement]::Escape($Identity.PublisherDisplayName)
    $xml = @"
<?xml version="1.0" encoding="utf-8"?>
<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
 xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
 xmlns:uap10="http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
 xmlns:desktop6="http://schemas.microsoft.com/appx/manifest/desktop/windows10/6"
 xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
 IgnorableNamespaces="uap uap10 desktop6 rescap">
 <Identity Name="$name" Publisher="$publisher" Version="$msixVersion" ProcessorArchitecture="x64" />
 <Properties>
  <DisplayName>Cookies Core</DisplayName><PublisherDisplayName>$display</PublisherDisplayName>
  <Description>Cookie Core - Cookies Studio</Description><Logo>Assets\StoreLogo.png</Logo>
  <desktop6:FileSystemWriteVirtualization>disabled</desktop6:FileSystemWriteVirtualization>
 </Properties>
 <Dependencies><TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.19041.0" MaxVersionTested="10.0.26100.0" /></Dependencies>
 <Resources><Resource Language="fr-FR" /></Resources>
 <Applications>
  <Application Id="CookieCore" Executable="CookieCore.exe" EntryPoint="Windows.FullTrustApplication" uap10:RuntimeBehavior="packagedClassicApp" uap10:TrustLevel="mediumIL">
   <uap:VisualElements DisplayName="Cookies Core" Description="Cookie Core - Cookies Studio" BackgroundColor="transparent" Square150x150Logo="Assets\Square150x150Logo.png" Square44x44Logo="Assets\Square44x44Logo.png" />
  </Application>
 </Applications>
 <Capabilities><rescap:Capability Name="runFullTrust" /><rescap:Capability Name="unvirtualizedResources" /></Capabilities>
</Package>
"@
    [IO.File]::WriteAllText($Destination, $xml, [Text.UTF8Encoding]::new($false))
}
function Find-MakeAppx([string]$ExplicitPath) {
    if ($ExplicitPath) { if (!(Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) { throw 'MakeAppx not found' }; return (Resolve-Path -LiteralPath $ExplicitPath).Path }
    $sdk = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    $candidate = Get-ChildItem -LiteralPath $sdk -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^10\.0\.\d+\.0$' } | Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'x64\makeappx.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (!$candidate) { throw 'Install Windows SDK with x64 MakeAppx (no automatic download)' }
    return $candidate
}
function New-StoreAssets([string]$Source, [string]$Destination) {
    Add-Type -AssemblyName System.Drawing
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    $image = [Drawing.Image]::FromFile($Source)
    try {
        foreach ($asset in @(@('StoreLogo.png',50),@('Square44x44Logo.png',44),@('Square150x150Logo.png',150))) {
            $size = [int]$asset[1]; $bitmap = [Drawing.Bitmap]::new($size,$size)
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([Drawing.Color]::Transparent)
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $ratio = [Math]::Min($size / $image.Width, $size / $image.Height)
                $width = [int]($image.Width * $ratio); $height = [int]($image.Height * $ratio)
                $graphics.DrawImage($image,[int](($size-$width)/2),[int](($size-$height)/2),$width,$height)
                $bitmap.Save((Join-Path $Destination $asset[0]),[Drawing.Imaging.ImageFormat]::Png)
            } finally { $graphics.Dispose(); $bitmap.Dispose() }
        }
    } finally { $image.Dispose() }
}
function Get-PayloadHash([string]$Path) {
    $stream = [IO.File]::OpenRead($Path); $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
function Test-AppImage([string]$Image,[string]$Version) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $Image 'app\cookie-core.jar'))
    try {
        $names = @($jar.Entries | ForEach-Object FullName)
        foreach ($required in @('be/cookies/studio/core/PackagedLauncher.class','be/cookies/studio/core/CookieCoreApp.class','be/cookies/studio/core/service/InstanceManager.class','be/cookies/studio/core/service/PrismRuntime.class','be/cookies/studio/core/service/LauncherSession.class','view/shell-view.fxml','videos/CoreIntro.mp4','css/style.css','version.txt')) {
            if ($names -cnotcontains $required) { throw "Missing application payload: $required" }
        }
        if (@($names | Where-Object { $_ -match '(^be/cookies/studio/core/update/|update-keys|release\.(json|sig)$|accounts\.json$|Install\.ps1$|Launch\.vbs$)' }).Count) { throw 'Legacy updater or account data present in payload' }
        $reader = [IO.StreamReader]::new($jar.GetEntry('version.txt').Open())
        try { if ($reader.ReadToEnd().Trim() -cne $Version) { throw 'Embedded version mismatch' } } finally { $reader.Dispose() }
    } finally { $jar.Dispose() }
    foreach ($module in @('base','controls','fxml','graphics','media')) {
        if (!(Test-Path -LiteralPath (Join-Path $Image "app\javafx-$module-25-win.jar"))) { throw "Missing JavaFX $module Windows runtime" }
    }
    if (@(Get-ChildItem -LiteralPath $Image -File -Recurse | Where-Object { $_.Name -match '^(accounts\.json|Install\.ps1|Launch\.vbs|release\.(json|sig))$' }).Count) { throw 'Unexpected user data/legacy installer in image' }
}
