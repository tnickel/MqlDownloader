# MqlDownloader REST-API — Technische Referenz (v1)

> **Zielgruppe dieses Dokuments:** Andere Anwendungen und deren Entwickler bzw. KI-Assistenten,
> die aus dem Netzwerk auf die Daten des MqlDownloader zugreifen wollen. Das Dokument ist
> vollständig und maschinenlesbar genug, um daraus Connectoren zu implementieren, ohne den
> Quellcode des Downloaders zu kennen.

Stand: 2026-10-16 (Abgleich mit lokalem Quellcode, kein Laufzeittest) · API-Version: `v1` · Implementierung: `src/rest/` (Java, JDK-HttpServer)

---

## 1. Was ist das?

Der MqlDownloader ist eine Java-Desktop-Anwendung, die auf einem Rechner im LAN läuft und
Signal-Provider-Daten von mql5.com sammelt. Er hält drei Datenarten vor:

| Datenart | Quelle | Beispiel |
|---|---|---|
| **Abonnenten-Statistiken** + Historie | H2-Datenbank `config/subscribers` (Tabellen `signal_subscribers`, `subscriber_history`) | Provider „Gold Spike" hat 8 Abonnenten, +4 in 30 Tagen |
| **Trading-History (Tradelisten)** | CSV-Dateien `<Download-Verzeichnis>\mql4\` bzw. `\mql5\`, benannt `<Name>_<SignalId>.csv` | Einzelne Trades: Zeit, Symbol, Buy/Sell, Volumen |
| **Kennzahlen je Provider** | Konvertierte TXT-Dateien `<Name>_<SignalId>_root.txt` (aus der Provider-Webseite) | Balance, Drawdown, 3MPDD, Monatsprofit |
| **Testreport-PDFs** | PDFs im konfigurierten „Analyse-Verzeichnis", Signal-ID im Dateinamen | Testbericht als PDF-Download |

Die REST-API macht all das über **HTTP + JSON** lesbar. Zusätzlich startet
`POST /api/v1/update` einen Datenaktualisierungsjob (Stufe 0, siehe unten).
Sie läuft **eingebettet im Downloader-Prozess** (kein zweiter Dienst) und ist
standardmäßig **aktiviert**. Die übrigen Datenendpunkte sind lesend.

---

## 2. Verbindungsaufbau („Connector")

Ein Connector benötigt folgende Verbindungsangaben:

1. **Base URL**: `http://<rechnername-oder-ip>:<port>/api/v1` — Standard-Port **8089**
2. **API-Token** (optional; nur nötig, wenn im Downloader einer gesetzt wurde)

Datenabrufe verwenden `GET`; die optionale Aktualisierung verwendet
`POST /api/v1/update` und anschließend `GET /api/v1/update/status`.

Verbindungstest: `GET http://<rechner>:8089/api/v1/health` → HTTP 200 bedeutet „erreichbar".

```json
{
  "status": "ok",
  "service": "MqlDownloader",
  "instance": "DESKTOP-NS1MQSV",
  "apiVersion": "v1",
  "uptimeSeconds": 3600,
  "serverTime": "2026-09-20T12:00:00",
  "providers": 176,
  "tokenRequired": false
}
```

`tokenRequired` sagt, ob ein Token mitgesendet werden muss. `instance` ist die
**Instanz-Kennung** dieses Downloaders (Setup-Dialog → „Instanz-Kennung“,
Standard: Rechnername) — Clients wie der SignalKiScanner unterscheiden damit
mehrere Downloader-Instanzen und warnen, wenn zwei konfigurierte Quellen
versehentlich auf denselben Downloader zeigen.

### Authentifizierung

Nur relevant, wenn `tokenRequired: true`. Der Token wird in **einer** von drei Formen mitgesendet:

```
X-API-Token: <token>                      (bevorzugt)
Authorization: Bearer <token>             (Standard-HTTP)
GET .../api/v1/providers?token=<token>    (Notausgang für Browser/Tools ohne Header)
```

