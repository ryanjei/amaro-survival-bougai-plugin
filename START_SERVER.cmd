@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul

cd /d "%~dp0"

set "SERVER_DIR=%~dp0.runtime\paper"
set "PAPER_JAR=%SERVER_DIR%\paper.jar"
set "JAVA_EXE=java.exe"

echo [ASBP] ローカルPaperサーバーを起動します。
echo [ASBP] Server: "%SERVER_DIR%"

if not exist "%SERVER_DIR%\" (
    echo [ASBP] エラー: Paperサーバーディレクトリがありません。
    echo [ASBP] READMEの「ローカル実機確認」に従って .runtime\paper を準備してください。
    goto :failure
)

if not exist "%PAPER_JAR%" (
    echo [ASBP] エラー: Paper Jarがありません: "%PAPER_JAR%"
    echo [ASBP] Paper 26.2 build 112を paper.jar という名前で配置してください。
    goto :failure
)

if not exist "%SERVER_DIR%\eula.txt" (
    echo [ASBP] エラー: eula.txtがありません。
    echo [ASBP] Minecraft EULAを確認し、同意する場合だけeula.txtを準備してください。
    goto :failure
)

findstr /r /i /c:"^[ ]*eula[ ]*=[ ]*true[ ]*$" "%SERVER_DIR%\eula.txt" >nul
if errorlevel 1 (
    echo [ASBP] エラー: Minecraft EULAへの同意を確認できません。
    echo [ASBP] EULAを確認し、同意する場合だけeula.txtへ eula=true を設定してください。
    goto :failure
)

where "%JAVA_EXE%" >nul 2>&1
if errorlevel 1 (
    echo [ASBP] エラー: PATHからJavaを見つけられませんでした。
    echo [ASBP] Java 25をインストールし、PATHを設定してください。
    goto :failure
)

set "JAVA_VERSION="
set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('%JAVA_EXE% -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"

if not defined JAVA_MAJOR (
    echo [ASBP] エラー: Java Versionを確認できませんでした。
    goto :failure
)

if not "%JAVA_MAJOR%"=="25" (
    echo [ASBP] エラー: Java 25が必要です。検出Version: %JAVA_VERSION%
    echo [ASBP] PATHでJava 25が先に選択されるよう設定してください。
    goto :failure
)

echo [ASBP] Java %JAVA_VERSION% を使用します。
echo [ASBP] 停止するときは、このConsoleで stop と入力してください。
echo.

pushd "%SERVER_DIR%"
"%JAVA_EXE%" -Xms2G -Xmx4G -jar "%PAPER_JAR%" nogui
set "PAPER_EXIT=%ERRORLEVEL%"
popd

if not "%PAPER_EXIT%"=="0" (
    echo.
    echo [ASBP] Paper Serverがエラー終了しました。Exit Code: %PAPER_EXIT%
    goto :failure
)

echo.
echo [ASBP] Paper Serverは正常に停止しました。
exit /b 0

:failure
echo.
echo [ASBP] 起動または実行に失敗しました。上の内容を確認してください。
pause
exit /b 1
