@echo off
rem ============================================================
rem  FightGameReforged launcher (ASCII only on purpose)
rem  Why ASCII only:
rem    cmd.exe parses .bat files using the console's OEM code page
rem    (936/GBK on Chinese Windows). Non-ASCII bytes in this file
rem    would corrupt the parsing of the NEXT line, so this file
rem    keeps every line pure ASCII and switches the console to
rem    UTF-8 at runtime instead. Do not add Chinese comments here;
rem    put Chinese docs in the .md file instead.
rem ============================================================
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JAR=build\libs\FightGameReforged-1.2.1.jar"
if not exist "%JAR%" (
    for %%F in ("build\libs\FightGameReforged-*.jar") do set "JAR=%%~fF"
)

if not exist "%JAR%" (
    echo [ERROR] Cannot find the game jar under build\libs\
    echo         Run this first:  gradle shadowJar
    pause
    exit /b 1
)

echo Starting: %JAR%
echo Console code page set to 65001 (UTF-8), JVM file.encoding=UTF-8.
echo.
java -Dfile.encoding=UTF-8 -jar "%JAR%" %*

endlocal
pause
