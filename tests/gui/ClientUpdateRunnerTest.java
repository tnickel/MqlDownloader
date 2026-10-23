package gui;

import database.SubscriberStat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rest.UpdateJob;
import static org.junit.jupiter.api.Assertions.*;

class ClientUpdateRunnerTest {
    @TempDir Path dir;

    private SubscriberStat provider(String id, String version, int subscribers) {
        return new SubscriberStat(id, version, "Synthetic " + id, subscribers, 0, null, null, null, null);
    }

    private final class FakeServices implements ClientUpdateRunner.Services {
        List<SubscriberStat> providers = Arrays.asList(provider("11", "mql4", 3), provider("22", "mql5", 5));
        List<String> calls = new ArrayList<>();
        List<List<SubscriberStat>> scopes = new ArrayList<>();
        int delivered, updated, created, failed;
        boolean downloadFailure, conversionFailure;
        @Override public List<SubscriberStat> providers() { return providers; }
        @Override public void download(String version, List<SubscriberStat> scope, boolean trades) throws Exception {
            calls.add(version + ":" + trades);
            scopes.add(scope);
            delivered++;
            if (trades) updated++;
            if (downloadFailure) { failed = 2; throw new IOException("synthetic partial failure / stop"); }
        }
        @Override public void convert() throws Exception {
            calls.add("convert");
            if (conversionFailure) throw new IOException("synthetic conversion failure");
        }
        @Override public int delivered() { return delivered; }
        @Override public int newTrades() { return created; }
        @Override public int updatedTrades() { return updated; }
        @Override public int failed() { return failed; }
    }

    private Path marker(int ageHours) throws Exception {
        Path path = dir.resolve("update_state.json");
        Files.writeString(path, "katalogLetzterLoad=" + Instant.now().minusSeconds(ageHours * 3600L) + "\n");
        return path;
    }

    @SuppressWarnings("unchecked") private Map<String, Object> result(UpdateJob job) {
        return (Map<String, Object>) job.statusJson().get("ergebnis");
    }

    @Test void freshCatalogueStillRefreshesEveryKnownVersionWithoutAdvancingCatalogueStamp() throws Exception {
        FakeServices services = new FakeServices();
        Path marker = marker(1); String before = Files.readString(marker);
        UpdateJob job = new UpdateJob("fresh-trades");
        new ClientUpdateRunner(job, services, marker, 72, true).run();
        assertEquals(Arrays.asList("MQL4:true", "MQL5:true", "convert"), services.calls);
        assertEquals("11", services.scopes.get(0).get(0).getSignalId());
        assertEquals("22", services.scopes.get(1).get(0).getSignalId());
        assertEquals(before, Files.readString(marker));
        assertEquals(UpdateJob.State.DONE, job.state());
        assertEquals(true, result(job).get("katalogUebersprungen"));
        assertEquals(2, result(job).get("tradelistenAktualisiert"));
    }

    @Test void staleCatalogueDiscoversBothVersionsAndRefreshesTrades() throws Exception {
        FakeServices services = new FakeServices(); Path marker = marker(100);
        String before = Files.readString(marker); UpdateJob job = new UpdateJob("stale");
        new ClientUpdateRunner(job, services, marker, 72, true).run();
        assertEquals(Arrays.asList("MQL4:true", "MQL5:true", "convert"), services.calls);
        assertTrue(services.scopes.stream().allMatch(scope -> scope == null));
        assertNotEquals(before, Files.readString(marker));
        assertEquals(false, result(job).get("katalogUebersprungen"));
    }

    @Test void freshCatalogueWithoutTradesDoesNotDownloadOrConvert() throws Exception {
        FakeServices services = new FakeServices(); Path marker = marker(1);
        String before = Files.readString(marker); UpdateJob job = new UpdateJob("catalogue-only");
        new ClientUpdateRunner(job, services, marker, 72, false).run();
        assertTrue(services.calls.isEmpty()); assertEquals(before, Files.readString(marker));
        assertEquals(UpdateJob.State.DONE, job.state());
        assertEquals(0, result(job).get("tradelistenAktualisiert"));
        assertEquals(2, result(job).get("signaleGeliefert"));
    }

    @Test void staleCatalogueWithoutTradesLoadsOnlyMetadata() throws Exception {
        FakeServices services = new FakeServices(); UpdateJob job = new UpdateJob("old-catalogue-only");
        new ClientUpdateRunner(job, services, marker(100), 72, false).run();
        assertEquals(Arrays.asList("MQL4:false", "MQL5:false", "convert"), services.calls);
        assertEquals(0, result(job).get("tradelistenAktualisiert"));
    }

    @Test void failedKnownRefreshKeepsPartialCountsAndDoesNotMarkCatalogueFreshAgain() throws Exception {
        FakeServices services = new FakeServices(); services.downloadFailure = true;
        Path marker = marker(1); String before = Files.readString(marker); UpdateJob job = new UpdateJob("failed");
        new ClientUpdateRunner(job, services, marker, 72, true).run();
        assertEquals(Arrays.asList("MQL4:true"), services.calls);
        assertEquals(UpdateJob.State.ERROR, job.state());
        assertEquals(1, result(job).get("tradelistenAktualisiert"));
        assertEquals(2, result(job).get("tradelistenFehler"));
        assertEquals(true, result(job).get("tradelistenAbgebrochen"));
        assertEquals(before, Files.readString(marker));
    }

    @Test void conversionFailurePreservesOldStampAndActualTradeCounters() throws Exception {
        FakeServices services = new FakeServices(); services.conversionFailure = true;
        Path marker = marker(100); String before = Files.readString(marker); UpdateJob job = new UpdateJob("convert-failed");
        new ClientUpdateRunner(job, services, marker, 72, true).run();
        assertEquals(UpdateJob.State.ERROR, job.state()); assertEquals(before, Files.readString(marker));
        assertEquals(2, result(job).get("tradelistenAktualisiert"));
        assertEquals(0, result(job).get("tradelistenFehler"));
        assertEquals(false, result(job).get("tradelistenAbgebrochen"));
    }

    @Test void emptyCachedScopeCannotSkipDiscoveryAndInvalidIdentitiesNeverReachKnownRefresh() throws Exception {
        FakeServices services = new FakeServices();
        services.providers = Arrays.asList(provider("test_1", "mql4", 5), provider("12", "mql5", 0));
        UpdateJob job = new UpdateJob("empty-cache");
        new ClientUpdateRunner(job, services, marker(1), 72, true).run();
        assertTrue(services.scopes.stream().allMatch(scope -> scope == null));
        assertEquals(false, result(job).get("katalogUebersprungen"));
    }

    @Test void freshSingleVersionDoesNotStartEmptyVersionOrCropKnownScopeToDiscoveryLimit() throws Exception {
        FakeServices services = new FakeServices();
        services.providers = Arrays.asList(provider("11", "mql4", 3), provider("13", "mql4", 9),
                provider("test_1", "mql4", 8), provider("22", "mql4", 0));
        new ClientUpdateRunner(new UpdateJob("single-version"), services, marker(1), 72, true).run();
        assertEquals(Arrays.asList("MQL4:true", "convert"), services.calls);
        assertEquals(2, services.scopes.get(0).size());
        assertEquals("13", services.scopes.get(0).get(0).getSignalId());
    }
}
