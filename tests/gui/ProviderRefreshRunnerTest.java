package gui;

import database.SubscriberStat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rest.UpdateJob;
import static org.junit.jupiter.api.Assertions.*;

class ProviderRefreshRunnerTest {
    @TempDir Path dir;

    private final class Services implements ProviderRefreshRunner.Services {
        List<String> calls = new ArrayList<>();
        List<SubscriberStat> selected;
        int updated = 1, failures = 0;
        boolean downloadError, convertError, verifyError;
        @Override public List<SubscriberStat> providers() {
            return Arrays.asList(provider("42", "mql4"), provider("42", "mql5"), provider("99", "mql5"));
        }
        @Override public void download(String version, List<SubscriberStat> exact) throws Exception {
            calls.add("download:" + version); selected = exact;
            if (downloadError) throw new IOException("Login/Export gescheitert");
        }
        @Override public void convert(String version, String id) throws Exception {
            calls.add("convert:" + version + ":" + id);
            if (convertError) throw new IOException("Konvertierung gescheitert");
        }
        @Override public void verify(String version, String id, long start) throws Exception {
            calls.add("verify:" + version + ":" + id);
            if (verifyError) throw new IOException("Kennzahlen alt");
        }
        @Override public int newTrades() { return 0; }
        @Override public int updatedTrades() { return updated; }
        @Override public int failed() { return failures; }
    }

    private SubscriberStat provider(String id, String version) {
        return new SubscriberStat(id, version, "Synthetic " + id, 9, 0, null, null, null, null);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> result(UpdateJob job) {
        return (Map<String,Object>) job.statusJson().get("ergebnis");
    }

    @Test void refreshesExactlyOneVersionAndPublishesProofOnlyAfterVerifiedConversion() {
        for (String version : Arrays.asList("mql4", "mql5")) {
            Services services = new Services(); UpdateJob job = new UpdateJob(version);
            new ProviderRefreshRunner(job, services, 42, version).run();
            assertEquals(UpdateJob.State.DONE, job.state());
            assertEquals(1, services.selected.size());
            assertEquals("42", services.selected.get(0).getSignalId());
            assertEquals(version, services.selected.get(0).getMqlVersion());
            assertEquals(Arrays.asList("download:" + version.toUpperCase(),
                    "convert:" + version + ":42", "verify:" + version + ":42"), services.calls);
            assertEquals("42", result(job).get("providerId"));
            assertEquals(version, result(job).get("version"));
            assertEquals(true, result(job).get("platformRefreshed"));
            assertEquals(true, result(job).get("fullHistory"));
            assertNotNull(Instant.parse((String) result(job).get("refreshedAt")));
        }
    }

    @Test void unknownCatalogueIdentityStillUsesExactlyOneTargetAndNeverDiscovery() {
        Services services = new Services(); UpdateJob job = new UpdateJob("direct");
        new ProviderRefreshRunner(job, services, 777, "mql5").run();
        assertEquals(UpdateJob.State.DONE, job.state());
        assertEquals(1, services.selected.size());
        assertEquals("777", services.selected.get(0).getSignalId());
    }

    @Test void missingNativeSuccessCounterCannotBecomeFreshHistoryProof() {
        Services services = new Services(); services.updated = 0;
        UpdateJob job = new UpdateJob("none"); new ProviderRefreshRunner(job, services, 42, "mql5").run();
        assertEquals(UpdateJob.State.ERROR, job.state());
        assertEquals(Arrays.asList("download:MQL5"), services.calls);
        assertEquals(false, result(job).get("platformRefreshed"));
        assertEquals(false, result(job).get("fullHistory"));
        assertNull(result(job).get("refreshedAt"));
    }

    @Test void downloadConversionAndVerificationErrorsKeepSuccessFlagsFalse() {
        for (int error = 0; error < 3; error++) {
            Services services = new Services();
            services.downloadError = error == 0; services.convertError = error == 1; services.verifyError = error == 2;
            UpdateJob job = new UpdateJob("error" + error);
            new ProviderRefreshRunner(job, services, 42, "mql5").run();
            assertEquals(UpdateJob.State.ERROR, job.state());
            assertEquals(false, result(job).get("platformRefreshed"));
            assertEquals(false, result(job).get("fullHistory"));
            assertNull(result(job).get("refreshedAt"));
        }
    }

    @Test void artifactCheckRequiresFreshExactCsvHtmlAndConvertedMetrics() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("mql5"));
        Path csv = folder.resolve("Synthetic_42.csv"), html = folder.resolve("Synthetic_42_root.html");
        Path txt = folder.resolve("Synthetic_42_root.txt");
        long start = System.currentTimeMillis() - 100;
        Files.writeString(csv, "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n");
        Files.writeString(html, "<div data-id='42' data-mt='5'></div>"); Files.writeString(txt, "Currency=USD\n");
        ProviderRefreshRunner.verifyArtifacts(dir.toFile(), "42", "mql5", start);
        Files.writeString(html, "<div data-id='42' data-mt='4'></div>");
        assertThrows(IOException.class, () -> ProviderRefreshRunner.verifyArtifacts(dir.toFile(), "42", "mql5", start));
        Files.writeString(html, "<div data-id='42' data-mt='5'></div>");
        assertThrows(IOException.class, () -> ProviderRefreshRunner.verifyArtifacts(dir.toFile(), "42", "mql4", start));
        Files.setLastModifiedTime(txt, FileTime.fromMillis(start - 100));
        assertThrows(IOException.class, () -> ProviderRefreshRunner.verifyArtifacts(dir.toFile(), "42", "mql5", start));
        Files.setLastModifiedTime(txt, FileTime.fromMillis(start + 10));
        Files.writeString(csv, "login html");
        assertThrows(IOException.class, () -> ProviderRefreshRunner.verifyArtifacts(dir.toFile(), "42", "mql5", start));
    }

    @Test void targetedConversionPreservesLowOrMissingPerformanceAndLeavesOtherProvidersUntouched() throws Exception {
        config.ConfigurationManager config = new config.ConfigurationManager(dir.toString());
        config.initializeDirectories(); config.setSubscribersOnly(false);
        Path folder = Files.createDirectories(dir.resolve("download/mql5"));
        String header = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n";
        Path csv = folder.resolve("Synthetic_42.csv"), html = folder.resolve("Synthetic_42_root.html");
        Files.writeString(csv, header);
        Files.writeString(html, "<div class='s-list-info__item'><div class='s-list-info__label'>Balance:</div>"
                + "<div class='s-list-info__value'>123.45 USD</div></div>Maximaler R\u00fcckgang: 5%"
                + "<div data-id='42' data-mt='5'></div>");
        Path other = folder.resolve("Other_99_root.html"); Files.writeString(other, "other untouched");
        new converter.HtmlConverter(dir.resolve("download").toString(), config).convertProvider("mql5", "42");
        assertTrue(Files.isRegularFile(csv)); assertTrue(Files.isRegularFile(html));
        assertTrue(Files.isRegularFile(folder.resolve("Synthetic_42_root.txt")));
        assertEquals("other untouched", Files.readString(other));
        assertFalse(Files.exists(folder.resolve("Other_99_root.txt")));
    }
}
