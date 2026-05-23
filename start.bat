@echo off
chcp 65001 > nul
title MqlDownloader Launcher

echo =======================================================================
echo   MqlDownloader - Start-Skript
echo =======================================================================
echo.
echo Dieses Skript kompiliert das Java-Projekt und startet die GUI-Anwendung.
echo.

rem ??berpr??fe, ob Maven (mvn) im System-Pfad vorhanden ist
where mvn >nul 2>nul
if %errorlevel% equ 0 goto :maven_ok

echo [FEHLER] Apache Maven (mvn) wurde nicht im System-Pfad (PATH) gefunden.
echo Bitte stellen Sie sicher, dass Java und Maven installiert und konfiguriert sind.
echo.
pause
exit /b 1

:maven_ok
echo [INFO] Kompiliere und starte MqlDownloaderApp...
echo.
call mvn compile exec:java "-Dexec.mainClass=main.MqlDownloaderApp"

if %errorlevel% neq 0 goto :run_error
echo.
echo [INFO] Anwendung erfolgreich beendet.
goto :end

:run_error
echo.
echo [FEHLER] Die Anwendung wurde mit Fehlern beendet oder konnte nicht gestartet werden.

:end
echo.
pause