Falscher/fehlender Token → **HTTP 401** mit JSON-Körper `{"error":"..."}`.
Der Vergleich ist konstantzeit (kein Timing-Orakel).

### Konfiguration (wird im Downloader gesetzt, nicht in der Client-App)

Datei `<Wurzelverzeichnis>\config\MqldownloaderConfig.txt` bzw. GUI → Datei → Einstellungen
→ Abschnitt „REST-API":

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `apiEnabled` | `true` | Server beim App-Start starten |
| `apiPort` | `8089` | HTTP-Port (1024–65535) |
| `apiToken` | *(leer)* | Leer = kein Schutz (nur für vertrauenswürdiges LAN gedacht) |

Änderungen im Setup-Dialog wirken sofort (Server wird neu gestartet).

### Allgemeine Konventionen

- **Zeichencodierung**: JSON und erzeugte CSV-Antworten verwenden UTF-8;
  erzeugte CSVs haben ein BOM und `;` als Trennzeichen. Rohe Trade-CSVs
  werden unverändert ausgeliefert; deren BOM und Trennzeichen stammen aus der Datei.
- **Zeitstempel**: doppelt geliefert — `timestamp` als lokale ISO-Zeit (`2026-09-20T11:53:12`) und `timestampUtc` als ISO-Instant (`2026-09-20T09:53:12Z`).
- **Fehler**: JSON `{"error":"<Meldung>"}` mit passendem Code: 400 (schlechter Parameter), 401 (Token), 404 (unbekannter Pfad/Objekt), 405 (nicht unterstützte HTTP-Methode), 409 (Update bei beschäftigter GUI), 500 (intern).
- **Seitengröße**: Default-Limit 1000 Zeilen bei Listen (`providers`, `trades`, `history`), 100 bei `events`; mit `limit`/`offset` steuerbar.
- **CORS**: `Access-Control-Allow-Origin: *` ist gesetzt; `OPTIONS`-Preflight wird beantwortet. Web-Frontends können die API direkt aufrufen.
- **Maschinenlesbare Selbstbeschreibung**: `GET /api/v1/openapi.json` beschreibt
  die Leseendpunkte als OpenAPI 3.0.3. Die Stufe-0-Pfade `/update` und
  `/update/status` fehlen derzeit darin und sind unten separat dokumentiert.

---

## 3. Endpunkt-Referenz

Alle Pfade relativ zur Base `http://<rechner>:<port>/api/v1`.
`{id}` = Signal-ID (numerischer String, z. B. `123`), `{version}` = `mql4`/`mql5` (auch `mt4`/`mt5` akzeptiert).

### 3.1 `GET /health` — Verbindungstest

Keine Parameter. Antwort siehe Abschnitt 2.

### 3.2 `GET /providers` — alle Provider

Abonnenten-Stand je Provider inkl. Änderungen der letzten 7/30 Tage (gleiche Werte wie der Statistik-Dialog der GUI).

JSON-Providerobjekte enthalten außerdem `weeks` (Integer oder `null`) und
`weeksSource` (`mql_profile_cached_weeks_lower_bound` oder `null`). Grundlage ist
der strukturierte Wochenindikator der lokal gespeicherten Profilseite mit
passender Signal-ID und MT4-/MT5-Identität. Der gespeicherte Stand wird nicht um
seitdem verstrichene Wochen erhöht. Fehlende, ungültige oder widersprüchliche
Belege bleiben unbekannt; weder Dateizeit noch Abonnenten-Historie ersetzen sie.
CSV-Statistikexport und TXT-Metrics behalten ihr bestehendes Schema.

