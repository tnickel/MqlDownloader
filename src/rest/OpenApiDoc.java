package rest;

/**
 * Erzeugt eine OpenAPI-3-Beschreibung der REST-API. Damit lassen sich
 * Connectoren in anderen Anwendungen generieren oder z. B. Swagger-UI
 * auf die Schnittstelle zeigen.
 */
final class OpenApiDoc {

    private OpenApiDoc() {
    }

    static String toJson() {
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("openapi").value("3.0.3")
            .name("info").beginObject()
                .name("title").value("MqlDownloader REST API")
                .name("version").value("1.0.0")
                .name("description").value("Lesezugriff auf Signal-Provider-Statistiken, "
                        + "Abonnenten-Historien, Trading-History-CSVs, Kennzahlen und Testreport-PDFs. "
                        + "Falls ein API-Token konfiguriert ist, muss er im Header X-API-Token, "
                        + "als Authorization: Bearer oder als Query-Parameter token mitgegeben werden.")
            .endObject()
            .name("servers").beginArray()
                .beginObject().name("url").value("/").endObject()
            .endArray()
            .name("security").beginArray()
                .beginObject().name("ApiKeyToken").beginArray().endArray().endObject()
            .endArray()
            .name("paths").beginObject();

        path(w, "/api/v1/health", "Verbindungs- und Gesundheitstest", true);
        path(w, "/api/v1/providers", "Alle Signal-Provider mit Abonnenten und Deltas", false);
        path(w, "/api/v1/providers/{id}", "Provider über alle MQL-Versionen", false);
        path(w, "/api/v1/providers/{id}/{version}", "Provider-Detail einer Version", false);
        path(w, "/api/v1/providers/{id}/{version}/history", "Abonnenten-Historie", false);
        path(w, "/api/v1/providers/{id}/{version}/trades", "Trading History als JSON-Zeilen", false);
        path(w, "/api/v1/providers/{id}/{version}/trades.csv", "Trading History als rohe CSV-Datei", false);
        path(w, "/api/v1/providers/{id}/{version}/metrics", "Kennzahlen aus der konvertierten Root-Seite", false);
        path(w, "/api/v1/providers/{id}/{version}/reports", "Liste der Testreport-PDFs", false);
        path(w, "/api/v1/providers/{id}/{version}/reports/{name}", "Testreport-PDF herunterladen", false);
        path(w, "/api/v1/trades", "Katalog aller Trading-History-CSVs", false);
        path(w, "/api/v1/trades/file/{version}/{name}", "Trading-History-CSV per Dateiname herunterladen", false);
        path(w, "/api/v1/events", "Neueste Abonnenten-Änderungen über alle Signale", false);
        path(w, "/api/v1/summary", "Aggregierte Kennzahlen über den Datenbestand", false);

        w.endObject() // paths
            .name("components").beginObject()
                .name("securitySchemes").beginObject()
                    .name("ApiKeyToken").beginObject()
                        .name("type").value("apiKey")
                        .name("in").value("header")
                        .name("name").value("X-API-Token")
                    .endObject()
                .endObject()
            .endObject()
        .endObject();
        return w.toString();
    }

    private static void path(JsonWriter w, String path, String summary, boolean noParams) {
        w.name(path).beginObject()
            .name("get").beginObject()
                .name("summary").value(summary)
                .name("responses").beginObject()
                    .name("200").beginObject()
                        .name("description").value("OK")
                    .endObject()
                    .name("401").beginObject()
                        .name("description").value("Token fehlt oder ist falsch")
                    .endObject()
                    .name("404").beginObject()
                        .name("description").value("Nicht gefunden")
                    .endObject()
                .endObject()
            .endObject()
        .endObject();
    }
}
