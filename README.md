# MQL Signal Provider Analyzer

Projektversion: `0.0.35-SNAPSHOT` laut `pom.xml`.

## 📊 Überblick

Ein hochentwickeltes Java-Tool zur automatisierten Analyse und Bewertung von MetaTrader (MQL4/MQL5) Signal Providern. Das System ermöglicht die systematische Bewertung von Trading-Signalen basierend auf fortgeschrittenen Performance-Metriken wie MPDD (Month Profit Divided by Drawdown).

## ✨ Hauptfeatures

### 🔄 Automatisierter Download & Verarbeitung
- **Selenium-basierter Web-Scraper** für automatische Datenextraktion
- **Batch-Verarbeitung** von hunderten Signal Providern
- **Fehlerweitergabe** an den Updatejob; technische Fehler und unbekannte MPDD-Werte erhalten Originaldateien
- **Progress-Tracking** mit detailliertem Logging

### 📈 Erweiterte Performance-Analyse
- **MPDD-Berechnungen** (3, 6, 9, 12 Monate) für präzise Risiko-Bewertung
- **Automatische Qualitätsfilterung** (berechneter, endlicher MPDD < 0.5; im Abonnentenmodus deaktiviert). Fehlende Monats-/DD-Daten sind kein Löschkriterium.
- **Drawdown-Chart-Analyse** mit grafischer Datenextraktion
- **Stabilitätsbewertung** basierend auf historischen Daten
- **Monatliche Profit-Trend-Analyse**

### 🎯 Intelligente Datenkonvertierung
- **HTML zu strukturiertem Text** Konvertierung
- **Chart-Daten-Extraktion** aus embedded SVG-Grafiken
- **Automatische Datennormalisierung** und -bereinigung
- **Umfassende Metadaten-Extraktion**

### 🖥️ Benutzerfreundliche GUI
- **Moderne Swing-Oberfläche** mit Progress-Bars
- **Real-time Logging** mit farbcodiertem Output
- **Konfigurierbare Download-Geschwindigkeit**
- **Setup-Dialog** für Benutzereinstellungen

### 🌐 REST-API für andere Anwendungen
- **Eingebetteter HTTP-Server** (Standard-Port 8089, optional mit API-Token)
- **JSON/CSV-Endpunkte** für Provider-Statistiken, Abonnenten-Historie und -Änderungen
- `/providers` liefert standardmäßig nur Signale mit mindestens einem Abonnenten;
  `minSubscribers=0` öffnet ausdrücklich den vollständigen lokalen Katalog.
  Der Filter gilt vor Pagination für JSON und CSV. Detail-/Archivzugriff bleibt möglich.
- Der aktuelle MQL-Katalog enthält keinen belegten `weeks`-Wert. Die konvertierten
  TXT-Kennzahlen erzeugen derzeit ebenfalls kein Altersfeld. Abonnenten-Snapshots
  und Dateizeitstempel werden nicht als Signalgründung ausgegeben; im Scanner
  bleibt das Alter unbekannt und besteht den regulären Mindestalterfilter nicht.
- **Tradelisten** als JSON oder rohe CSV, **Kennzahlen** und **Testreport-PDFs** abrufbar
- CSV/Kennzahlen werden anhand der abschließenden Signal-ID zugeordnet;
  Zahlen im Signalnamen zählen nicht als ID. Fehlt der reguläre Versionscache,
  werden direkte Archivordner nur bei eindeutigem Dateisatz berücksichtigt:
  gleiche ID und Plattform im selben `data-id`/`data-mt`-HTML-Element,
  widerspruchsfreie gespeicherte Seite und passender MT4-/MT5-CSV-Kopf.
  Mehrere passende Archive bleiben gesperrt. Die Originaldateien werden
  unverändert bereitgestellt; eine Archivmigration findet nicht statt.
- **Dateinummernkorrektur ohne Ueberschreiben:** HTML-Dateinamen muessen
  Providername und Signal-ID eindeutig in beide Richtungen zuordnen.
  Gleichnamige Provider mit verschiedenen IDs, mehrere CSV-/TXT-Kandidaten
  oder vorhandene Zieldateien bleiben unveraendert. Der abschliessende Move
  ersetzt auch bei gleichzeitig angelegtem Ziel keine vorhandene Datei.
