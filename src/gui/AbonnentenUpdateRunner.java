package gui;

import database.SubscriberStat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import rest.UpdateJob;

/** Separate table-only acquisition; both platform lists must succeed before publication. */
final class AbonnentenUpdateRunner implements Runnable {
    interface Services {
        List<SubscriberStat> collect(String version, int target) throws Exception;
        void publish(List<SubscriberStat> providers) throws Exception;
    }
    private final UpdateJob job;
    private final Services services;
    private final Path fullCatalogueMarker;
    private final int target;

    AbonnentenUpdateRunner(UpdateJob job, Services services, Path marker, int target) {
        if(target<1 || target>200) throw new IllegalArgumentException("target muss zwischen 1 und 200 liegen");
        this.job=job;this.services=services;this.fullCatalogueMarker=marker;this.target=target;
    }
    static List<SubscriberStat> top(List<SubscriberStat> candidates,int target) {
        List<SubscriberStat> sorted=new ArrayList<>(candidates);
        Set<String> identities=new HashSet<>();
        for(SubscriberStat p:sorted) {
            if(p.getSubscribers()<1 || p.getSignalId()==null || !p.getSignalId().matches("[1-9][0-9]*")
                    || !("mql4".equals(p.getMqlVersion()) || "mql5".equals(p.getMqlVersion()))
                    || !identities.add(p.getMqlVersion()+":"+p.getSignalId()))
                throw new IllegalArgumentException("Unverifizierte oder doppelte Listenidentitaet");
        }
        sorted.sort(Comparator.comparingInt(SubscriberStat::getSubscribers).reversed()
            .thenComparing(SubscriberStat::getMqlVersion).thenComparing(SubscriberStat::getSignalId));
        return new ArrayList<>(sorted.subList(0,Math.min(target,sorted.size())));
    }
    @Override public void run() {
        job.ergebnis("nurAbonnenten",true);
        job.ergebnis("katalogUebersprungen",false);
        job.ergebnis("tradelistenNeu",0);job.ergebnis("tradelistenAktualisiert",0);
        try {
            List<SubscriberStat> candidates=new ArrayList<>();
            for(String version:new String[]{"MQL4","MQL5"}) {
                job.melde("abonnenten",0,target,version+": Abonnentenliste absteigend lesen (ohne Profile) …");
                candidates.addAll(services.collect(version,target));
            }
            List<SubscriberStat> selected=top(candidates,target);
            // A subset observation proves no full-profile/catalogue freshness.
            Files.deleteIfExists(fullCatalogueMarker);
            services.publish(selected);
            job.ergebnis("signaleGeliefert",selected.size());
            job.hinweis("Nur Abonnenten: insgesamt hoechstens "+target+" Anbieter aus MT4 und MT5, mindestens 1 Abonnent; keine Profile/Tradelisten neu geladen.");
            job.melde("abonnenten",selected.size(),selected.size(),"Top-Abonnentenkatalog atomar uebernommen");
            job.fertig(LocalDateTime.now().toString());
        } catch(Exception error) { job.fehler(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()); }
    }
}