| Parameter | Werte | Wirkung |
|---|---|---|
| `version` | `mql4`,`mql5`,`mt4`,`mt5` | nur diese Plattform |
| `name` | Text | Teilstring-Filter auf `signalName` (Groß-/Kleinschreibung egal) |
| `minSubscribers` | Zahl (Default `1`) | nur Provider mit mindestens so vielen Abonnenten; explizit `0` schließt auch Provider ohne Abonnenten ein |
| `sort` | `subscribers` (Default), `name`, `latestChange`, `weekChange`, `monthChange`, `lastUpdated` | Sortierung |
| `order` | `asc`,`desc` (Default `desc`) | Richtung |
| `limit`,`offset` | Zahl | Fensterung |
| `format` | `json` (Default), `csv` | CSV-Antwort (`;`-getrennt, Header-Zeile) |

Der Abonnentenfilter gilt für JSON und CSV vor Sortierung und Fensterung.
Ein ausgelassener oder leerer `minSubscribers`-Parameter verwendet `1`;
`total` zählt die gefilterten Provider. Detailendpunkte bleiben unabhängig
vom Listenfilter erreichbar.

Beispiel `GET /api/v1/providers?version=mql5&sort=monthChange&limit=2`:

```json
{
  "total": 168,
  "count": 2,
  "offset": 0,
  "items": [
    {
      "signalId": "574703",
      "version": "mql5",
      "signalName": "World PEACE Multi FX Algo",
      "subscribers": 668,
      "latestChange": 5,
      "weekChange": 3,
      "monthChange": 42,
      "lastUpdated": "2026-08-27T11:53:12",
      "lastUpdatedUtc": "2026-08-27T09:53:12Z",
      "risk": null,
      "rowColor": null,
      "url": "https://www.mql5.com/en/signals/574703",
      "links": {
        "history": "/api/v1/providers/574703/mql5/history",
        "trades": "/api/v1/providers/574703/mql5/trades",
        "tradesCsv": "/api/v1/providers/574703/mql5/trades.csv",
        "metrics": "/api/v1/providers/574703/mql5/metrics",
        "reports": "/api/v1/providers/574703/mql5/reports"
      }
    }
  ]
}
```

Feldbedeutungen: `latestChange` = Änderung bei der letzten Messung; `weekChange`/`monthChange` = Differenz zur Messung vor exakt 7 bzw. 30 Tagen (±Toleranzfenster); `null` wenn es in dem Fenster keine Messung gab. `risk` und `rowColor` sind manuell gepflegte Werte aus der GUI (kann `null` sein).

CSV-Antwort (bei `format=csv`): Header `signalId;mqlVersion;signalName;subscribers;latestChange;weekChange;monthChange;lastUpdated;risk;rowColor;url`.

### 3.3 `GET /providers/{id}` — ein Provider, alle Versionen

Liefert `{"count":1,"items":[ <wie oben> ]}` — ein Element pro MQL-Version (ein Signal kann als mql4 und mql5 existieren). 404 bei unbekannter ID.

### 3.4 `GET /providers/{id}/{version}` — ein Provider, konkrete Version

Ein einzelnes Provider-Objekt (ohne `count`/`items`-Wrapper).

### 3.5 `GET /providers/{id}/{version}/history` — Abonnenten-Historie

Vollständige Zeitreihe der Abonnentenzahl (aufsteigend).

| Parameter | Wirkung |
|---|---|
| `days=N` | nur Punkte der letzten N Tage (relativ zu jetzt) |
| `from` / `since` | untere Zeitgrenze |
| `to` / `until` | obere Zeitgrenze |
| `limit`, `offset` | Fensterung (aufsteigende Reihenfolge bleibt erhalten) |

Zeitformate für `from`/`to`: Epoch-Millis (`1799927520000`), ISO-Datum (`2026-08-01`), ISO-Datum/Uhrzeit lokal (`2026-08-01T12:00`), ISO-Instant (`2026-08-01T10:00:00Z`).