- **OpenAPI-Beschreibung** unter `/api/v1/openapi.json`
- **Stufe-0-Aktualisierung:** `POST /api/v1/update` startet einen Hintergrundjob;
  `GET /api/v1/update/status` liefert dessen Status. Die übrigen Datenendpunkte
  bleiben lesend. Bekannte Bodyfelder werden typstreng validiert (400 bei ungültigem JSON/Typschema); Jobresultate bleiben verschachtelte JSON-Objekte. Workerfehler verhindern einen falschen erfolgreichen Frischevermerk. Die OpenAPI-Beschreibung enthält derzeit nur die Leseendpunkte.
- **Verlustfreie JSON-Trades:** Wiederholte MQL-Spalten erhalten zusätzlich `Time_1`/`Time_2`, `Price_1`/`Price_2` und bei MT5 `Volume_1`/`Volume_2`. Die bisherigen einfachen Schlüssel behalten aus Kompatibilitätsgründen den letzten Wert; CSV bleibt unverändert.
- **Cacheprüfung:** Download-Skip nur mit exakt passender Signal-ID, MQL-Version/CSV-Kopf und konfiguriertem Dateialter.
- Details für Connectoren: [doc/REST_API_Dokumentation.md](doc/REST_API_Dokumentation.md)

## 🏗️ Architektur

### Paket-Struktur

```
📁 src/
├── 📦 main/                    # Hauptanwendung
│   └── MqlDownloaderApp.java   # Einstiegspunkt
├── 📦 gui/                     # Benutzeroberfläche
│   ├── MqlDownloaderGui.java   # Hauptfenster
│   ├── LogHandler.java         # Logging-System
│   └── SetupDialog.java        # Konfiguration
├── 📦 downloader/              # Download-Engine
│   └── SignalDownloader.java   # Core Download-Logik
├── 📦 converter/               # Datenkonvertierung
│   └── HtmlConverter.java      # HTML->TXT Transformation
├── 📦 calculators/             # Performance-Berechnungen
│   └── MPDDCalculator.java     # MPDD-Algorithmus
├── 📦 utils/                   # Hilfsbibliotheken
│   ├── HtmlParser.java         # HTML-Parsing
│   ├── DataExtractor.java      # Datenextraktion
│   └── FileUtils.java          # Dateiverwaltung
├── 📦 browser/                 # Web-Automation
│   └── WebDriverManager.java   # Selenium-Verwaltung
└── 📦 config/                  # Konfiguration
    └── ConfigurationManager.java
```

## 🚀 Technologie-Stack

- **Java 11+** - Laufzeitvoraussetzung; das Maven-Compilerziel `source`/`target`
  ist noch `1.8` und belegt keine Java-8-Lauffähigkeit der Abhängigkeiten.
- **Selenium WebDriver** - Web-Automation
- **JSoup** - HTML-Parsing
- **Log4j2** - Professionelles Logging
- **Swing** - Desktop GUI
- **Maven** - Dependency Management (`pom.xml`)

## 📊 MPDD-Algorithmus (Kernfeature)

Das Herzstück der Anwendung ist der proprietäre MPDD-Algorithmus:

```java
MPDD = Durchschnittlicher Monatlicher Profit / Equity Drawdown
```

### MPDD-Vorteile:
- **Risiko-adjustierte Performance-Bewertung**
- **Vergleichbarkeit** verschiedener Trading-Strategien
- **Automatische Qualitätsfilterung** (Threshold: 0.5)
- **Multi-Zeitraum-Analyse** (3, 6, 9, 12 Monate)

## ⚡ Leistungsmerkmale

### Performance-Optimierungen
- **Caching-System** für HTML-Inhalte
- **Batch-Verarbeitung** mit optimierter Speichernutzung
- **Parallele Datenextraktion** wo möglich
- **Intelligente Fehlerwiederherstellung**

### Datenqualität
- **Automatische Datenvalidierung**
- **Duplikat-Erkennung und -Bereinigung**
- **Konsistenz-Checks** für alle Metriken
- **Detaillierte Fehlerberichterstattung**

## 🎯 Anwendungsfälle

