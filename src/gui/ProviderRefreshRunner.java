package gui;

import database.SubscriberStat;
import downloader.DownloadFiles;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import rest.ProviderFiles;
import rest.UpdateJob;

/** Ein gezielter Plattformabruf, niemals Discovery oder Abonnenten-Publikation. */
final class ProviderRefreshRunner implements Runnable {
    interface Services {
        List<SubscriberStat> providers();
        void download(String version, List<SubscriberStat> exactScope) throws Exception;
        void convert(String version, String providerId) throws Exception;
        void verify(String version, String providerId, long startedAt) throws Exception;
        int newTrades();
        int updatedTrades();
        int failed();
    }

    private final UpdateJob job;
    private final Services services;
    private final String id;
    private final String version;

    ProviderRefreshRunner(UpdateJob job, Services services, long id, String version) {
        if (id <= 0 || !("mql4".equals(version) || "mql5".equals(version)))
            throw new IllegalArgumentException("Anbieter-ID/Plattform ungueltig");
        this.job = job;
        this.services = services;
        this.id = Long.toString(id);
        this.version = version;
    }

    private SubscriberStat provider() {
        for (SubscriberStat p : services.providers()) {
            if (id.equals(p.getSignalId()) && version.equals(ProviderFiles.normalizeVersionFolder(p.getMqlVersion())))
                return p;
        }
        // Kein fehlender Katalogeintrag darf einen Vollkataloglauf auslösen.
        return new SubscriberStat(id, version, id, 0, 0, null, null, null, null);
    }

    @Override public void run() {
        long startedAt = System.currentTimeMillis();
        job.ergebnis("providerId", id);
        job.ergebnis("version", version);
        job.ergebnis("platformRefreshed", false);
        job.ergebnis("fullHistory", false);
        try {
            job.melde("provider", 0, 1, "Anbieter " + id + " mit vollstaendigem Export frisch laden");
            services.download(version.toUpperCase(java.util.Locale.ROOT), Collections.singletonList(provider()));
            if (services.failed() != 0 || services.newTrades() + services.updatedTrades() != 1)
                throw new IOException("Kein erfolgreicher neuer Einzelanbieter-Tradeexport belegt");
            job.melde("konvertieren", 0, 1, "Aktuelle Kennzahlen dieses Anbieters konvertieren");
            services.convert(version, id);
            services.verify(version, id, startedAt);
            job.ergebnis("signaleGeliefert", 1);
            job.ergebnis("tradelistenNeu", services.newTrades());
            job.ergebnis("tradelistenAktualisiert", services.updatedTrades());
            job.ergebnis("platformRefreshed", true);
            job.ergebnis("fullHistory", true);
            String timestamp = Instant.now().toString();
            job.ergebnis("refreshedAt", timestamp);
            job.melde("fertig", 1, 1, "Einzelanbieter frisch geladen und konvertiert");
            job.fertig(timestamp);
        } catch (Exception error) {
            job.ergebnis("tradelistenFehler", Math.max(1, services.failed()));
            job.ergebnis("tradelistenAbgebrochen", true);
            job.fehler(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    /** Gleicher ID-/Versionsdateisatz, nicht ein beliebiger alter Root-TXT-Fallback. */
    static void verifyArtifacts(File downloadDir, String id, String version, long startedAt) throws IOException {
        File csv = ProviderFiles.findTradeCsv(new File(downloadDir, version), id);
        if (csv == null || !DownloadFiles.validHeader(csv, version) || csv.lastModified() < startedAt)
            throw new IOException("Frischer passender Tradeexport fehlt");
        String stem = csv.getName().substring(0, csv.getName().length() - 4);
        File html = new File(csv.getParentFile(), stem + "_root.html");
        File metrics = new File(csv.getParentFile(), stem + "_root.txt");
        if (!html.isFile() || !metrics.isFile() || html.lastModified() < startedAt
                || metrics.lastModified() < startedAt || metrics.length() == 0)
            throw new IOException("Aktueller passender Profil-/Kennzahlendateisatz fehlt");
        if (!ProviderFiles.htmlIdentity(org.jsoup.Jsoup.parse(html, null), id, version))
            throw new IOException("Profilidentitaet oder MetaTrader-Version nicht belegt");
    }
}
