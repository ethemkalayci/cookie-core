. "$PSScriptRoot\StorePackaging.ps1"
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$out = Join-Path $root ('build\store-tests\' + [guid]::NewGuid().ToString())
[IO.Directory]::CreateDirectory($out) | Out-Null
$checks = 0
function Assert([bool]$Condition,[string]$Message) { if (!$Condition) { throw $Message }; $script:checks++ }
function Assert-Rejected([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert $rejected 'Invalid configuration was accepted'
}
foreach ($version in @('0.1.0','1.0','01.0.0','1.0.0.0','1.0.0-beta','1.0.65536','65536.0.0','-1.0.0','1.0.0-SNAPSHOT')) {
    Assert-Rejected { Get-MsixVersion $version }
}
Assert ((Get-MsixVersion '1.0.0') -eq '1.0.0.0') 'Wrong Store version'
Assert ((Get-MsixVersion '65535.65535.65535') -eq '65535.65535.65535.0') 'Boundary version rejected'
Assert-Rejected { Read-StoreIdentity "$out\missing.json" }
[IO.File]::WriteAllText("$out\empty.json",'{"Name":"","Publisher":"","PublisherDisplayName":""}')
Assert-Rejected { Read-StoreIdentity "$out\empty.json" }
$identity = Read-StoreIdentity "$PSScriptRoot\identity.json"
Write-StoreManifest "$out\AppxManifest.xml" '1.0.0' $identity
[xml]$manifest = Get-Content -Raw "$out\AppxManifest.xml"
Assert ($manifest.Package.Identity.Name -ceq $identity.Name) 'Identity changed'
Assert ($manifest.Package.Identity.Publisher -ceq $identity.Publisher) 'Publisher changed'
Assert ($manifest.Package.Identity.ProcessorArchitecture -eq 'x64') 'Wrong architecture'
Assert ($manifest.Package.Identity.Version -eq '1.0.0.0') 'Wrong version'
Assert ($manifest.Package.Applications.Application.Executable -eq 'CookieCore.exe') 'Wrong executable'
Assert ($manifest.Package.Properties.FileSystemWriteVirtualization -eq 'disabled') 'Data virtualization enabled'
Assert (@($manifest.Package.Capabilities.Capability | Where-Object Name -eq 'unvirtualizedResources').Count -eq 1) 'Missing persistence capability'
# XML escaping must preserve literal identity text, never create elements.
$escaped = [pscustomobject]@{Name=$identity.Name;Publisher=$identity.Publisher;PublisherDisplayName='A & B <Test>'}
Write-StoreManifest "$out\escaped.xml" '1.2.3' $escaped
[xml]$xml = Get-Content -Raw "$out\escaped.xml"
Assert ($xml.Package.Properties.PublisherDisplayName -ceq 'A & B <Test>') 'Identity XML escaping failed'
New-StoreAssets "$root\src\main\resources\images\logo\logo-core.png" "$out\Assets"
foreach ($item in @(@('StoreLogo.png',50),@('Square44x44Logo.png',44),@('Square150x150Logo.png',150))) {
    $image = [Drawing.Image]::FromFile((Join-Path "$out\Assets" $item[0]))
    try { Assert ($image.Width -eq $item[1] -and $image.Height -eq $item[1]) 'Asset dimensions invalid' } finally { $image.Dispose() }
}
"PASS: $checks Store packaging checks" | Tee-Object -FilePath "$out\result.txt"