### Für Trading-Profis
- **Portfolio-Management** - Systematische Provider-Auswahl
- **Risk-Management** - MPDD-basierte Risikobewertung
- **Performance-Tracking** - Historische Trend-Analyse

### Für Algorithmic Trader
- **Signal-Validation** - Automatisierte Qualitätsprüfung
- **Backtesting-Daten** - Strukturierte historische Daten
- **API-Integration** - Exportierte Daten für weitere Analyse

### Für Forschung & Entwicklung
- **Marktforschung** - Großangelegte Provider-Analyse
- **Strategie-Entwicklung** - Datenbasierte Insights
- **Academic Research** - Strukturierte Finanzmarkt-Daten

## 🔧 Installation & Setup

### Voraussetzungen
- Java 11 oder höher
- Chrome Browser (für Selenium)
- 4GB+ RAM empfohlen

### Projektpfad
Das Projekt befindet sich auf der Festplatte unter: `D:\AntiGravitySoftware\GitWorkspace\SIGNALDOWNLOADER\MqlDownloader` (ehemals `D:\git\MQL\MqlDownloader`).

### Quick Start
1. Repository klonen oder direkt aus `D:\AntiGravitySoftware\GitWorkspace\SIGNALDOWNLOADER\MqlDownloader` in der IDE öffnen.
2. Dependencies installieren.
3. Konfigurationspfad anpassen (`C:\Forex\MqlAnalyzer`).
4. Unter Windows `start.bat` ausführen; es kompiliert vor dem Start. Alternativ: `mvn compile exec:java "-Dexec.mainClass=main.MqlDownloaderApp"`.

Der Standard-Datenpfad der Anwendung ist `C:\\Forex\\MqlAnalyzer`; seine Konfiguration liegt außerhalb des Git-Checkouts. Ein Maven-Testlauf verwendet ausschließlich temporäre Testverzeichnisse.

## 📈 Output-Formate

### Strukturierte Textdateien
```
Balance=15420.50
Currency=USD
Average3MonthProfitMonthsIncluded=3
EquityDrawdown=12.34
3MPDD=0.8567
MonthProfitProz=2024/01=5.2,2024/02=3.1,2024/03=7.8
StabilityValue=85.40
```

`Balance` und die exportierten Trade-CSV-Betraege behalten die native
Kontowaehrung. `Currency` stammt ausschliesslich aus der Kontostand-Zeile
der HTML-Seite; fehlt eine eindeutige Einheit, liefert die REST-API
`Currency: "unknown"` (auch bei alten TXT-Caches ohne dieses Feld).
Es gibt keine pauschale USD-Annahme und keine generelle USC-Umrechnung.
`Average3MonthProfitMonthsIncluded` nennt die tatsaechlich gemittelten
Monate (maximal drei, ohne den neuesten exportierten Monat); bei duennen
Historien sind weniger als drei Monate moeglich. Wins/Losses/TradesTotal
werden aus dieser HTML-Kennzahlenkonvertierung nicht behauptet.

### Detaillierte Logs
- **Conversion-Logs** mit Provider-Statistiken
- **Performance-Metriken** pro Download-Session
- **Fehler-Reports** mit Kontextinformationen

## 🤝 Beitragen

Wir begrüßen Beiträge! Besonders interessant sind:
- **Neue Performance-Metriken**
- **GUI-Verbesserungen**
- **Performance-Optimierungen**
- **Additional Data Sources**

## 📄 Lizenz

[Hier deine gewünschte Lizenz einfügen]

## 🧪 Tests

```bash
mvn test    # REST-API, Parser, MPDD-Berechnung, Setup, DB
```

## 🎯 Status & Roadmap

- [x] **REST API (:8089)** für Integration mit SignalKiScanner (Protokoll mql5-downloader-v1, OpenAPI unter `/api/v1/openapi.json`)
- [x] **Abonnenten-Historie & Metriken** über REST
- [x] **MPDD-Qualitätsfilterung** und automatische Bereinigung
- [ ] **Cloud-Integration** für skalierbare Verarbeitung
- [ ] **Docker-Support** für einfaches Deployment

---

> **Hinweis**: Dieses Tool ist für Bildungs- und Analysezwecke konzipiert. Trading birgt finanzielle Risiken.
