package rest;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import config.ConfigurationManager;
import database.DatabaseManager;
import database.SubscriberEvent;
import database.SubscriberHistoryPoint;
import database.SubscriberStat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Eingebetteter REST-Server (JDK HttpServer, keine neue Abhängigkeit), der
 * den Datenbestand des Downloaders im LAN als HTTP+JSON bereitstellt:
 * Provider-Statistiken, Abonnenten-Historien und -Änderungen, Trading-History
 * (CSV und als JSON-Zeilen), Kennzahlen aus den konvertierten Root-Seiten
 * sowie die Testreport-PDFs. Rein lesend. optionaler Token-Schutz, CORS für
 * Web-Clients, OpenAPI-Beschreibung unter /api/v1/openapi.json.
 */
public class RestApiServer {
    private static final Logger logger = LogManager.getLogger(RestApiServer.class);
    private static final String API_PREFIX = "/api/v1";
    private static final int DEFAULT_PAGE_SIZE = 1000;

    private final DatabaseManager databaseManager;
    private final ConfigurationManager configManager;
    private final long startedAt = System.currentTimeMillis();
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;

    public RestApiServer(DatabaseManager databaseManager, ConfigurationManager configManager) {
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    // ------------------------------------------------------------------
    // Lebenszyklus
    // ------------------------------------------------------------------

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        int port = configManager.getApiPort();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::dispatch);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "rest-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        running = true;
        logger.info("REST-API gestartet auf Port {} - Endpunkte unter {}/", port, API_PREFIX);
        logger.info("REST-API erreichbar unter: {}", getLocalUrls());
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        running = false;
        logger.info("REST-API gestoppt");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return configManager.getApiPort();
    }

    /** Best-effort-Anzeige der Adressen für Statusanzeigen im GUI/Log. */
    public String getLocalUrls() {
        StringBuilder sb = new StringBuilder();
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local != null && !local.isLoopbackAddress()) {
                sb.append("http://").append(local.getHostAddress()).append(':').append(getPort());
            }
        } catch (IOException ignored) {
            // Best effort: localhost bleibt als Fallback
        }
        if (sb.length() > 0) {
            sb.append(" (lokal: http://localhost:").append(getPort()).append(")");
        } else {
            sb.append("http://localhost:").append(getPort());
        }
        sb.append(API_PREFIX).append("/health");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            applyCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                send(exchange, Resp.empty(204));
                return;
            }
            if (!"GET".equals(method)) {
                send(exchange, Resp.json(405, errorBody("Nur GET und OPTIONS werden unterstützt")));
                return;
            }

            Map<String, String> query = parseQuery(exchange);
            if (!isAuthorized(exchange, query)) {
                send(exchange, Resp.json(401, errorBody(
                        "Ungültiger oder fehlender API-Token (X-API-Token, Bearer oder ?token=)")));
                return;
            }

            List<String> segs = segments(exchange);
            Resp response;
            try {
                response = route(segs, query);
            } catch (IllegalArgumentException e) {
                response = Resp.json(400, errorBody(e.getMessage()));
            }
            send(exchange, response);
        } catch (Exception e) {
            logger.error("Fehler in der REST-API ({})", exchange.getRequestURI(), e);
            try {
                send(exchange, Resp.json(500, errorBody("Interner Fehler: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()))));
            } catch (IOException ignored) {
                // Antwort war nicht mehr möglich (Client weg)
            }
        } finally {
            exchange.close();
        }
    }

    private Resp route(List<String> segs, Map<String, String> query) throws IOException {
        // erwartete Form: api, v1, ...
        if (segs.isEmpty() || !"api".equals(segs.get(0)) || segs.size() < 2
                || !"v1".equals(segs.get(1))) {
            return Resp.json(404, errorBody("Unbekannter Pfad. API unter " + API_PREFIX + "/..."));
        }
        List<String> path = segs.subList(2, segs.size());
        if (path.isEmpty()) {
            return Resp.json(404, errorBody("Pfad fehlt. Siehe " + API_PREFIX + "/openapi.json"));
        }

        String head = path.get(0);
        if (path.size() == 1) {
            if ("health".equals(head)) {
                return health();
            }
            if ("providers".equals(head)) {
                return providers(query);
            }
            if ("trades".equals(head)) {
                return tradesCatalog(query);
            }
            if ("events".equals(head)) {
                return events(query);
            }
            if ("summary".equals(head)) {
                return summary();
            }
            if ("openapi.json".equals(head)) {
                return Resp.json(200, OpenApiDoc.toJson());
            }
            return notFound(head);
        }

        // /trades/file/{version}/{name}
        if ("trades".equals(head)) {
            if (path.size() == 4 && "file".equals(path.get(1))) {
                return tradeCsvByFileName(path.get(2), path.get(3));
            }
            return notFound(join(path));
        }

        // ab hier: /providers/{id}[/{version}[/sub]]
        if (!"providers".equals(head)) {
            return notFound(join(path));
        }
        String signalId = path.get(1);
        if (path.size() == 2) {
            return providerDetailAll(signalId, query);
        }
        String version = path.get(2);
        if (path.size() == 3) {
            return providerDetailVersion(signalId, version, query);
        }
        String sub = path.get(3);
        if (path.size() == 4) {
            if ("history".equals(sub)) {
                return providerHistory(signalId, version, query);
            }
            if ("trades".equals(sub)) {
                return providerTrades(signalId, version, query);
            }
            if ("trades.csv".equals(sub)) {
                return providerTradesCsv(signalId, version);
            }
            if ("metrics".equals(sub)) {
                return providerMetrics(signalId, version);
            }
            if ("reports".equals(sub)) {
                return providerReports(signalId, version);
            }
            return notFound(join(path));
        }
        if (path.size() == 5 && "reports".equals(sub)) {
            return providerReportDownload(signalId, version, path.get(4));
        }
        return notFound(join(path));
    }

    // ------------------------------------------------------------------
    // Endpunkte
    // ------------------------------------------------------------------

    private Resp health() {
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("status").value("ok")
            .name("service").value("MqlDownloader")
            .name("apiVersion").value("v1")
            .name("uptimeSeconds").value((System.currentTimeMillis() - startedAt) / 1000)
            .name("serverTime").value(iso(new Timestamp(System.currentTimeMillis())))
            .name("providers").value(databaseManager.getAllSubscriberStatistics().size())
            .name("tokenRequired").value(!configManager.getApiToken().isEmpty())
        .endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providers(Map<String, String> query) {
        List<SubscriberStat> stats = new ArrayList<>(databaseManager.getAllSubscriberStatistics());
        applyProviderFilters(stats, query);
        sortProviders(stats, query);
        int total = stats.size();
        int[] page = pageWindow(query, total);
        List<SubscriberStat> window = stats.subList(page[0], page[1]);

        if ("csv".equalsIgnoreCase(query.get("format"))) {
            return providersCsv(window);
        }

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("total").value(total)
            .name("count").value(window.size())
            .name("offset").value(page[0])
            .name("items").beginArray();
        for (SubscriberStat stat : window) {
            writeStat(w, stat, true);
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providersCsv(List<SubscriberStat> stats) {
        String[] header = {"signalId", "mqlVersion", "signalName", "subscribers", "latestChange",
                "weekChange", "monthChange", "lastUpdated", "risk", "rowColor", "url"};
        StringBuilder csv = new StringBuilder();
        csv.append(joinCsv(header)).append("\r\n");
        for (SubscriberStat stat : stats) {
            csv.append(joinCsv(new String[]{
                    stat.getSignalId(), stat.getMqlVersion(), stat.getSignalName(),
                    String.valueOf(stat.getSubscribers()), String.valueOf(stat.getLatestChange()),
                    stat.getWeekChange() == null ? "" : String.valueOf(stat.getWeekChange()),
                    stat.getMonthChange() == null ? "" : String.valueOf(stat.getMonthChange()),
                    iso(stat.getLastUpdated()), stat.getRisk() == null ? "" : stat.getRisk(),
                    stat.getRowColor() == null ? "" : stat.getRowColor(), stat.getUrl()
            })).append("\r\n");
        }
        return Resp.csv(200, csv.toString(), "providers.csv");
    }

    private Resp providerDetailAll(String signalId, Map<String, String> query) {
        List<SubscriberStat> matches = new ArrayList<>();
        for (SubscriberStat stat : databaseManager.getAllSubscriberStatistics()) {
            if (stat.getSignalId().equals(signalId)) {
                matches.add(stat);
            }
        }
        if (matches.isEmpty()) {
            return Resp.json(404, errorBody("Kein Provider mit ID " + signalId + " gefunden"));
        }
        JsonWriter w = new JsonWriter();
        w.beginObject().name("count").value(matches.size()).name("items").beginArray();
        for (SubscriberStat stat : matches) {
            writeStat(w, stat, true);
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerDetailVersion(String signalId, String version, Map<String, String> query) {
        SubscriberStat stat = findStat(signalId, version);
        if (stat == null) {
            return Resp.json(404, errorBody("Kein Provider " + signalId + " (" + version + ") gefunden"));
        }
        JsonWriter w = new JsonWriter();
        w.beginObject();
        writeStat(w, stat, true);
        w.endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerHistory(String signalId, String version, Map<String, String> query) {
        if (findStat(signalId, version) == null) {
            return Resp.json(404, errorBody("Kein Provider " + signalId + " (" + version + ") gefunden"));
        }
        List<SubscriberHistoryPoint> history =
                databaseManager.getSubscriberHistory(signalId, normalizeVersion(version));
        Timestamp since = parseSince(query);
        Timestamp until = parseUntil(query);
        String daysRaw = query.get("days");
        if (daysRaw != null && !daysRaw.trim().isEmpty()) {
            long days = parseLong(daysRaw, 0, "days");
            Timestamp dayLimit = new Timestamp(System.currentTimeMillis() - days * 24L * 3600L * 1000L);
            if (since == null || dayLimit.after(since)) {
                since = dayLimit;
            }
        }
        int limit = (int) parseLong(query.get("limit"), DEFAULT_PAGE_SIZE, "limit");
        int offset = (int) parseLong(query.get("offset"), 0, "offset");

        List<SubscriberHistoryPoint> filtered = new ArrayList<>();
        for (SubscriberHistoryPoint point : history) {
            if (since != null && point.getTimestamp().before(since)) {
                continue;
            }
            if (until != null && point.getTimestamp().after(until)) {
                continue;
            }
            filtered.add(point);
        }
        int total = filtered.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("signalId").value(signalId)
            .name("version").value(normalizeVersion(version))
            .name("total").value(total)
            .name("count").value(to - from)
            .name("points").beginArray();
        for (SubscriberHistoryPoint point : filtered.subList(from, to)) {
            w.beginObject()
                .name("timestamp").value(iso(point.getTimestamp()))
                .name("timestampUtc").value(isoUtc(point.getTimestamp()))
                .name("subscribers").value(point.getSubscribers())
                .name("change").value(point.getChangeAmount())
            .endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerTrades(String signalId, String version, Map<String, String> query) throws IOException {
        File csvFile = resolveTradeCsv(signalId, version);
        if (csvFile == null) {
            return Resp.json(404, errorBody("Keine Trading-History-CSV für " + signalId
                    + " (" + version + ") gefunden"));
        }
        CsvParser parser = CsvParser.parse(csvFile);
        String needle = query.get("q");
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : parser.getRows()) {
            if (needle != null && !needle.trim().isEmpty() && !rowContains(row, needle.trim())) {
                continue;
            }
            rows.add(row);
        }
        int total = rows.size();
        int offset = (int) parseLong(query.get("offset"), 0, "offset");
        int limit = (int) parseLong(query.get("limit"), DEFAULT_PAGE_SIZE, "limit");
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("signalId").value(signalId)
            .name("version").value(normalizeVersion(version))
            .name("file").beginObject()
                .name("name").value(csvFile.getName())
                .name("sizeBytes").value(csvFile.length())
                .name("lastModified").value(iso(new Timestamp(csvFile.lastModified())))
                .name("href").value(API_PREFIX + "/providers/" + signalId + "/" + normalizeVersion(version) + "/trades.csv")
            .endObject()
            .name("header").beginArray();
        for (String column : parser.getHeader()) {
            w.value(column);
        }
        w.endArray()
            .name("total").value(total)
            .name("count").value(to - from)
            .name("rows").beginArray();
        for (Map<String, String> row : rows.subList(from, to)) {
            w.beginObject();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                w.name(entry.getKey()).value(entry.getValue());
            }
            w.endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerTradesCsv(String signalId, String version) throws IOException {
        File csvFile = resolveTradeCsv(signalId, version);
        if (csvFile == null) {
            return Resp.json(404, errorBody("Keine Trading-History-CSV für " + signalId
                    + " (" + version + ") gefunden"));
        }
        return Resp.file(200, csvFile, "text/csv; charset=utf-8");
    }

    private Resp providerMetrics(String signalId, String version) throws IOException {
        File rootTxt = resolveRootTxt(signalId, version);
        if (rootTxt == null) {
            return Resp.json(404, errorBody("Keine konvertierten Kennzahlen (*_root.txt) für "
                    + signalId + " (" + version + ") gefunden. Konvertierung ausführen?"));
        }
        MetricsParser parser = MetricsParser.parse(rootTxt);
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("signalId").value(signalId)
            .name("version").value(normalizeVersion(version))
            .name("file").beginObject()
                .name("name").value(rootTxt.getName())
                .name("sizeBytes").value(rootTxt.length())
                .name("lastModified").value(iso(new Timestamp(rootTxt.lastModified())))
            .endObject()
            .name("metrics").beginObject();
        for (Map.Entry<String, String> entry : parser.getMetrics().entrySet()) {
            w.name(entry.getKey()).numberOrString(entry.getValue());
        }
        w.endObject()
            .name("monthProfits").beginObject();
        for (Map.Entry<String, String> entry : parser.getMonthProfits().entrySet()) {
            w.name(entry.getKey()).numberOrString(entry.getValue());
        }
        w.endObject()
            .name("drawdown").beginArray();
        for (MetricsParser.DrawdownPoint point : parser.getDrawdownPoints()) {
            w.beginObject()
                .name("date").value(point.getDate())
                .name("value").numberOrString(point.getValue())
            .endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerReports(String signalId, String version) {
        List<File> reports = ProviderFiles.findReports(configManager.getAnalysePath(), signalId);
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("signalId").value(signalId)
            .name("version").value(normalizeVersion(version))
            .name("count").value(reports.size())
            .name("items").beginArray();
        for (File report : reports) {
            String href = API_PREFIX + "/providers/" + signalId + "/" + normalizeVersion(version)
                    + "/reports/" + encodePathSegment(report.getName());
            w.beginObject()
                .name("name").value(report.getName())
                .name("sizeBytes").value(report.length())
                .name("lastModified").value(iso(new Timestamp(report.lastModified())))
                .name("href").value(href)
            .endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp providerReportDownload(String signalId, String version, String fileName) throws IOException {
        List<File> reports = ProviderFiles.findReports(configManager.getAnalysePath(), signalId);
        File report = ProviderFiles.findByName(reports, fileName);
        if (report == null) {
            return Resp.json(404, errorBody("Kein Report mit Namen " + fileName
                    + " für Signal " + signalId));
        }
        return Resp.file(200, report, "application/pdf");
    }

    private Resp tradesCatalog(Map<String, String> query) {
        String versionFilter = query.get("version");
        String idFilter = query.get("id");
        String nameFilter = query.get("name");
        if (nameFilter != null) {
            nameFilter = nameFilter.toLowerCase(Locale.ROOT);
        }
        int limit = (int) parseLong(query.get("limit"), DEFAULT_PAGE_SIZE, "limit");
        int offset = (int) parseLong(query.get("offset"), 0, "offset");

        List<String[]> items = new ArrayList<>(); // versionFolder, fileName, signalId
        for (String folder : new String[]{"mql4", "mql5"}) {
            if (versionFilter != null && !versionFilter.trim().isEmpty()
                    && !folder.equals(ProviderFiles.normalizeVersionFolder(versionFilter))) {
                continue;
            }
            File dir = versionDir(folder);
            for (File csv : ProviderFiles.listTradeCsvs(dir)) {
                String signalId = ProviderFiles.extractSignalId(csv.getName());
                if (idFilter != null && !idFilter.trim().isEmpty()
                        && !idFilter.trim().equals(signalId)) {
                    continue;
                }
                if (nameFilter != null && !csv.getName().toLowerCase(Locale.ROOT).contains(nameFilter)) {
                    continue;
                }
                items.add(new String[]{folder, csv.getName(), signalId});
            }
        }
        int total = items.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("total").value(total)
            .name("count").value(to - from)
            .name("items").beginArray();
        for (String[] item : items.subList(from, to)) {
            File csv = new File(versionDir(item[0]), item[1]);
            w.beginObject()
                .name("signalId").value(item[2])
                .name("version").value(item[0])
                .name("fileName").value(item[1])
                .name("sizeBytes").value(csv.length())
                .name("lastModified").value(iso(new Timestamp(csv.lastModified())))
                .name("href").value(API_PREFIX + "/trades/file/" + item[0] + "/"
                        + encodePathSegment(item[1]))
            .endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp tradeCsvByFileName(String versionFolder, String fileName) throws IOException {
        // Dateinamen sind in segments() bereits URL-dekodiert
        String folder = ProviderFiles.normalizeVersionFolder(versionFolder);
        if (folder == null) {
            return Resp.json(404, errorBody("Unbekannte Version " + versionFolder));
        }
        List<File> csvs = ProviderFiles.listTradeCsvs(versionDir(folder));
        File csv = ProviderFiles.findByName(csvs, fileName);
        if (csv == null) {
            return Resp.json(404, errorBody("Keine CSV mit Namen " + fileName + " im Ordner " + folder));
        }
        return Resp.file(200, csv, "text/csv; charset=utf-8");
    }

    private Resp events(Map<String, String> query) {
        Timestamp since = parseSince(query);
        Timestamp until = parseUntil(query);
        boolean onlyChanges = !"false".equalsIgnoreCase(query.get("includeUnchanged"));
        int limit = (int) parseLong(query.get("limit"), 100, "limit");
        List<SubscriberEvent> events = databaseManager.getRecentEvents(since, until, onlyChanges, limit);

        if ("csv".equalsIgnoreCase(query.get("format"))) {
            StringBuilder csv = new StringBuilder();
            csv.append(joinCsv(new String[]{"timestamp", "signalId", "mqlVersion", "signalName",
                    "subscribers", "change", "recordType"})).append("\r\n");
            for (SubscriberEvent event : events) {
                csv.append(joinCsv(new String[]{
                        iso(event.getTimestamp()), event.getSignalId(), event.getMqlVersion(),
                        event.getSignalName(), String.valueOf(event.getSubscribers()),
                        String.valueOf(event.getChangeAmount()), event.getRecordType()
                })).append("\r\n");
            }
            return Resp.csv(200, csv.toString(), "events.csv");
        }

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("count").value(events.size())
            .name("onlyChanges").value(onlyChanges)
            .name("items").beginArray();
        for (SubscriberEvent event : events) {
            w.beginObject()
                .name("signalId").value(event.getSignalId())
                .name("version").value(event.getMqlVersion())
                .name("signalName").value(event.getSignalName())
                .name("timestamp").value(iso(event.getTimestamp()))
                .name("timestampUtc").value(isoUtc(event.getTimestamp()))
                .name("subscribers").value(event.getSubscribers())
                .name("change").value(event.getChangeAmount())
                .name("recordType").value(event.getRecordType())
            .endObject();
        }
        w.endArray().endObject();
        return Resp.json(200, w.toString());
    }

    private Resp summary() {
        List<SubscriberStat> stats = databaseManager.getAllSubscriberStatistics();
        int total = 0;
        int mql4Count = 0;
        int mql5Count = 0;
        int subsMql4 = 0;
        int subsMql5 = 0;
        Timestamp newest = null;
        for (SubscriberStat stat : stats) {
            total++;
            if ("mql4".equals(stat.getMqlVersion())) {
                mql4Count++;
                subsMql4 += stat.getSubscribers();
            } else if ("mql5".equals(stat.getMqlVersion())) {
                mql5Count++;
                subsMql5 += stat.getSubscribers();
            }
            if (stat.getLastUpdated() != null
                    && (newest == null || stat.getLastUpdated().after(newest))) {
                newest = stat.getLastUpdated();
            }
        }

        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("providers").beginObject()
                .name("total").value(total)
                .name("mql4").value(mql4Count)
                .name("mql5").value(mql5Count)
            .endObject()
            .name("subscribers").beginObject()
                .name("total").value(subsMql4 + subsMql5)
                .name("mql4").value(subsMql4)
                .name("mql5").value(subsMql5)
            .endObject()
            .name("newestMeasurement").value(iso(newest));
        writeTopMovers(w, "topWeek", stats, weekChangeComparator());
        writeTopMovers(w, "topMonth", stats, monthChangeComparator());
        w.endObject();
        return Resp.json(200, w.toString());
    }

    private void writeTopMovers(JsonWriter w, String name, List<SubscriberStat> stats,
                                Comparator<SubscriberStat> comparator) {
        List<SubscriberStat> copy = new ArrayList<>(stats);
        copy.sort(comparator);
        w.name(name).beginArray();
        int count = 0;
        for (SubscriberStat stat : copy) {
            Integer change = comparator == weekChangeComparator()
                    ? stat.getWeekChange() : stat.getMonthChange();
            if (change == null || change <= 0) {
                continue;
            }
            w.beginObject()
                .name("signalId").value(stat.getSignalId())
                .name("version").value(stat.getMqlVersion())
                .name("signalName").value(stat.getSignalName())
                .name("subscribers").value(stat.getSubscribers())
                .name("change").value(change)
            .endObject();
            if (++count >= 5) {
                break;
            }
        }
        w.endArray();
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private void writeStat(JsonWriter w, SubscriberStat stat, boolean withLinks) {
        w.beginObject()
            .name("signalId").value(stat.getSignalId())
            .name("version").value(stat.getMqlVersion())
            .name("signalName").value(stat.getSignalName())
            .name("subscribers").value(stat.getSubscribers())
            .name("latestChange").value(stat.getLatestChange());
        if (stat.getWeekChange() != null) {
            w.name("weekChange").value(stat.getWeekChange());
        } else {
            w.name("weekChange").nullValue();
        }
        if (stat.getMonthChange() != null) {
            w.name("monthChange").value(stat.getMonthChange());
        } else {
            w.name("monthChange").nullValue();
        }
        w.name("lastUpdated").value(iso(stat.getLastUpdated()))
            .name("lastUpdatedUtc").value(isoUtc(stat.getLastUpdated()))
            .name("risk").value(stat.getRisk())
            .name("rowColor").value(stat.getRowColor())
            .name("url").value(stat.getUrl());
        if (withLinks) {
            String base = API_PREFIX + "/providers/" + stat.getSignalId() + "/" + stat.getMqlVersion();
            w.name("links").beginObject()
                .name("history").value(base + "/history")
                .name("trades").value(base + "/trades")
                .name("tradesCsv").value(base + "/trades.csv")
                .name("metrics").value(base + "/metrics")
                .name("reports").value(base + "/reports")
            .endObject();
        }
        w.endObject();
    }

    private SubscriberStat findStat(String signalId, String version) {
        String normalized = normalizeVersion(version);
        for (SubscriberStat stat : databaseManager.getAllSubscriberStatistics()) {
            if (stat.getSignalId().equals(signalId) && stat.getMqlVersion().equals(normalized)) {
                return stat;
            }
        }
        return null;
    }

    private File resolveTradeCsv(String signalId, String version) {
        String folder = ProviderFiles.normalizeVersionFolder(version);
        if (folder == null) {
            return null;
        }
        return ProviderFiles.findTradeCsv(versionDir(folder), signalId);
    }

    private File resolveRootTxt(String signalId, String version) {
        String folder = ProviderFiles.normalizeVersionFolder(version);
        if (folder == null) {
            return null;
        }
        return ProviderFiles.findRootTxt(versionDir(folder), signalId);
    }

    private File versionDir(String folder) {
        return new File(configManager.getBaseDownloadPath(), folder);
    }

    private static String normalizeVersion(String version) {
        String folder = ProviderFiles.normalizeVersionFolder(version);
        return folder != null ? folder : (version == null ? "" : version.trim().toLowerCase(Locale.ROOT));
    }

    private void applyProviderFilters(List<SubscriberStat> stats, Map<String, String> query) {
        String version = query.get("version");
        if (version != null && !version.trim().isEmpty()) {
            String normalized = ProviderFiles.normalizeVersionFolder(version);
            stats.removeIf(stat -> !stat.getMqlVersion().equals(normalized));
        }
        String name = query.get("name");
        if (name != null && !name.trim().isEmpty()) {
            String needle = name.trim().toLowerCase(Locale.ROOT);
            stats.removeIf(stat -> stat.getSignalName() == null
                    || !stat.getSignalName().toLowerCase(Locale.ROOT).contains(needle));
        }
        String minSubscribers = query.get("minSubscribers");
        if (minSubscribers != null && !minSubscribers.trim().isEmpty()) {
            int min = (int) parseLong(minSubscribers, 0, "minSubscribers");
            stats.removeIf(stat -> stat.getSubscribers() < min);
        }
    }

    private void sortProviders(List<SubscriberStat> stats, Map<String, String> query) {
        String sort = query.get("sort");
        boolean ascending = !"desc".equalsIgnoreCase(query.get("order"));
        Comparator<SubscriberStat> comparator;
        if (sort == null || sort.trim().isEmpty() || "subscribers".equals(sort)) {
            comparator = Comparator.comparingInt(SubscriberStat::getSubscribers);
        } else if ("name".equals(sort)) {
            comparator = Comparator.comparing(SubscriberStat::getSignalName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else if ("latestChange".equals(sort)) {
            comparator = Comparator.comparingInt(SubscriberStat::getLatestChange);
        } else if ("weekChange".equals(sort)) {
            comparator = Comparator.comparing(SubscriberStat::getWeekChange,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("monthChange".equals(sort)) {
            comparator = Comparator.comparing(SubscriberStat::getMonthChange,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("lastUpdated".equals(sort)) {
            comparator = Comparator.comparing(SubscriberStat::getLastUpdated,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparingInt(SubscriberStat::getSubscribers);
        }
        if (!ascending) {
            comparator = comparator.reversed();
        }
        stats.sort(comparator);
    }

    private Comparator<SubscriberStat> weekChangeComparator() {
        return Comparator.comparing(SubscriberStat::getWeekChange,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<SubscriberStat> monthChangeComparator() {
        return Comparator.comparing(SubscriberStat::getMonthChange,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** {from, to} als Schnittfenster der Liste. */
    private int[] pageWindow(Map<String, String> query, int total) {
        int offset = (int) parseLong(query.get("offset"), 0, "offset");
        int limit = (int) parseLong(query.get("limit"), DEFAULT_PAGE_SIZE, "limit");
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        return new int[]{from, to};
    }

    private static boolean rowContains(Map<String, String> row, String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        for (String value : row.values()) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private static long parseLong(String raw, long fallback, String param) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter '" + param + "' muss eine Zahl sein: " + raw);
        }
    }

    private static Timestamp parseSince(Map<String, String> query) {
        return parseTimeParameter(query.get("from"), query.get("since"));
    }

    private static Timestamp parseUntil(Map<String, String> query) {
        return parseTimeParameter(query.get("to"));
    }

    private static Timestamp parseTimeParameter(String... values) {
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            return new Timestamp(parseFlexibleDateTime(value.trim()));
        }
        return null;
    }

    /** Akzeptiert Epoch-Millis, ISO-Datum, ISO-Datum/Uhrzeit und ISO mit Offset. */
    private static long parseFlexibleDateTime(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notEpoch) {
            // weiter mit Textformaten
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException notInstant) {
            // weiter
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(
                    java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException notLocalDateTime) {
            // weiter
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(
                    java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException notDate) {
            throw new IllegalArgumentException(
                    "Zeitparameter nicht erkennbar (Epoch-Millis, ISO-Datum oder ISO-Datum/Uhrzeit erwartet): " + value);
        }
    }

    private static String iso(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String isoUtc(Timestamp ts) {
        return ts == null ? null : ts.toInstant().toString();
    }

    private static String encodePathSegment(String segment) {
        // URLEncoder ist fuer Query-Parameter gedacht und encodiert Leerzeichen
        // als '+'; in Pfadsegmenten muss %20 verwendet werden.
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String join(List<String> parts) {
        return "/" + String.join("/", parts);
    }

    private static String joinCsv(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(csvEscape(cells[i]));
        }
        return sb.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // ------------------------------------------------------------------
    // HTTP-Mechanik
    // ------------------------------------------------------------------

    private boolean isAuthorized(HttpExchange exchange, Map<String, String> query) {
        String expected = configManager.getApiToken();
        if (expected.isEmpty()) {
            return true;
        }
        String provided = exchange.getRequestHeaders().getFirst("X-API-Token");
        if (provided == null || provided.trim().isEmpty()) {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                provided = authorization.substring("Bearer ".length()).trim();
            }
        }
        if ((provided == null || provided.trim().isEmpty()) && query.containsKey("token")) {
            provided = query.get("token");
        }
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static void applyCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
                "X-API-Token, Authorization, Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
    }

    private static List<String> segments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        List<String> segs = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                segs.add(part);
            }
        }
        return segs;
    }

    private static Map<String, String> parseQuery(HttpExchange exchange) {
        Map<String, String> query = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                query.put(URLDecoder.decode(key, StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
            } catch (java.io.UnsupportedEncodingException impossible) {
                // UTF-8 existiert immer
            }
        }
        return query;
    }

    private static String errorBody(String message) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("error").value(message).endObject();
        return w.toString();
    }

    private static Resp notFound(String path) {
        return Resp.json(404, errorBody("Unbekannter Pfad: " + path));
    }

    private static void send(HttpExchange exchange, Resp response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType);
        if (response.fileName != null) {
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + response.fileName.replace("\"", "") + "\"");
        }
        exchange.sendResponseHeaders(response.status, response.body.length == 0 ? -1 : response.body.length);
        if (response.body.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.body);
            }
        }
    }

    /** Vorbereitete Antwort. */
    private static final class Resp {
        final int status;
        final String contentType;
        final byte[] body;
        final String fileName;

        private Resp(int status, String contentType, byte[] body, String fileName) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.fileName = fileName;
        }

        static Resp json(int status, String json) {
            return new Resp(status, "application/json; charset=utf-8",
                    json.getBytes(StandardCharsets.UTF_8), null);
        }

        static Resp csv(int status, String content, String fileName) {
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[bom.length + body.length];
            System.arraycopy(bom, 0, combined, 0, bom.length);
            System.arraycopy(body, 0, combined, bom.length, body.length);
            return new Resp(status, "text/csv; charset=utf-8", combined, fileName);
        }

        static Resp file(int status, File file, String contentType) throws IOException {
            return new Resp(status, contentType, Files.readAllBytes(file.toPath()), file.getName());
        }

        static Resp empty(int status) {
            return new Resp(status, "application/json; charset=utf-8", new byte[0], null);
        }
    }
}