```json
{
  "signalId": "123",
  "version": "mql5",
  "total": 2,
  "count": 2,
  "points": [
    { "timestamp": "2026-08-20T18:00:05", "timestampUtc": "2026-08-20T16:00:05Z", "subscribers": 42, "change": 0 },
    { "timestamp": "2026-08-27T18:00:10", "timestampUtc": "2026-08-27T16:00:10Z", "subscribers": 45, "change": 3 }
  ]
}
```

`change: 0` im ältesten Punkt bedeutet **Baseline** (erste Sichtung), danach die Differenz zur Vorwoche.

### 3.6 `GET /providers/{id}/{version}/trades` — Tradeliste als JSON

Liest die der Signal-ID eindeutig zugeordnete `<Name>_<id>.csv` des
Versionsordners und parst sie generisch: erste Zeile = Spaltenüberschriften,
jede Zeile ein JSON-Objekt mit diesen Überschriften als Schlüsseln.
Trennzeichen (`;`, `,`, Tab) wird automatisch erkannt,
RFC-4180-Anführungszeichen werden verstanden. Fehlt der reguläre Cache,
kommen direkte Archivordner nur bei eindeutig belegter Signal-ID und
MT4-/MT5-Zuordnung infrage; mehrdeutige Archive bleiben gesperrt.

| Parameter | Wirkung |
|---|---|
| `q=Text` | Volltext-Filter: nur Zeilen, in denen irgendein Feld den Text enthält |
| `limit`, `offset` | Fensterung |

```json
{
  "signalId": "123",
  "version": "mql5",
  "file": {
    "name": "Test_Provider_Alpha_123.csv",
    "sizeBytes": 8124,
    "lastModified": "2026-08-27T12:00:00",
    "href": "/api/v1/providers/123/mql5/trades.csv"
  },
  "header": ["Open Time", "Symbol", "Type", "Volume"],
  "total": 2,
  "count": 2,
  "rows": [
    { "Open Time": "2025.01.02 10:00:00", "Symbol": "EURUSD", "Type": "buy", "Volume": "0.10" },
    { "Open Time": "2025.01.03 11:30:00", "Symbol": "GBPUSD", "Type": "sell", "Volume": "0.20" }
  ]
}
```

Hinweis: `header` bewahrt die originalen Spaltennamen einschließlich Wiederholungen. Die JSON-Zeilen besitzen bei mehrfachen Namen zusätzlich nummerierte Schlüssel: `Time_1`/`Time_2`, `Price_1`/`Price_2`, bei MT5 `Volume_1`/`Volume_2`. Die einfachen Schlüssel enthalten weiterhin den letzten Wert (Altverhalten); für verlustfreie Verarbeitung die nummerierten Schlüssel oder rohe CSV verwenden. Werte sind Strings. 404, wenn keine CSV existiert (z. B. Download noch nie gelaufen oder Provider gefiltert).

### 3.7 `GET /providers/{id}/{version}/trades.csv` — Tradeliste roh

Liefert die CSV-Datei binär (`Content-Type: text/csv; charset=utf-8`, `Content-Disposition: attachment`). Für One-to-One-Weiterverarbeitung/Ablage.

### 3.8 `GET /providers/{id}/{version}/metrics` — Kennzahlen

Parst `<Name>_<id>_root.txt` (wird bei der HTML-Konvertierung im Downloader erzeugt). Zahlen werden als JSON-Nummern geliefert, wo sie als solche erkennbar sind, sonst als String.

```json
{
  "signalId": "123",
  "version": "mql5",
  "file": { "name": "Test_Provider_Alpha_123_root.txt", "sizeBytes": 934, "lastModified": "2026-08-27T12:31:00" },
  "metrics": {
    "Balance": 1234.56,
    "Currency": "USD",
    "Subscribers": 45,
    "MaxDDGraphic": 10.0,
    "EquityDrawdown": 8.5,
    "Average3MonthProfit": 2.25,
    "Average3MonthProfitMonthsIncluded": 3,
    "StabilityValue": 0.75,
    "3MPDD": 0.8123
  },
  "monthProfits": { "2025.06": 1.5, "2025.07": -0.5 },
  "drawdown": [ { "date": "2025.01.05", "value": 1.23 } ]
}
```

