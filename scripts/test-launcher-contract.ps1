param([switch]$SkipDownloads)

$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'start-server.ps1'
$source = [IO.File]::ReadAllText($launcher, [Text.UTF8Encoding]::new($false))
$tokens = $null
$parseErrors = $null
[Management.Automation.Language.Parser]::ParseFile($launcher, [ref]$tokens, [ref]$parseErrors) | Out-Null
if ($parseErrors.Count -ne 0) { throw "Launcher構文検査失敗: $($parseErrors[0].Message)" }

$plugins = @(
    @{ Name = 'Geyser'; Url = 'https://download.geysermc.org/v2/projects/geyser/versions/2.11.2/builds/1233/downloads/spigot'; Sha256 = 'a851adeb232e45644526ce16263e819ceb427a98f3919e5a97e6334b165c2f83' },
    @{ Name = 'Floodgate'; Url = 'https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138/downloads/spigot'; Sha256 = '44bdb908e2fb4ff1b974d5313d048a625a21555a9844cfb86256a98e8e1c6bd1' },
    @{ Name = 'ViaVersion'; Url = 'https://github.com/ViaVersion/ViaVersion/releases/download/5.10.0/ViaVersion-5.10.0.jar'; Sha256 = 'ab137b62829721c8ced3c554ede904a6c02f6d1963c33b32d7d432bb25607b60' },
    @{ Name = 'ViaBackwards'; Url = 'https://github.com/ViaVersion/ViaBackwards/releases/download/5.10.0/ViaBackwards-5.10.0.jar'; Sha256 = '107a6bce08b1661382b8590df7c0ab714bc5967a93c1bba2d71531448689ce82' }
)

foreach ($plugin in $plugins) {
    if (-not $source.Contains($plugin.Url) -or -not $source.Contains($plugin.Sha256)) { throw "$($plugin.Name)の固定URLまたはSHA-256がLauncherと一致しません。" }
}
if ($source -notmatch '/launcher/shutdown' -or $source -notmatch 'X-ASBP-Shutdown-Token' -or $source -notmatch 'StatusCode -eq 202') { throw '認証付きshutdown専用要求を確認できません。' }
if ($source -match "StandardInput\.Write" -or $source -match "Stop-Process") { throw 'stdin commandまたは強制killを検出しました。' }
if ($source -match "Get-ChildItem[^\r\n]+-Filter '\*\.jar'") { throw '全Plugin JARを対象にする危険な削除処理を検出しました。' }

if (-not $SkipDownloads) {
    $temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) "asbp-launcher-contract-$PID"
    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    try {
        foreach ($plugin in $plugins) {
            $target = Join-Path $temporaryDirectory "$($plugin.Name).jar"
            Invoke-WebRequest -Headers @{'User-Agent' = 'asbp-launcher-contract-test/0.1'} -Uri $plugin.Url -OutFile $target -TimeoutSec 120
            $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actual -ne $plugin.Sha256) { throw "$($plugin.Name)の公式配布JAR Hashが固定値と一致しません。" }
            Remove-Item -LiteralPath $target -Force
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryDirectory) {
            Get-ChildItem -LiteralPath $temporaryDirectory -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
            Remove-Item -LiteralPath $temporaryDirectory -Force
        }
    }
}

Write-Host 'Launcher contract test: PASS'
