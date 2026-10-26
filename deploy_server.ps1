# deploy_server.ps1 - Deploy des MqlDownloader-JARs auf den Tradingserver (DESKTOP-NS1MQSV)
#
# Zugang steht in config/deploy.local.json (gitignored, siehe config/deploy.local.example.json):
#   host, user, ssh_key_pfad, ziel (Remote-JAR-Pfad), hostname_erwartet (Falsch-Rechner-Schutz)
#
# Aufruf:
#   powershell -NoProfile -ExecutionPolicy Bypass -File deploy_server.ps1 [-Jar <pfad>] [-Force]
#     -Jar    fertiges Fat-JAR; Default: neuestes target/*-jar-with-dependencies.jar,
#             sonst tmp/MqlDownloaderApp.jar
#     -Force  eine laufende MqlDownloaderApp auf dem Ziel wird vorher beendet
#             (ohne -Force bricht der Deploy ab, solange die App das JAR sperrt)
param(
    [string]$Jar = "",
    [switch]$Force
)
$ErrorActionPreference = "Stop"

$cfgPath = Join-Path $PSScriptRoot "config\deploy.local.json"
if (-not (Test-Path $cfgPath)) {
    Write-Error "Fehlt: $cfgPath  (Vorlage: config/deploy.local.example.json)"
    exit 1
}
$cfg = Get-Content $cfgPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Remote([string]$Command) {
    $out = & ssh -o BatchMode=yes -o ConnectTimeout=10 -i $cfg.ssh_key_pfad "$($cfg.user)@$($cfg.host)" $Command 2>$null
    if ($LASTEXITCODE -ne 0) { throw "SSH fehlgeschlagen: $Command" }
    return $out
}

# 1) Zu deployendes JAR bestimmen
if (-not $Jar) {
    $built = Get-ChildItem -Path (Join-Path $PSScriptRoot "target") -Filter "*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($built) { $Jar = $built.FullName } else { $Jar = Join-Path $PSScriptRoot "tmp\MqlDownloaderApp.jar" }
}
if (-not (Test-Path $Jar)) { Write-Error "JAR nicht gefunden: $Jar"; exit 1 }
$localSize = (Get-Item $Jar).Length
Write-Host "==> Deploye $(Split-Path -Leaf $Jar) ($([math]::Round($localSize / 1MB, 1)) MB) ..."

# 2) Falsch-Rechner-Schutz
$remoteHost = (Remote "hostname")
if ($remoteHost -notlike "*$($cfg.hostname_erwartet)*") {
    Write-Error "Ziel-Rechner ist '$remoteHost', erwartet '$($cfg.hostname_erwartet)' - abgebrochen"
    exit 1
}
Write-Host "==> Ziel: $remoteHost ($($cfg.host))"

# 3) Laufende App beenden (ein offenes JAR blockiert den Upload)
# Hinweis: PowerShell 5.1 stript doppelte Anfuehrungszeichen in Argumenten an
# ssh.exe - Remote-Befehle daher ohne Quotes formulieren (Pfade ohne Leerzeichen).
$javaLines = @()
$javaLines += Remote "wmic process where name='java.exe' get processid,commandline"
$javaLines += Remote "wmic process where name='javaw.exe' get processid,commandline"
$procLines = @($javaLines | Where-Object { $_ -match "MqlDownloaderApp" })
if ($procLines.Count -gt 0 -and -not $Force) {
    Write-Error "MqlDownloaderApp laeuft auf dem Ziel - mit -Force wird sie vorher beendet."
    exit 1
}
foreach ($line in $procLines) {
    if ($line -match '(\d+)\s*$') {
        $procId = $Matches[1]
        Write-Host "==> Beende laufende MqlDownloaderApp (PID $procId)"
        Remote "taskkill /PID $procId /F" | Out-Null
    }
}

# 4) Backup des alten JARs, dann atomarer Upload (erst .upload_tmp, dann move)
$remoteScp = $cfg.ziel                                            # z. B. C:/Forex/MqlAnalyzer/bin/MqlDownloaderApp.jar
$remoteWin = $remoteScp -replace "/", "\"                         # cmd auf dem Ziel will Backslashes
$remoteWinTmp = $remoteWin + ".upload_tmp"
Remote "if exist $remoteWin copy /y $remoteWin $remoteWin.bak" | Out-Null

Write-Host "==> Upload nach $remoteScp (atomar) ..."
& scp -o BatchMode=yes -o ConnectTimeout=10 -i $cfg.ssh_key_pfad $Jar "$($cfg.user)@$($cfg.host):$remoteScp.upload_tmp"
if ($LASTEXITCODE -ne 0) { Write-Error "SCP-Upload fehlgeschlagen"; exit 1 }
Remote "move /y $remoteWinTmp $remoteWin" | Out-Null

# 5) Groesse gegenprüfen
$remoteSizeLine = Remote "for %A in ($remoteWin) do @echo %~zA"
if ($remoteSizeLine -match '(\d+)') {
    if ([long]$Matches[1] -ne $localSize) {
        Write-Warning "Groesse weicht ab: lokal $localSize, Ziel $($Matches[1]) Bytes"
    } else {
        Write-Host "==> Groesse stimmt: $localSize Bytes"
    }
}

Write-Host "==> Deploy auf $remoteHost fertig."
if ($procLines.Count -gt 0) {
    Write-Host "Hinweis: MqlDownloaderApp wurde beendet - auf dem Ziel per Doppelklick"
    Write-Host "         (StartMqlDownloaderApp.bat in C:\Forex\MqlAnalyzer\bin) neu starten."
}
