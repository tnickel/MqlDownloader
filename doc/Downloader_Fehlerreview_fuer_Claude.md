# Downloader-Fehlerreview fuer Claude

Stand: 2026-06-21

Dieses Dokument fasst die im aktuellen Downloader-Code gefundenen Fehler und Risiken zusammen. Der Build laeuft mit `mvn clean test` erfolgreich durch, die folgenden Punkte sind daher vor allem Runtime-, Logik- und Robustheitsprobleme.

## 1. Stop-Signal erreicht den aktiven Downloader nicht

**Schwere:** P1

**Betroffene Stellen:**

- `src/gui/DownloadManager.java`, Methode `startDownload`
- `src/gui/DownloadManager.java`, Methode `stopDownload`
- `src/downloader/SignalDownloader.java`, Methode `setStopFlag`

**Problem:**

Beim Start wird `downloader.setStopFlag(stopRequested)` nur einmal mit dem aktuellen Wert `false` aufgerufen. Wenn der Benutzer danach auf Stop klickt, setzt `DownloadManager.stopDownload()` nur `DownloadManager.stopRequested = true`. Die aktive `SignalDownloader`-Instanz erhaelt dieses neue Stop-Signal nicht.

**Auswirkung:**

Der Download-Thread kann weiterarbeiten oder in `sleepWithProgress()` weiter warten, obwohl die GUI schon Cleanup ausloest. Dadurch kann der WebDriver geschlossen werden, waehrend `SignalDownloader` ihn noch nutzt. Moegliche Folgen sind WebDriver-Fehler, inkonsistente Logs, unvollstaendige Downloads oder ein scheinbar gestoppter Download, der intern weiterlaeuft.

**Fix-Idee:**

Die aktive `SignalDownloader`-Instanz als Feld in `DownloadManager` halten und in `stopDownload()` ebenfalls `activeDownloader.setStopFlag(true)` aufrufen. Cleanup sollte erst erfolgen, wenn der Download-Thread wirklich beendet ist oder der Downloader kontrolliert abgebrochen hat.

## 2. WebDriver-Recovery kann neue Chrome-Session leaken

**Schwere:** P1

**Betroffene Stellen:**

- `src/downloader/SignalDownloader.java`, Methode `attemptRecovery`
- `src/gui/DownloadManager.java`, Feld `currentDriver`
- `src/gui/DownloadManager.java`, Methode `cleanupDownload`

**Problem:**

`SignalDownloader` erstellt intern einen eigenen `WebDriverManager`. Bei Recovery wird in `SignalDownloader.attemptRecovery()` ein neuer WebDriver erzeugt und in `this.driver` gespeichert. `DownloadManager.currentDriver` bleibt aber auf der alten Driver-Instanz stehen.

**Auswirkung:**

Beim Cleanup schliesst `DownloadManager.cleanupDownload()` nur den alten Driver. Der neue Recovery-Driver kann offen bleiben. Das fuehrt zu haengenden Chrome-/Chromedriver-Prozessen und kann spaetere Downloads stoeren.

**Fix-Idee:**

Den WebDriver-Lebenszyklus an einer Stelle buendeln. Entweder `SignalDownloader` gibt Driver-Aenderungen per Callback an `DownloadManager` zurueck, oder `DownloadManager` uebergibt denselben `WebDriverManager` und fragt nach Recovery den aktuellen Driver ab. Alternativ sollte `SignalDownloader.cleanup()` immer den aktuell gehaltenen Driver sauber schliessen.

## 3. Cleanup beendet alle Chrome-Prozesse des Systems

**Schwere:** P1/P2

**Betroffene Stelle:**

- `src/browser/WebDriverManager.java`, Methode `cleanupPreviousSession`

**Problem:**

Unter Windows wird `taskkill /F /IM chrome.exe` ausgefuehrt. Das beendet nicht nur die Selenium-Session, sondern alle Chrome-Prozesse des Benutzers.

**Auswirkung:**

Normale Browserfenster des Benutzers werden zwangsweise geschlossen. Das kann Datenverlust in offenen Tabs verursachen und ist fuer ein Desktop-Tool sehr riskant.

**Fix-Idee:**

Kein globales `taskkill chrome.exe` verwenden. Stattdessen nur `driver.quit()` nutzen und temporaere Selenium-Profile ueber `currentUserDataDir` loeschen. Falls Prozess-Cleanup noetig ist, nur bekannte Child-Prozesse der eigenen Chromedriver-Session beenden.

## 4. Limit-UI erlaubt Werte, die ConfigurationManager ablehnt

**Schwere:** P2

**Betroffene Stellen:**

- `src/gui/ButtonPanelManager.java`, Methode `addLimitFieldListener`
- `src/config/ConfigurationManager.java`, Methoden `setMql4Limit` und `setMql5Limit`

**Problem:**

Die GUI akzeptiert Werte von `0` bis `99999` und zeigt eine Fehlermeldung, dass `0` fuer unbegrenzt erlaubt sei. Die Config-Setter erlauben aber nur `1` bis `5000` und werfen bei `0` oder Werten ueber `5000` eine `IllegalArgumentException`.

**Auswirkung:**

