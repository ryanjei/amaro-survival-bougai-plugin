param([switch]$BuildOnly)

$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$launcher = Join-Path $PSScriptRoot 'start-server.ps1'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'scripts/start-server.ps1 was not found.' }
$utf8 = New-Object Text.UTF8Encoding($false)
$content = [IO.File]::ReadAllText($launcher, $utf8)
$script = [ScriptBlock]::Create($content)
& $script -BuildOnly:$BuildOnly -RepositoryRoot $repository
exit $LASTEXITCODE
