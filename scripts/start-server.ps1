param([switch]$BuildOnly, [string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new()

$repository = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) { (Resolve-Path (Join-Path $PSScriptRoot '..')).Path } else { (Resolve-Path $RepositoryRoot).Path }
$runtime = Join-Path $repository '.runtime\paper'
$runtimeLogs = Join-Path $repository '.runtime\logs'
$launcherLog = Join-Path $runtimeLogs 'launcher-latest.log'
$paperJar = Join-Path $runtime 'paper.jar'
$paperMetadata = Join-Path $runtime 'paper-version.properties'
$plugins = Join-Path $runtime 'plugins'
$deployedPlugin = Join-Path $plugins 'amaro-survival-bougai-plugin.jar'
$shutdownHandoff = Join-Path $plugins 'AmaroSurvivalBougaiPlugin\launcher-shutdown.token'
$paperVersion = '26.2'
$paperBuild = '112'
$paperApi = "https://fill.papermc.io/v3/projects/paper/versions/$paperVersion/builds"
$userAgent = 'amaro-survival-bougai-launcher/0.1 (https://github.com/ryanjei/amaro-survival-bougai-plugin)'
$runtimePluginDefinitions = @(
    @{ DisplayName = 'Geyser'; FileName = 'Geyser-Spigot.jar'; Version = '2.11.2 build 1233'; Url = 'https://download.geysermc.org/v2/projects/geyser/versions/2.11.2/builds/1233/downloads/spigot'; Sha256 = 'a851adeb232e45644526ce16263e819ceb427a98f3919e5a97e6334b165c2f83' },
    @{ DisplayName = 'Floodgate'; FileName = 'floodgate-spigot.jar'; Version = '2.2.5 build 138'; Url = 'https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138/downloads/spigot'; Sha256 = '44bdb908e2fb4ff1b974d5313d048a625a21555a9844cfb86256a98e8e1c6bd1' },
    @{ DisplayName = 'ViaVersion'; FileName = 'ViaVersion.jar'; Version = '5.10.0'; Url = 'https://github.com/ViaVersion/ViaVersion/releases/download/5.10.0/ViaVersion-5.10.0.jar'; Sha256 = 'ab137b62829721c8ced3c554ede904a6c02f6d1963c33b32d7d432bb25607b60' },
    @{ DisplayName = 'ViaBackwards'; FileName = 'ViaBackwards.jar'; Version = '5.10.0'; Url = 'https://github.com/ViaVersion/ViaBackwards/releases/download/5.10.0/ViaBackwards-5.10.0.jar'; Sha256 = '107a6bce08b1661382b8590df7c0ab714bc5967a93c1bba2d71531448689ce82' }
)
$paperProcess = $null
$transcriptStarted = $false

function Show-Step([string]$message) { Write-Host "[ASBP] $message" -ForegroundColor Cyan }
function Stop-WithMessage([string]$message) { throw $message }
function Get-Sha256([string]$path) {
    $stream = [IO.File]::OpenRead($path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
}
function Get-JavaMajor([string]$javaExe) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $javaExe
    $info.Arguments = '-version'
    $info.UseShellExecute = $false
    $info.RedirectStandardError = $true
    $info.RedirectStandardOutput = $true
    $info.CreateNoWindow = $true
    $process = [Diagnostics.Process]::Start($info)
    $versionText = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or $versionText -notmatch 'version "(?<major>\d+)') { return $null }
    return [int]$Matches.major
}
function Get-JavaHome([string]$javaExe) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $javaExe
    $info.Arguments = '-XshowSettings:properties -version'
    $info.UseShellExecute = $false
    $info.RedirectStandardError = $true
    $info.RedirectStandardOutput = $true
    $info.CreateNoWindow = $true
    $process = [Diagnostics.Process]::Start($info)
    $settings = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or $settings -notmatch '(?m)^\s*java\.home\s*=\s*(?<home>.+?)\s*$') { return $null }
    $resolvedJavaHome = $Matches.home.Trim()
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedJavaHome 'bin\java.exe'))) { return $null }
    return $resolvedJavaHome
}
function Find-Java25 {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $null = $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) { $null = $candidates.Add($command.Source) }
    @('C:\Program Files\Amazon Corretto', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', (Join-Path $env:USERPROFILE '.jdks')) | ForEach-Object {
        if (Test-Path -LiteralPath $_) {
            Get-ChildItem -LiteralPath $_ -Directory -ErrorAction SilentlyContinue | Where-Object Name -Match '25' | ForEach-Object { $null = $candidates.Add((Join-Path $_.FullName 'bin\java.exe')) }
        }
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $candidate) {
            try { if ((Get-JavaMajor $candidate) -eq 25) { return $candidate } } catch { continue }
        }
    }
    return $null
}
function Read-PaperMetadata {
    if (-not (Test-Path -LiteralPath $paperMetadata)) { return $null }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $paperMetadata) {
        if ($line -match '^([^=]+)=(.*)$') { $values[$Matches[1]] = $Matches[2] }
    }
    return $values
}
function Test-CachedPaper {
    if (-not (Test-Path -LiteralPath $paperJar)) { return $false }
    $metadata = Read-PaperMetadata
    if ($null -eq $metadata) { return $false }
    if ($metadata.version -ne $paperVersion -or $metadata.build -ne $paperBuild -or [string]::IsNullOrWhiteSpace($metadata.sha256)) { return $false }
    return (Get-Sha256 $paperJar) -eq $metadata.sha256
}
function Install-Paper {
    Show-Step "Paper $paperVersion build $paperBuild を公式配布元から取得・検証しています。"
    try { $builds = Invoke-RestMethod -Headers @{'User-Agent' = $userAgent} -Uri $paperApi -TimeoutSec 30 }
    catch { Stop-WithMessage 'Paper build情報の取得に失敗しました。ネットワークとPaperMCへの接続を確認してください。' }
    $build = $builds | Where-Object { $_.id.ToString() -eq $paperBuild -and $_.channel -eq 'STABLE' } | Select-Object -First 1
    if ($null -eq $build) { Stop-WithMessage "Paper $paperVersion build $paperBuild のSTABLE配布を確認できません。不明なversionでは起動しません。" }
    $download = $build.downloads.'server:default'
    if ($null -eq $download -or [string]::IsNullOrWhiteSpace($download.url) -or [string]::IsNullOrWhiteSpace($download.checksums.sha256)) { Stop-WithMessage 'Paper download情報またはSHA-256を解決できません。' }
    $temporary = "$paperJar.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    try { Invoke-WebRequest -Headers @{'User-Agent' = $userAgent} -Uri $download.url -OutFile $temporary -TimeoutSec 120 }
    catch { Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue; Stop-WithMessage 'Paper JARの取得に失敗しました。不完全なJARでは起動しません。' }
    $actualHash = Get-Sha256 $temporary
    if ($actualHash -ne $download.checksums.sha256) { Remove-Item -LiteralPath $temporary -Force; Stop-WithMessage 'Paper JARのSHA-256検証に失敗しました。' }
    Move-Item -LiteralPath $temporary -Destination $paperJar -Force
    @("version=$paperVersion", "build=$paperBuild", "sha256=$actualHash") | Set-Content -LiteralPath $paperMetadata -Encoding ascii
}
function Confirm-Eula {
    $eula = Join-Path $runtime 'eula.txt'
    if ((Test-Path -LiteralPath $eula) -and (Get-Content -LiteralPath $eula | Where-Object { $_ -match '^\s*eula\s*=\s*true\s*$' })) { return }
    Show-Step 'Minecraft EULAへの明示同意が必要です。'
    Write-Host '内容を確認してください: https://aka.ms/MinecraftEULA'
    do { $choice = (Read-Host '同意する場合は Y、中止する場合は N').Trim().ToUpperInvariant() } while ($choice -notin @('Y', 'N'))
    if ($choice -ne 'Y') { Stop-WithMessage 'EULAへ同意していないためPaperは起動していません。' }
    Set-Content -LiteralPath $eula -Value 'eula=true' -Encoding ascii
}
function Install-RuntimePlugin([hashtable]$definition) {
    $target = Join-Path $plugins $definition.FileName
    Show-Step "$($definition.DisplayName) $($definition.Version) を確認しています。"
    if ((Test-Path -LiteralPath $target) -and (Get-Sha256 $target) -eq $definition.Sha256) {
        Write-Host "$($definition.DisplayName): 固定版 / SHA-256検証済み"
        return
    }
    $temporary = "$target.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    try { Invoke-WebRequest -Headers @{'User-Agent' = $userAgent} -Uri $definition.Url -OutFile $temporary -TimeoutSec 120 }
    catch {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        Stop-WithMessage "$($definition.DisplayName)の公式配布JAR取得に失敗しました。不完全なJARでは起動しません。"
    }
    if ((Get-Sha256 $temporary) -ne $definition.Sha256) {
        Remove-Item -LiteralPath $temporary -Force
        Stop-WithMessage "$($definition.DisplayName)のSHA-256検証に失敗しました。既存JARは変更していません。"
    }
    Move-Item -LiteralPath $temporary -Destination $target -Force
    Write-Host "$($definition.DisplayName): 配置完了 / SHA-256検証済み"
}
function Set-YamlSectionValue([Collections.Generic.List[string]]$lines, [string]$section, [string]$key, [string]$value) {
    $sectionIndex = -1
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match ('^' + [Regex]::Escape($section) + ':\s*$')) { $sectionIndex = $index; break }
    }
    if ($sectionIndex -lt 0) { Stop-WithMessage "Geyser configに現行の$section sectionを確認できません。設定を推測で変更しません。" }
    $sectionEnd = $lines.Count
    for ($index = $sectionIndex + 1; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^[^\s#][^:]*:\s*') { $sectionEnd = $index; break }
    }
    for ($index = $sectionIndex + 1; $index -lt $sectionEnd; $index++) {
        if ($lines[$index] -match ('^\s{2}' + [Regex]::Escape($key) + ':\s*')) {
            $lines[$index] = "  ${key}: $value"
            return
        }
    }
    $lines.Insert($sectionIndex + 1, "  ${key}: $value")
}
function Initialize-GeyserConfig([bool]$wasAbsentBeforeStart) {
    if (-not $wasAbsentBeforeStart) { return }
    $config = Join-Path $plugins 'Geyser-Spigot\config.yml'
    if (-not (Test-Path -LiteralPath $config)) {
        Write-Host '[ASBP] Geyser初期configが生成されなかったため変更していません。Paper logを確認してください。' -ForegroundColor Yellow
        return
    }
    Show-Step '初回生成されたGeyser configへFloodgate接続設定を適用しています。'
    $lines = [Collections.Generic.List[string]]::new()
    Get-Content -LiteralPath $config | ForEach-Object { $lines.Add($_) }
    Set-YamlSectionValue $lines 'bedrock' 'address' '0.0.0.0'
    Set-YamlSectionValue $lines 'bedrock' 'port' '19132'
    Set-YamlSectionValue $lines 'java' 'auth-type' 'floodgate'
    $temporary = "$config.asbp-new"
    $lines | Set-Content -LiteralPath $temporary -Encoding utf8
    Move-Item -LiteralPath $temporary -Destination $config -Force
    Write-Host '[ASBP] Geyser初期設定完了: Bedrock UDP 19132 / Floodgate認証。次回起動以降はユーザー設定を上書きしません。' -ForegroundColor Green
}
function Request-SafeShutdown {
    if (-not (Test-Path -LiteralPath $shutdownHandoff)) { return $false }
    $token = (Get-Content -LiteralPath $shutdownHandoff -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($token)) { return $false }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri 'http://127.0.0.1:8766/launcher/shutdown' -Headers @{'X-ASBP-Shutdown-Token' = $token} -TimeoutSec 10
        return $response.StatusCode -eq 202
    } catch { return $false }
}

