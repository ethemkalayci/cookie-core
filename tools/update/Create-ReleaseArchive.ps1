param(
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Destination,
    [Parameter(Mandatory)][string]$Jdk
)
$ErrorActionPreference='Stop'
# The Windows PowerShell .NET ZIP writer can emit backslashes. The JDK writer
# always produces portable ZIP entry names, as required by the updater.
if(Test-Path -LiteralPath $Destination){throw "Archive already exists: $Destination"}
& "$Jdk\bin\jar.exe" --create --file $Destination --no-manifest -C $Source .
if($LASTEXITCODE){throw 'Release ZIP creation failed'}
