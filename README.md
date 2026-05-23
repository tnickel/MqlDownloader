# MQL Signal Provider Analyzer

## 📊 Überblick

Ein hochentwickeltes Java-Tool zur automatisierten Analyse und Bewertung von MetaTrader (MQL4/MQL5) Signal Providern. Das System ermöglicht die systematische Bewertung von Trading-Signalen basierend auf fortgeschrittenen Performance-Metriken wie MPDD (Month Profit Divided by Drawdown).

## ✨ Hauptfeatures

### 🔄 Automatisierter Download & Verarbeitung
- **Selenium-basierter Web-Scraper** für automatische Datenextraktion
- **Batch-Verarbeitung** von hunderten Signal Providern
- **Intelligente Fehlerbehandlung** mit automatischer Datei-Bereinigung
- **Progress-Tracking** mit detailliertem Logging

### 📈 Erweiterte Performance-Analyse
- **MPDD-Berechnungen** (3, 6, 9, 12 Monate) für präzise Risiko-Bewertung
- **Automatische Qualitätsfilterung** (Provider mit MPDD < 0.5 werden entfernt)
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

- **Java 11+** - Moderne Java-Features
- **Selenium WebDriver** - Web-Automation
- **JSoup** - HTML-Parsing
- **Log4j2** - Professionelles Logging
- **Swing** - Desktop GUI
- **Maven/Gradle** - Dependency Management

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
Das Projekt befindet sich auf der Festplatte unter: `D:\git\MQL\MqlDownloader` (ehemals `D:\git\MqlDownloader`).

### Quick Start
1. Repository klonen oder direkt aus `D:\git\MQL\MqlDownloader` in der IDE öffnen.
2. Dependencies installieren.
3. Konfigurationspfad anpassen (`C:\Forex\MqlAnalyzer`).
4. Anwendung starten.

## 📈 Output-Formate

### Strukturierte Textdateien
```
Balance=15420.50
EquityDrawdown=12.34
3MPDD=0.8567
MonthProfitProz=2024/01=5.2,2024/02=3.1,2024/03=7.8
StabilityValue=85.40
```

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

## 🎯 Roadmap

- [ ] **Cloud-Integration** für skalierbare Verarbeitung
- [ ] **REST API** für externe Integration
- [ ] **Machine Learning** für Provider-Ranking
- [ ] **Real-time Monitoring** für Live-Signale
- [ ] **Docker-Support** für einfache Deployment

---

> **Hinweis**: Dieses Tool ist für Bildungs- und Analysezwecke konzipiert. Trading birgt finanzielle Risiken.