`drawdown` ist der gescrubbte Drawdown-Chart (Datum → Prozent). 404, wenn noch nie konvertiert wurde.

`Balance` und die originalen Trade-CSV-Betraege sind native Kontobeträge,
keine automatisch nach USD konvertierten Werte. `Currency` ist der explizite
dreibuchstabige Code aus der Kontostand-Zeile (`USD`, `USC`, `EUR` usw.).
Ohne eindeutigen Beleg, insbesondere bei alten TXT-Caches, lautet das Feld
`"unknown"`. USC wird hier nicht pauschal durch 100 geteilt; Consumer müssen
die Einheit belegen, bevor sie Beträge als USD verwenden.
`Average3MonthProfitMonthsIncluded` nennt bei neu konvertierten Dateien die
tatsächlich gemittelten Monate (höchstens drei, neuester exportierter Monat
ausgeschlossen). Altdateien ohne Zähler belegen keine Drei-Monats-Abdeckung.

Unbekannte Werte für `sort`, `order`, `format`, `version` und
`includeUnchanged` werden an den jeweiligen Katalog-/Events-Endpunkten mit
400 zurückgewiesen. `includeUnchanged=true` schließt Baseline/Nulländerungen
ein; Default und `false` liefern ausschließlich Änderungen.

### 3.9 `GET /providers/{id}/{version}/reports` — Testreport-PDFs

```json
{
  "signalId": "123",
  "version": "mql5",
  "count": 1,
  "items": [
    { "name": "testreport_123.pdf", "sizeBytes": 245760,
      "lastModified": "2026-08-25T09:00:00",
      "href": "/api/v1/providers/123/mql5/reports/testreport_123.pdf" }
  ]
}
```

### 3.10 `GET /providers/{id}/{version}/reports/{name}` — PDF herunterladen

`Content-Type: application/pdf`. Der `name` muss exakt einem Eintrag aus 3.9 entsprechen (URL-encodiert); Pfadangaben und `..` werden abgewiesen (404).

### 3.11 `GET /trades` — Katalog aller Tradelisten

Alle gefundenen CSVs beider Versionsordner, ohne eine konkrete Provider-Abfrage.

| Parameter | Wirkung |
|---|---|
| `version` | nur `mql4` oder `mql5` |
| `id` | nur diese Signal-ID |
| `name` | Teilstring-Filter auf Dateiname |
| `limit`, `offset` | Fensterung |

```json
{
  "total": 214,
  "count": 1,
  "items": [
    { "signalId": "123", "version": "mql5", "fileName": "Test_Provider_Alpha_123.csv",
      "sizeBytes": 8124, "lastModified": "2026-08-27T12:00:00",
      "href": "/api/v1/trades/file/mql5/Test_Provider_Alpha_123.csv" }
  ]
}
```

### 3.12 `GET /trades/file/{version}/{name}` — CSV per Dateiname

Roher CSV-Download analog 3.7. `name` muss exakt im Katalog liegen (Traversal-Schutz).

### 3.13 `GET /events` — neueste Änderungen über alle Signale

Gegenteil von 3.5: nicht je Signal die Historie, sondern die neuesten Messungen global, absteigend — ideal für periodisches Polling „was hat sich geändert?".

| Parameter | Wirkung |
|---|---|
| `limit` | Default 100 |
| `since` / `from`, `to` / `until` | Zeitfenster (Formate wie 3.5) |
| `includeUnchanged=true` | auch Zeilen ohne Änderung (`change=0`) und Baselines liefern |
| `format=csv` | CSV-Ausgabe |

