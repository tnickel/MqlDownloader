@echo off
chcp 65001 > nul
title MqlDownloader Deployment
echo =======================================================================
echo   MqlDownloader - Auto-Deployment & Version Increment
echo =======================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File deploy.ps1

if %errorlevel% neq 0 (
    echo.
    echo [FEHLER] Deployment fehlgeschlagen.
    goto :end
)

echo.
echo [INFO] Deployment erfolgreich beendet.

:end
echo.
pause
