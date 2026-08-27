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
$paperVersion = '26.2'
$paperBuild = '112'
$paperApi = "https://fill.papermc.io/v3/projects/paper/versions/$paperVersion/builds"
$userAgent = 'amaro-survival-bougai-launcher/0.1 (https://github.com/ryanjei/amaro-survival-bougai-plugin)'
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
function Stop-PaperSafely {
    if ($null -eq $paperProcess -or $paperProcess.HasExited) { return }
    Show-Step 'Paperへstopを送信し、World保存と正常終了を待っています。'
    try { $paperProcess.StandardInput.WriteLine('stop'); $paperProcess.StandardInput.Flush(); $paperProcess.StandardInput.Close() }
    catch { Write-Host '[ASBP] stop送信に失敗しました。データ保護のため強制終了は行いません。' -ForegroundColor Red; return }
    if (-not $paperProcess.WaitForExit(120000)) {
        Write-Host '[ASBP] 保存に時間がかかっています。強制終了せず、そのまま待機します。' -ForegroundColor Yellow
        $paperProcess.WaitForExit()
    }
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
    $info.Arguments = '-Xms1G -Xmx2G -Dpaper.disableStartupVersionCheck=true -jar "' + $paperJar + '" --nogui'
    $paperProcess = [Diagnostics.Process]::Start($info)
    if ($null -eq $paperProcess) { Stop-WithMessage 'Paper processを開始できませんでした。' }
    $originalControlC = [Console]::TreatControlCAsInput
    [Console]::TreatControlCAsInput = $true
    try {
        while (-not $paperProcess.HasExited) {
            if ([Console]::KeyAvailable) {
                $key = [Console]::ReadKey($true)
                $stopRequested = $key.KeyChar.ToString().ToUpperInvariant() -eq 'Y' -or (($key.Modifiers -band [ConsoleModifiers]::Control) -and $key.Key -eq [ConsoleKey]::C)
                if ($stopRequested) { Stop-PaperSafely; break }
            }
            Start-Sleep -Milliseconds 100
        }
    } finally { [Console]::TreatControlCAsInput = $originalControlC }
    if (-not $paperProcess.HasExited) { Stop-PaperSafely }
    $paperProcess.WaitForExit()
    Write-Host "Paper終了コード: $($paperProcess.ExitCode)"
    if ($paperProcess.ExitCode -ne 0) { Stop-WithMessage "Paperが異常終了しました。終了コード=$($paperProcess.ExitCode)" }
    Show-Step 'Paperを安全に停止しました。'
    exit 0
} catch {
    if ($null -ne $paperProcess -and -not $paperProcess.HasExited) { Stop-PaperSafely }
    Write-Host ''
    Write-Host "[ASBP] エラー: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Launcher log: $launcherLog"
    exit 1
} finally {
    if ($transcriptStarted) { try { Stop-Transcript | Out-Null } catch {} }
}