Gueltig wirkende GUI-Eingaben koennen beim Fokusverlust eine ungefangene Exception ausloesen. Ausserdem ist "unbegrenzt" im Downloader zwar ueber `limit == 0` vorgesehen, kann aber ueber die aktuelle GUI/Config-Kombination nicht gespeichert werden.

**Fix-Idee:**

Entscheiden, welches Verhalten gewuenscht ist:

- Wenn `0 = unbegrenzt` gelten soll: Config-Setter auf `0..5000` oder `0..99999` anpassen.
- Wenn maximal `5000` gelten soll: GUI-Validierung und Fehlermeldung auf `1..5000` korrigieren.

Die Exception sollte in `ButtonPanelManager` zusaetzlich als `IllegalArgumentException` abgefangen werden.

## 5. Subscriber-Datenbank vermischt MQL4 und MQL5 bei gleicher Signal-ID

**Schwere:** P2

**Betroffene Stelle:**

- `src/database/DatabaseManager.java`

**Problem:**

Die Tabelle `signal_subscribers` verwendet `signal_id` als alleinigen Primary Key. Der Select prueft ebenfalls nur `signal_id`, obwohl `mql_version` gespeichert wird.

**Auswirkung:**

Wenn MQL4 und MQL5 gleiche Signal-IDs haben, ueberschreiben oder vergleichen sie dieselbe Datenbankzeile. Subscriber-Aenderungen koennen dadurch falsch gemeldet werden.

**Fix-Idee:**

Composite Key aus `signal_id` und `mql_version` verwenden. Auch `SELECT`, `UPDATE` und gegebenenfalls Migration bestehender Daten muessen `mql_version` beruecksichtigen.

## 6. CSV-Dateierkennung kann falsche Datei verschieben

**Schwere:** P2

**Betroffene Stellen:**

- `src/downloader/SignalDownloader.java`, Methode `handleDownloadedFile`
- `src/downloader/SignalDownloader.java`, Methode `findDownloadedFile`
- `src/downloader/SignalDownloader.java`, Methode `cleanupDownloadDirectory`

**Problem:**

`findDownloadedFile()` gibt einfach die erste `.csv` im Download-Verzeichnis zurueck. Es gibt keine Sortierung nach Aenderungszeit, keine Pruefung auf `.crdownload`, keine Stabilitaetspruefung der Dateigroesse und keine Zuordnung zur Provider-ID.

**Auswirkung:**

Bei alten, parallelen oder langsam abgeschlossenen Downloads kann die falsche CSV verschoben und falsch benannt werden. Das ist besonders kritisch, weil anschliessend `originalId` aus dem Dateinamen extrahiert und fuer Protokoll/Dateinamen genutzt wird.

**Fix-Idee:**

Vor dem Klick Timestamp merken und nach dem Klick nur Dateien betrachten, die danach entstanden oder geaendert wurden. Warten, bis keine `.crdownload` mehr existiert und die Dateigroesse fuer mehrere Intervalle stabil bleibt. Wenn moeglich, den erwarteten Dateinamen oder die Signal-ID validieren.

## 7. Chrome wird mit deaktiviertem JavaScript gestartet

**Schwere:** P2/P3

**Betroffene Stelle:**

- `src/browser/WebDriverManager.java`, Methode `createRobustChromeOptions`

**Problem:**

Chrome bekommt `--disable-javascript`, waehrend der Downloader Login, Navigation, Tabs und Export-Klicks ueber eine moderne Website automatisiert. Viele MQL5-UI-Elemente koennen JavaScript benoetigen.

**Auswirkung:**

Login, Tab-Wechsel oder History-Export koennen je nach Seitenzustand unzuverlaessig oder gar nicht funktionieren.

**Fix-Idee:**

`--disable-javascript` entfernen. Falls Performance wichtig ist, eher Bilder oder unwichtige Ressourcen blockieren, aber JavaScript fuer die MQL-Seite aktiv lassen.

## 8. Root-Page-URL ignoriert die uebergebene Provider-URL

**Schwere:** P3

**Betroffene Stelle:**

- `src/downloader/SignalDownloader.java`, Methode `downloadProviderRootPage`

**Problem:**

Die Methode bekommt `providerUrl`, baut die Root-URL aber selbst als `https://www.mql5.com/de/signals/{id}?source=...`. Damit wird hart auf Deutsch gewechselt und eventuelle URL-Struktur aus der Liste ignoriert.

**Auswirkung:**

Wenn MQL5 die URL-Struktur, Sprache oder Query-Parameter aendert, kann der Root-Download brechen oder eine andere Ansicht speichern als erwartet.

**Fix-Idee:**

Moeglichst die extrahierte `providerUrl` verwenden und nur gezielt Query-Parameter ergaenzen, wenn sie wirklich noetig sind.

## Verifikation

Ausgefuehrt:

```powershell
mvn clean test
```

Ergebnis: Build erfolgreich.

Wichtig: Es gibt offenbar keine automatisierten Tests fuer die Downloader-Runtime mit Selenium, Stop/Recovery oder Dateidownload. Die gefundenen Punkte sollten deshalb nach dem Fix mit mindestens einem kleinen Integrationstest oder manuellem Testlauf abgesichert werden.
