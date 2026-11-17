param(
 [Parameter(Mandatory)][ValidateSet('Baseline','Compare')][string]$Mode,
 [Parameter(Mandatory)][string]$DataRoot,
 [Parameter(Mandatory)][string]$Snapshot
)
. "$PSScriptRoot\StorePackaging.ps1"
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$data = (Resolve-Path -LiteralPath $DataRoot).Path.TrimEnd('\')
$snapshotPath = [IO.Path]::GetFullPath($Snapshot)
if (!$snapshotPath.StartsWith((Join-Path $project 'build') + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Snapshot must be inside project build directory' }
if ($snapshotPath.StartsWith($data+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Snapshot must be outside the data being measured' }
if (!(Test-Path -LiteralPath "$data\.cookiecore-test-data" -PathType Leaf)) { throw 'Refused: test-data marker missing. Never use real player data.' }
if ((Get-Content -Raw -LiteralPath "$data\.cookiecore-test-data").Trim() -cne 'SYNTHETIC COOKIE CORE VALIDATION DATA') { throw 'Invalid test-data marker' }
$files = @{}
$pending = [Collections.Generic.Stack[string]]::new(); $pending.Push($data)
while ($pending.Count) {
    $directory = $pending.Pop()
    if ((Get-Item -LiteralPath $directory -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refused: junction or symlink in test data' }
    foreach ($item in Get-ChildItem -LiteralPath $directory -Force) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refused: junction or symlink in test data' }
        if ($item.PSIsContainer) { $pending.Push($item.FullName); continue }
        if ($item.Name -eq 'launcher.lock') { continue }
        $relative = $item.FullName.Substring($data.Length+1)
        $files[$relative] = Get-PayloadHash $item.FullName
    }
}
if ($Mode -eq 'Baseline') {
    if (Test-Path -LiteralPath $snapshotPath) { throw 'Baseline already exists; never overwrite the before-state' }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($snapshotPath)) | Out-Null
    [ordered]@{Schema=1;DataRoot=$data;Files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
    Write-Host "PASS: baseline captured ($($files.Count) synthetic files)."
    exit 0
}
$before = Get-Content -Raw -Encoding UTF8 -LiteralPath $snapshotPath | ConvertFrom-Json
if ($before.Schema -ne 1 -or $before.DataRoot -ine $data) { throw 'Snapshot belongs to another data root or schema' }
$changed=@(); $missing=@(); $added=@()
foreach ($entry in $before.Files.PSObject.Properties) {
    if (!$files.ContainsKey($entry.Name)) { $missing += $entry.Name }
    elseif ($files[$entry.Name] -cne $entry.Value) { $changed += $entry.Name }
}
foreach ($name in $files.Keys) { if (!$before.Files.PSObject.Properties[$name]) { $added += $name } }
$status = if ($changed.Count -or $missing.Count) { 'FAIL' } else { 'PASS' }
$result = [ordered]@{Status=$status;Changed=$changed;Missing=$missing;Added=$added;Note='Only baseline files must remain identical; additions are reported separately. No installed-MSIX claim.'}
$resultPath = "$snapshotPath.comparison-$([guid]::NewGuid().ToString('N')).json"
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $resultPath -Encoding UTF8
Write-Host "$status : $($changed.Count) changed, $($missing.Count) missing, $($added.Count) added. $resultPath"
if ($status -ne 'PASS') { exit 1 }