# MqlDownloader – Benutzerhandbuch & Dokumentation

Dieses Benutzerhandbuch führt Sie in die Bedienung, Konfiguration und Fehlerbehebung der Desktop-Anwendung **MqlDownloader** ein. Das Programm dient dem automatisierten Download und der anschließenden Performancestatistik-Filterung von Handels-Signalen der Plattform MQL5.

---

## 📖 1. Einführung & Verwendungszweck

Der **MqlDownloader** ist ein Desktop-Werkzeug für Händler und Entwickler, die systematisch Signal-Provider der Handelsplattformen MetaTrader 4 (MT4) und MetaTrader 5 (MT5) bewerten möchten. 

Das Programm führt folgende Schritte für Sie aus:
1. **Automatisches Scraping**: Es steuert einen Webbrowser, navigiert durch die MQL5-Signal-Listen und lädt die Informationsseite jedes Signal-Anbieters als HTML herunter.
2. **Daten-Download**: Es lädt den kompletten historischen Handelsverlauf des Anbieters als CSV-Datei herunter (sofern verfügbar).
3. **Daten-Konvertierung**: Es wandelt die HTML-Daten in strukturierte Textdateien um.
4. **Qualitäts-Filterung**: Es wendet die MPDD-Formel (Month Profit Divided by Drawdown) an. Signal-Provider mit unzureichenden Werten (3MPDD < 0.5) werden automatisch gelöscht, um eine hohe Signalqualität zu gewährleisten und Speicherplatz einzusparen.

---

## 💻 2. Systemvoraussetzungen & Vorbereitung

Bevor Sie das Programm starten, stellen Sie sicher, dass Ihr System folgende Anforderungen erfüllt:

- **Java Runtime Environment (JRE) 11** oder neuer.
- **Google Chrome**: Auf Ihrem System muss der reguläre Google Chrome Browser installiert sein. (Der benötigte WebDriver wird vom Programm automatisch im Hintergrund verwaltet).
- **Aktive Internetverbindung**: Erforderlich, da die Daten live von der MQL5-Webseite gescraped werden.
- **MQL5-Benutzerkonto (optional, aber empfohlen)**: Für den Download des Handelsverlaufs (CSV-Dateien) ist es oft notwendig, bei MQL5 eingeloggt zu sein. Die Anmeldedaten können in der Konfigurationsdatei hinterlegt werden.

---

## 🚀 3. Erster Start und Ordnerstruktur

Beim ersten Start der Anwendung über die Hauptklasse `main.MqlDownloaderApp` wird auf Ihrer lokalen Festplatte automatisch das Arbeitsverzeichnis **`C:\Forex\MqlAnalyzer`** angelegt.