```json
{
  "count": 2,
  "onlyChanges": true,
  "items": [
    { "signalId": "123", "version": "mql5", "signalName": "Test Provider Alpha",
      "timestamp": "2026-08-27T18:00:10", "timestampUtc": "2026-08-27T16:00:10Z",
      "subscribers": 45, "change": 3, "recordType": "EVENT" }
  ]
}
```

`recordType`: `EVENT` (Messung mit Änderung) oder `BASELINE` (erstmalige Sichtung, nur mit `includeUnchanged`).

### 3.14 `GET /summary` — Aggregat-Kennzahlen

Ein Aufruf für Dashboards:

```json
{
  "providers": { "total": 176, "mql4": 8, "mql5": 168 },
  "subscribers": { "total": 3412, "mql4": 22, "mql5": 3390 },
  "newestMeasurement": "2026-08-27T12:04:39",
  "topWeek":  [ { "signalId": "...", "version": "mql5", "signalName": "...", "subscribers": 668, "change": 12 } ],
  "topMonth": [ { "signalId": "...", "version": "mql5", "signalName": "...", "subscribers": 668, "change": 42 } ]
}
```

`topWeek`/`topMonth` enthalten maximal 5 Einträge, nur positive Änderungen, sortiert absteigend.

### 3.15 `GET /openapi.json` — Selbstbeschreibung

OpenAPI-3.0.3-JSON der Leseendpunkte; nutzbar für Codegenerierung
(openapi-generator, Swagger) oder Dokumentations-UIs. Die Stufe-0-Pfade
`/update` und `/update/status` sind dort derzeit nicht enthalten.

---

## 4. Typische Connector-Muster

### 4.1 Periodisches Pollen (empfohlenes Grundmuster)

```
alle N Minuten:
  1. GET /health                      → erreichbar? (Fehler → später erneut)
  2. GET /events?limit=100            → neue Änderungen verarbeiten
     (alternativ: /events?from=<letzter Poll-Zeitpunkt>)
  3. bei Bedarf je geändertem Signal: /providers/{id}/{version}/metrics, /trades, /reports
```

### 4.2 Python

```python
import requests

BASE = "http://192.168.1.50:8089/api/v1"
HEADERS = {"X-API-Token": "mein-token"}   # weglassen, wenn tokenRequired=false

r = requests.get(f"{BASE}/providers", params={"version": "mql5", "sort": "monthChange"}, headers=HEADERS, timeout=10)
r.raise_for_status()
for p in r.json()["items"]:
    print(p["signalName"], p["subscribers"], p["monthChange"])

pdf = requests.get(f"{BASE}/providers/574703/mql5/reports", headers=HEADERS).json()
if pdf["count"]:
    name = pdf["items"][0]["name"]
    data = requests.get(f"{BASE}/providers/574703/mql5/reports/{requests.utils.quote(name)}", headers=HEADERS).content
    open(name, "wb").write(data)
```

### 4.3 C#

```csharp
using var http = new HttpClient { BaseAddress = new Uri("http://192.168.1.50:8089") };
http.DefaultRequestHeaders.Add("X-API-Token", "mein-token");
var providers = await http.GetFromJsonAsync<JsonElement>("/api/v1/providers?minSubscribers=10");
foreach (var item in providers.GetProperty("items").EnumerateArray())
    Console.WriteLine(item.GetProperty("signalName").GetString());
```

### 4.4 Excel / Power Query

