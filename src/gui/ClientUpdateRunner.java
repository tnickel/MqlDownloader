package gui;

import database.SubscriberStat;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rest.ProviderFiles;
import rest.UpdateJob;

/** Catalogue freshness controls discovery only; requested trades are always refreshed. */
final class ClientUpdateRunner implements Runnable {
    interface Services {
        List<SubscriberStat> providers();
        void download(String version, List<SubscriberStat> knownProviders, boolean trades) throws Exception;
        void convert() throws Exception;
        int delivered();
        int newTrades();
        int updatedTrades();
        int failed();
    }

    private final UpdateJob job;
    private final Services services;
    private final Path marker;
    private final int maxAgeHours;
    private final boolean trades;

    ClientUpdateRunner(UpdateJob job, Services services, Path marker, int maxAgeHours, boolean trades) {
        this.job = job;
        this.services = services;
        this.marker = marker;
        this.maxAgeHours = maxAgeHours;
        this.trades = trades;
    }

    static List<SubscriberStat> eligible(List<SubscriberStat> providers, String version) {
        List<SubscriberStat> result = new ArrayList<>();
        for (SubscriberStat provider : providers) {
            if (provider.getSignalId() != null && provider.getSignalId().matches("[1-9][0-9]*")
                    && provider.getSubscribers() > 0
                    && ProviderFiles.normalizeVersionFolder(version).equals(
                            ProviderFiles.normalizeVersionFolder(provider.getMqlVersion()))) result.add(provider);
        }
        result.sort(Comparator.comparingInt(SubscriberStat::getSubscribers).reversed()
                .thenComparing(SubscriberStat::getSignalId));
        return result;
    }

    private void counters() {
        job.ergebnis("tradelistenNeu", services.newTrades());
        job.ergebnis("tradelistenAktualisiert", services.updatedTrades());
        job.ergebnis("tradelistenFehler", services.failed());
    }

    @Override public void run() {
        boolean cached = false;
        int knownCount = 0;
        boolean tradePhaseActive = false;
        try {
            List<SubscriberStat> known = services.providers();
            List<SubscriberStat> mt4 = eligible(known, "mql4");
            List<SubscriberStat> mt5 = eligible(known, "mql5");
            knownCount = mt4.size() + mt5.size();
            String lastLoad = MqlDownloaderGui.leseKatalogVermerk(marker);
            cached = knownCount > 0 && lastLoad != null
                    && MqlDownloaderGui.katalogFrischGenug(lastLoad, maxAgeHours);
            job.ergebnis("katalogUebersprungen", cached);
            if (cached && !trades) {
                counters();
                job.ergebnis("signaleGeliefert", knownCount);
                job.fertig(lastLoad);
                return;
            }
            for (String version : new String[]{"MQL4", "MQL5"}) {
                List<SubscriberStat> scope = "MQL4".equals(version) ? mt4 : mt5;
                if (cached && scope.isEmpty()) continue;
                job.melde(cached ? "tradelisten" : version.toLowerCase(), 0,
                        cached ? scope.size() : 0, cached
                                ? "Bekannte " + version + "-Historien aktualisieren …"
                                : version + "-Katalog laden …");
                tradePhaseActive = trades;
                services.download(version, cached ? scope : null, trades);
                tradePhaseActive = false;
                counters();
                job.ergebnis("signaleGeliefert", cached ? knownCount : services.delivered());
            }
            job.melde("konvertieren", 0, 0, "Kennzahlen konvertieren …");
            services.convert();
            if (!cached) MqlDownloaderGui.schreibeKatalogVermerk(marker);
            counters();
            job.ergebnis("signaleGeliefert", cached ? knownCount : services.delivered());
            job.fertig(LocalDateTime.now().toString());
        } catch (Exception error) {
            counters();
            job.ergebnis("signaleGeliefert", services.delivered());
            job.ergebnis("tradelistenAbgebrochen", tradePhaseActive);
            job.fehler(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