Dieses Verzeichnis enthält folgende Struktur:
* **`C:\Forex\MqlAnalyzer\config\`**: Enthält die Einstellungsdatei `MqldownloaderConfig.txt`.
* **`C:\Forex\MqlAnalyzer\logs\`**: Speichert detaillierte System-Fehlerprotokolle für Entwickler.
* **`C:\Forex\MqlAnalyzer\download\`**: Das Hauptverzeichnis für alle heruntergeladenen Daten.
  * `mql4/` – Unterordner für MetaTrader 4 Signale.
  * `mql5/` – Unterordner für MetaTrader 5 Signale.

---

## 🖥️ 4. Die Programmoberfläche (GUI) im Detail

Nach dem Start öffnet sich das Hauptfenster der Anwendung:

![GUI Layout Skizze](https://via.placeholder.com/800x600.png?text=MqlDownloader+GUI+Interface) *(Hinweis: Die Swing-Oberfläche ist funktional und kompakt aufgebaut).*

### 4.1. Die Steuerungselemente und Eingabefelder

#### A. Download-Bereich (Zeile 1)
- **MQL4 Download (Button)**: Startet den Scraping-Prozess für MetaTrader 4 Signale.
- **Limit (MQL4)**: Textfeld zur Eingabe einer Zahl (0 bis 99999). Bestimmt, wie viele Signale aus der MT4-Liste maximal gescannt werden sollen. Ein Wert von `0` bedeutet **unbegrenzt** (lädt alle verfügbaren Signale herunter).
- **Zähler (MQL4, Goldene Box)**: Zeigt in Echtzeit an, wie viele MT4-Signale im aktuellen Durchlauf bereits erfolgreich verarbeitet wurden.
- **MQL5 Download (Button)**: Startet den Scraping-Prozess für MetaTrader 5 Signale.
- **Limit (MQL5)**: Textfeld zur Eingabe des Limits für MT5-Signale (0 bis 99999). Ein Wert von `0` bedeutet **unbegrenzt** (lädt alle verfügbaren Signale herunter).
- **Zähler (MQL5, Goldene Box)**: Zeigt die Anzahl verarbeiteter MT5-Signale an.

#### B. Optimierungs- und Filter-Bereich (Zeile 2)
- **Download only if older than (days)** (Download-Tage):
  Hier können Sie eine Zahl von **0 bis 20** eingeben.
  * **Wichtiges Feature**: Dies verhindert das erneute Herunterladen von Daten, die Sie vor Kurzem erst geladen haben. Bei einem Wert von `5` werden Signale, deren lokale HTML-Datei jünger als 5 Tage ist, übersprungen. Dies spart enorm viel Zeit und Bandbreite.
  * Geben Sie **`0`** ein, um das Überspringen zu deaktivieren und alle Daten unabhängig vom Alter neu herunterzuladen.
- **Convert (Button)**:
  Startet die Verarbeitung der heruntergeladenen HTML-Dateien zu strukturierten Textdateien (`_root.txt`).
  * > [!WARNING]
    > **Automatisches Löschen:** Der Konvertierungsprozess berechnet automatisch den 3-Monats-MPDD-Wert für jedes Signal. Liegt dieser **unter 0.5** oder ist die Datei aufgrund fehlerhafter Downloads **unvollständig bzw. korrupt**, werden die CSV-Datei, die HTML-Datei und die Textdatei des entsprechenden Signal-Providers **unwiderruflich von der Festplatte gelöscht**.

#### C. System-Aktionen (Zeile 3)
- **Stop Download (Button)**:
  Bricht den aktuellen Download- oder Konvertierungsprozess sofort ab. Der Browser wird im Hintergrund ordnungsgemäß geschlossen.
- **Do all at Once (Button)**:
  Der komfortable Komplett-Workflow. Führt nacheinander folgende Aktionen aus:
  1. Download der MT4-Signale bis zum eingestellten Limit.
  2. Download der MT5-Signale bis zum eingestellten Limit.
  3. Konvertierung aller Dateien inklusive automatischer MPDD-Filterung.

#### D. Statistik und Feedback (Zentrum & Fußbereich)
- **Show Download Statistics (Gelber Button)**:
  Öffnet ein separates Fenster, das Ihnen eine Übersicht darüber gibt, wie viele HTML-Dateien in Ihrem Downloadordner wie alt sind (z. B. 0 Tage alt, 1 Tag alt, ..., älter als 20 Tage). Dies hilft bei der Entscheidung, ob ein neuer Download notwendig ist.
- **Zentraler Log-Bereich (Schwarz)**:
  Hier sehen Sie live alle Aktionen, Erfolge und Fehler, farblich hervorgehoben (z. B. rote Fehlermeldungen, grüne Erfolgsmeldungen).
- **Fortschrittsbalken & Statusanzeige**:
  * **Wartezeit-Visualisierung**: Zeigt während der Wartezeiten (`sleepWithProgress`) den genauen Countdown-Fortschritt der aktuellen Verzögerung (z. B. `Warte 5.2s...`) direkt im Fortschrittsbalken an.
  * **Statusanzeige**: Informiert fortlaufend über den aktuellen Zustand des Downloads (z. B. welcher Provider gerade geladen/gespeichert/übersprungen wird).

---

## ⚙️ 5. Erweiterte Einstellungen (Setup-Dialog)

Über das obere Menü **`File -> Setup`** erreichen Sie das Einstellungsfenster:

### Download-Geschwindigkeit (Download Speed Configuration)
Da MQL5 IP-Sperren verhängt, wenn zu schnell hintereinander Seiten aufgerufen werden, müssen Sie hier künstliche Wartezeiten definieren:
- **Min Wait (seconds)**: Mindestwartezeit in Sekunden zwischen zwei Seitenaufrufen (mindestens 4 Sekunden).
- **Max Wait (seconds)**: Maximale Wartezeit in Sekunden (bis zu 120 Sekunden).

Das System wählt bei jedem Seitenwechsel eine zufällige Sekundenzahl zwischen diesen beiden Werten aus. Dies imitiert menschliches Verhalten und schützt Ihre IP-Adresse vor temporären Sperren.

---

## 📂 6. Die Ergebnisdaten verstehen

Nach dem Download und der Konvertierung finden Sie im Verzeichnis `C:\Forex\MqlAnalyzer\download\mql4\` (bzw. `mql5/`) drei Dateitypen pro Signal-Provider, benannt nach dem Schema `[ProviderName]_[SignalID]`:

1. **`..._root.html`**: Die originale, vom Browser gespeicherte Webseite des Signals.
2. **`.csv`**: Der komplette Trade-Verlauf des Anbieters. Diese Datei kann in Backtesting-Software importiert werden.
3. **`..._root.txt`**: Die bereinigten Kennzahlen im einfachen Textformat (Key-Value):
   - `Balance`: Das aktuelle Kontoguthaben des Anbieters.
   - `MaxDDGraphic`: Der maximale Drawdown, ermittelt aus der interaktiven SVG-Grafik des Anbieters.
   - `EquityDrawdown`: Der im Text ausgewiesene Drawdown-Wert.
   - `Average3MonthProfit`: Der durchschnittliche monatliche Gewinn der letzten 3 Monate (laufender Monat ausgeschlossen).
   - `StabilityValue`: Stabilitätswert der Performance zwischen 1.0 (sehr schwankend/unzuverlässig) und 100.0 (sehr stetige Gewinne).
   - `MonthProfitProz`: Komma-separierte Liste der monatlichen Renditen (z. B. `2026/04=5.2,2026/03=3.1`).
   - `DrawdownPoints`: Die Koordinaten-Punkte des Drawdown-Charts für Visualisierungen.

---

## 📜 7. Protokolldateien prüfen

Zur Kontrolle der automatischen Abläufe schreibt das Programm Protokolle direkt in den Hauptordner `C:\Forex\MqlAnalyzer\download\`:
- **`mql4download.txt` / `mql5download.txt`**: 
  Hier wird genau protokolliert, wann welcher Provider gestartet wurde, ob er erfolgreich heruntergeladen wurde, ob er übersprungen wurde (weil er zu neu war) oder ob ein Fehler auftrat.
- **`conversionLog.txt`**:
  Dokumentiert den Konvertierungsprozess. Hier sehen Sie genau, welche Provider das Kriterium **3MPDD >= 0.5** erfüllt haben und welche wegen eines zu niedrigen Wertes **gelöscht** wurden.

---

## 🔍 8. Fehlerbehebung (Troubleshooting & FAQ)

### ❓ Der Download startet, aber das Log-Fenster meldet "WebDriver-Initialisierung fehlgeschlagen"
* **Ursache**: Google Chrome ist nicht installiert oder die installierte Chrome-Version blockiert den Treiber.
* **Lösung**: Stellen Sie sicher, dass Google Chrome installiert und auf dem neuesten Stand ist. Starten Sie das Programm neu. Das Tool lädt den passenden Treiber automatisch herunter.

### ❓ Das Programm überspringt alle Provider beim Download
* **Ursache**: Ihre Einstellung bei `Download only if older than (days)` ist zu hoch und Sie haben die Daten vor Kurzem erst geladen.
* **Lösung**: Setzen Sie das Feld `Download only if older than (days)` auf **`0`**, um einen vollständigen Download zu erzwingen.

### ❓ Das Programm lädt HTMLs herunter, aber die CSV-Dateien fehlen
* **Ursache**: Zum Herunterladen der Historie ist bei manchen Signalen ein MQL5-Community-Login erforderlich.
* **Lösung**: Tragen Sie Ihre MQL5-Zugangsdaten in der Konfigurationsdatei `C:\Forex\MqlAnalyzer\config\MqldownloaderConfig.txt` in den Zeilen `username=` und `password=` ein und starten Sie das Programm neu.

### ❓ Mein Internet ist blockiert / Die MQL5-Seite zeigt ein Captcha an
* **Ursache**: Die Anfragen wurden zu schnell gesendet. Die Webseite hat Ihre IP-Adresse temporär für automatisierte Zugriffe gesperrt.
* **Lösung**: 
  1. Erhöhen Sie im Setup-Menü (`File -> Setup`) die Wartezeiten (z. B. Min: 10 Sekunden, Max: 45 Sekunden).
  2. Starten Sie Ihren Internet-Router neu, um eine neue IP-Adresse zu erhalten, oder warten Sie einige Stunden.

### ❓ Warum verschwinden manche Ordner/Dateien nach dem Klick auf "Convert"?
* **Ursache**: Das ist das eingebaute **Qualitäts-Filter-Feature**.
* **Erklärung**: Alle Signale, die im 3-Monats-Durchschnitt ein schlechtes Verhältnis von Gewinn zu Risiko aufweisen (3MPDD < 0.5), werden automatisch gelöscht. Dies ist beabsichtigt, damit Sie nur qualitativ hochwertige Signale auf Ihrem System behalten.