Daten → Daten abrufen → Aus dem Web → `http://<rechner>:8089/api/v1/providers?format=csv`
(direkt als Tabelle importierbar; Token: „Anmeldeinformationen bearbeiten" → Anonym, oder URL mit `?token=`).

---

## 5. Fehlerbehandlung & Grenzen

- Datenabrufe sind **lesend**. Die Ausnahme `POST /api/v1/update` aktualisiert
  die lokalen Quelldaten; manuelle Risiko-Werte werden weiterhin über die GUI gepflegt.
- Die API ist nur erreichbar, während der Downloader läuft. Clients sollen Verbindungsfehler tolerant behandeln und erneut versuchen.
- Tradelisten/Kennzahlen existieren nur für Provider, die heruntergeladen und konvertiert wurden; die Datenbank enthält u. U. mehr Signale als es CSVs gibt. Mit `links` im Provider-Objekt prüfen, was existiert (404 = nicht vorhanden, kein Fehlerfall).
- Ein `weekChange`/`monthChange` von `null` ist normal, wenn der Vergleichszeitpunkt außerhalb des Messfensters liegt (weniger als ~5 Tage bzw. ~26 Tage Historie).
- Dateinamen im `reports`/`trades`-Katalog werden serverseitig validiert; generierte Hrefs können direkt (URL-codiert) verwendet werden.

## 6. Internationale Hinweise für Implementierer

- Der Server erlaubt `GET` und `OPTIONS` sowie genau EINEN Schreib-Endpoint `POST /api/v1/update` (Stufe 0, siehe unten); alles andere → 405.
- Antwortgrößen können bei `trades` groß sein (zahlreiche Trades) — `limit`/`offset` nutzen und `total` für Pagination auslesen.
- JSON-Zahlen: `subscribers`, `change`, `sizeBytes` sind Integer; Metrik-Werte sind Number *oder* String (Parser-Fallback) — tolerant parsen.
- Bei `format=csv` ist das Trennzeichen `;` und die Codierung UTF-8 mit BOM.

## 7. Beispiel-Konfiguration anderer Anwendungen („Connector-Felder")

| Feld | Wert |
|---|---|
| Adresse / URL | `http://<downloader-rechner>:8089/api/v1` |
| Authentifizierung | Token im Header `X-API-Token` (nur wenn aktiviert) |
| Health-Check | `GET {base}/health` |
| Hauptdaten | `GET {base}/providers` |
| Änderungsfeed | `GET {base}/events?limit=100` |
| Schemabeschreibung | `GET {base}/openapi.json` |

---

## Stufe 0: Client-Update-Job (SignalKiScanner doc/23, 07.10.2026)

Der SignalKiScanner kann über das Stufe-0-Protokoll die Daten-Aktualisierung
des Clients anstoßen. Der MqlDownloader kennt dazu genau EINEN Schreib-Endpoint —
alle übrigen bleiben rein lesend (POST → 405).

### `POST /api/v1/update` — „Alles ausführen" anstoßen

Request-Body (JSON, alle Felder optional):
```json
{"target": 200, "tradelisten": true, "katalogMaxAlterH": 72}
```
- `target`: wird zur Protokoll-Kompatibilität gelesen, vom MQL-Update-Starter
  aber nicht verwendet. Der Download richtet sich nach den konfigurierten
  `mql4Limit`-/`mql5Limit`-Werten (Default jeweils 1000); ein vollständiger
  Plattformdownload ist dadurch nicht zugesichert. `signaleGeliefert`
  zählt die im jeweiligen Lauf verarbeiteten Signale mit Abonnenten.
- `tradelisten`: bei diesem Client ohne Wirkung (Katalog und Trades sind EIN
  Download) — wird der Protokoll-Kompatibilität wegen angenommen.
- `katalogMaxAlterH` (Default 72): **3-Tage-Regel** — lief der Gesamtprozess
  vor weniger als dieser Stundenzahl erfolgreich (Vermerk
  `config/update_state.json`), wird er komplett übersprungen.

Antwort sofort (ohne auf den Download zu warten):
- `202 {"jobId": "u-…", "status": "gestartet", "bereitsLaufend": false}`
- läuft schon ein Update: `200` mit `bereitsLaufend: true`
- GUI beschäftigt (Button-Download/Konvertierung läuft): `409 {"error": …}`
- Token gilt wie bei GET. Leerer Body nutzt Defaults; sonst ist ein gültiges JSON-Objekt erforderlich. `target` muss eine positive ganze Zahl, `katalogMaxAlterH` eine nichtnegative ganze Zahl und `tradelisten` ein Boolean sein. Falsche Typen, doppelte Root-Schlüssel, Zusatzinhalt oder ungültiges JSON ergeben `400`. Unbekannte Felder werden ignoriert.

### `GET /api/v1/update/status` — Job-Status (Poll)

```json
{"jobId": "u-…", "state": "running", "phase": "mql5", "done": 0, "total": 0,
 "message": "MQL5-Download laeuft (Limit: 100 Provider)", "startedAt": "…",
 "finishedAt": null,
 "ergebnis": {"signaleGeliefert": 52, "tradelistenNeu": null,
              "tradelistenAktualisiert": null, "tradelistenFehler": 0,
              "tradelistenAbgebrochen": false, "katalogUebersprungen": false,
              "datenstand": null, "hinweise": ["Signale MIT Abonnenten: …"]},
 "error": null}
```
`state`: `idle` (noch nie gelaufen) | `running` | `done` | `error`.
Die Kaskade führt MQL4 → MQL5 → Konvertierung aus. Während der Downloads
kann der Status-Poller `phase: "download"` melden; die Plattform und ihr
konfiguriertes Limit stehen dann in `message`. `signaleGeliefert` zählt die
im Lauf verarbeiteten Signale mit Abonnenten, nicht den vollständigen
Plattformbestand. Eine `202`-Antwort bestätigt zunächst nur die Jobannahme;
Fortschritt, Abschluss und Fehler stehen im gepollten Status. `ergebnis` ist ein JSON-Objekt und `hinweise` ein Array. Download-/Konvertierungsfehler ergeben `error`, positive `tradelistenFehler` und `tradelistenAbgebrochen=true`; ein erfolgreicher 3-Tage-Vermerk wird dann nicht geschrieben. Terminale Zustände bleiben auch gegenüber einem verspäteten Status-Poller erhalten.


### Reine Abonnentenmessung (23.10.2026)

`GET /api/v1/health` meldet `abonnentenRefresh: true`. Erst dieser Nachweis
bestaetigt Unterstuetzung fuer `POST /api/v1/update` mit
`{"nurAbonnenten":true,"target":200,"tradelisten":false,"katalogMaxAlterH":0}`.
`nurAbonnenten` muss ein JSON-Boolean sein; `target` eine Ganzzahl von 1 bis 200.
Ohne `nurAbonnenten:true` gelten die bisherigen Download-/GUI-Wege unveraendert.

Der neue Modus liest ausschliesslich nach Abonnenten absteigend sortierte
MT4-/MT5-Listen bis zum ersten belegten Nullwert oder zur Kandidatengrenze.
Er liest je Plattform hoechstens `target` Kandidaten, damit nach der Vereinigung
die globalen hoechstens `target` Anbieter mit mindestens einem Abonnenten
korrekt ausgewaehlt werden koennen. Einzelprofile, Kennzahlen-Konvertierung und
Tradelisten werden dabei nicht abgerufen. Beide Listen muessen erfolgreich
verifiziert sein; erst dann werden aktuelle Mitgliedschaft, Zaehler und Historien
atomar publiziert. Alte Profile, Risiko-/Farbnotizen und Historien bleiben erhalten.
Direkte Anbieter-/History-/Metrics-Abfragen bleiben fuer Archividentitaeten
lesbar. Die Katalogliste enthaelt nach diesem Modus nur die aktuelle Auswahl.
Ein erfolgreicher spaeterer regulaerer REST-Vollupdate stellt dessen bisherige
Katalogpolitik wieder her.

Der Job meldet `ergebnis.nurAbonnenten:true`, die tatsaechlich gelieferte Anzahl
und null neue/aktualisierte Tradelisten. Der Vollkatalog-/Profil-Frischemarker
wird vor der Publikation ungueltig gemacht; eine reine Zaehlerbeobachtung darf
keine aktuelle Kennzahlen- oder Tradepruefung vortaeuschen.