try {
    New-Item -ItemType Directory -Path $runtimeLogs, $runtime, $plugins -Force | Out-Null
    Start-Transcript -LiteralPath $launcherLog -Force | Out-Null
    $transcriptStarted = $true
    Write-Host '=== Amaro Survival Bougai Plugin ローカル実機サーバー ===' -ForegroundColor Cyan
    Write-Host "起動日時: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

    Show-Step 'Java 25を確認しています。'
    $javaExe = Find-Java25
    if (-not $javaExe) { Stop-WithMessage 'Java 25を確認できません。Java 25をインストールし、Windowsを再起動してから再実行してください。' }
    Write-Host "Java: $javaExe (major=$(Get-JavaMajor $javaExe))"
    $javaHome = Get-JavaHome $javaExe
    if (-not $javaHome) { Stop-WithMessage 'Java 25の実体Directoryを確認できません。JAVA_HOMEをJava 25へ設定してください。' }
    $env:JAVA_HOME = $javaHome
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH

    Show-Step '現在checkout中のsourceをclean buildしています。'
    & (Join-Path $repository 'gradlew.bat') clean build --no-daemon
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage 'Buildに失敗しました。Paperは起動しておらず、古いPlugin JARも使用しません。' }
    $builtJar = Join-Path $repository 'build\libs\amaro-survival-bougai-plugin-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $builtJar)) { Stop-WithMessage '今回buildしたASBP JARを確認できません。Paperは起動しません。' }
    Write-Host "Build: OK / Plugin JAR: $builtJar"
    if ($BuildOnly) { Show-Step 'Java・Build・JAR選択確認が完了しました。Paperは起動していません。'; exit 0 }

    Show-Step 'Paperを確認しています。'
    if (-not (Test-CachedPaper)) { Install-Paper }
    Write-Host "Paper: $paperVersion build $paperBuild / SHA-256検証済み"

    Show-Step '最新ASBP Plugin JARを配置しています。'
    $stagedPlugin = "$deployedPlugin.new"
    Copy-Item -LiteralPath $builtJar -Destination $stagedPlugin -Force
    Get-ChildItem -LiteralPath $plugins -File -Filter 'amaro-survival-bougai-plugin*.jar' | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    Move-Item -LiteralPath $stagedPlugin -Destination $deployedPlugin -Force
    Write-Host "Plugin配置: $deployedPlugin"

    Show-Step 'Runtime Pluginを固定版・SHA-256検証付きで確認しています。'
    foreach ($definition in $runtimePluginDefinitions) { Install-RuntimePlugin $definition }
    $adminFile = Join-Path $plugins 'AmaroSurvivalBougaiPlugin\test-admin.properties'
    if (-not (Test-Path -LiteralPath $adminFile)) {
        Show-Step '実機テスト管理者を設定します。未設定のまま続行する場合は空Enterです。'
        $adminName = (Read-Host 'Minecraft Java Player名').Trim()
        if (-not [string]::IsNullOrWhiteSpace($adminName)) {
            if ($adminName -notmatch '^[A-Za-z0-9_]{3,16}$') { Stop-WithMessage 'Player名は英数字と_の3～16文字で指定してください。' }
            New-Item -ItemType Directory -Path (Split-Path $adminFile -Parent) -Force | Out-Null
            @("player-name=$adminName", 'player-uuid=') | Set-Content -LiteralPath $adminFile -Encoding ascii
        }
    }
    $geyserConfigWasAbsent = -not (Test-Path -LiteralPath (Join-Path $plugins 'Geyser-Spigot\config.yml'))

    Confirm-Eula

    Show-Step 'Paperを起動しています。'
    Write-Host 'Minecraft接続先: localhost または 127.0.0.1' -ForegroundColor Green
    Write-Host "Paper log: $(Join-Path $runtime 'logs\latest.log')"
    Write-Host '安全に停止するには、このWindowで Y キーを押してください。' -ForegroundColor Yellow
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $javaExe
    $info.WorkingDirectory = $runtime
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.Arguments = '-Xms1G -Xmx2G -Dpaper.disableStartupVersionCheck=true -DgeyserUdpAddress=0.0.0.0 -DgeyserUdpPort=19132 -jar "' + $paperJar + '" --nogui'
    $paperProcess = [Diagnostics.Process]::Start($info)
    if ($null -eq $paperProcess) { Stop-WithMessage 'Paper processを開始できませんでした。' }
    $paperProcess.StandardInput.Close()
    $originalControlC = [Console]::TreatControlCAsInput
    [Console]::TreatControlCAsInput = $true
    try {
        while (-not $paperProcess.HasExited) {
            if ([Console]::KeyAvailable) {
                $key = [Console]::ReadKey($true)
                $stopRequested = $key.KeyChar.ToString().ToUpperInvariant() -eq 'Y' -or (($key.Modifiers -band [ConsoleModifiers]::Control) -and $key.Key -eq [ConsoleKey]::C)
                if ($stopRequested) {
                    Show-Step 'ASBP Pluginへ安全停止を要求しています。'
                    if (Request-SafeShutdown) { Write-Host '[ASBP] 安全停止要求を受理しました。World保存と終了を待っています。' -ForegroundColor Yellow; break }
                    Write-Host '[ASBP] 停止要求に失敗しました。Paper Serverはまだ稼働しています。強制終了しません。' -ForegroundColor Red
                }
            }
            Start-Sleep -Milliseconds 100
        }
    } finally { [Console]::TreatControlCAsInput = $originalControlC }
    $paperProcess.WaitForExit()
    Initialize-GeyserConfig $geyserConfigWasAbsent
    Write-Host "Paper終了コード: $($paperProcess.ExitCode)"
    if ($paperProcess.ExitCode -ne 0) { Stop-WithMessage "Paperが異常終了しました。終了コード=$($paperProcess.ExitCode)" }
    Show-Step 'Paperを安全に停止しました。'
    exit 0
} catch {
    if ($null -ne $paperProcess -and -not $paperProcess.HasExited) {
        if (Request-SafeShutdown) {
            Write-Host '[ASBP] Launcherエラー後、Pluginへ安全停止を要求しました。終了を待っています。' -ForegroundColor Yellow
            $paperProcess.WaitForExit()
        } else {
            Write-Host '[ASBP] Launcherでエラーが発生しましたがPaper Serverは稼働中です。強制終了せず、Process終了まで監視します。' -ForegroundColor Red
            while (-not $paperProcess.HasExited) { Start-Sleep -Seconds 5; Write-Host '[ASBP] Paper Serverは引き続き稼働中です。' -ForegroundColor Red }
        }
    }
    Write-Host ''
    Write-Host "[ASBP] エラー: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Launcher log: $launcherLog"
    exit 1
} finally {
    if ($transcriptStarted) { try { Stop-Transcript | Out-Null } catch {} }
}
