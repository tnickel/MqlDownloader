package rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderFilesTest {
    private static final String MT5_HEADER = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n";
    private static final String MT4_HEADER = "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment\n";

    @TempDir
    Path tempDir;

    private Path archive(String directory, String name, String html, String header) throws IOException {
        Path dir = tempDir.resolve(directory);
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".csv"), header.getBytes("UTF-8"));
        Files.write(dir.resolve(name + "_root.html"), html.getBytes("UTF-8"));
        Files.write(dir.resolve(name + "_root.txt"), "Balance=100".getBytes("UTF-8"));
        return dir.resolve(name + ".csv");
    }

    @Test
    void archiveRecoveryUsesExactIdAndSameElementPlatformEvidenceForDifferentSignals() throws IOException {
        Path first = archive("mql4mql5_", "Make_You_Rich_2349577",
                "<title>MetaTrader 5</title><div data-id='2349577' data-mt='5'></div>", MT5_HEADER);
        Path second = archive("mql45", "You_Only_Live_Once_2361280",
                "<button data-mt='5' data-id='2361280'></button>", MT5_HEADER);
        assertEquals(first.toFile(), ProviderFiles.resolveTradeCsv(tempDir.toFile(), "2349577", "mt5"));
        assertEquals(second.toFile(), ProviderFiles.resolveTradeCsv(tempDir.toFile(), "2361280", "mql5"));
        assertEquals("Make_You_Rich_2349577_root.txt",
                ProviderFiles.resolveRootTxt(tempDir.toFile(), "2349577", "mql5").getName());
        assertNull(ProviderFiles.resolveTradeCsv(tempDir.toFile(), "2349577", "mql4"));
    }

    @Test
    void archiveRejectsWrongIdentitySplitTagsContradictoryPlatformAndFormat() throws IOException {
        String[] invalid = {"<div data-id='999' data-mt='5'></div>",
                "<div data-id='123'></div><div data-mt='5'></div>",
                "<div data-id='123' data-mt='4'></div>",
                "<div data-id='123' data-mt='5'></div><div data-id='123' data-mt='4'></div>",
                "<title>MetaTrader 4</title><div data-id='123' data-mt='5'></div>",
                "<link rel='canonical' href='https://www.mql5.com/en/signals/999'><div data-id='123' data-mt='5'></div>"};
        for (String html : invalid) {
            archive("archive", "Strategy_123", html, MT5_HEADER);
            assertNull(ProviderFiles.resolveTradeCsv(tempDir.toFile(), "123", "mql5"));
            assertNull(ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
        }
        archive("archive", "Strategy_123", "<div data-id='123' data-mt='5'></div>", MT4_HEADER);
        assertNull(ProviderFiles.resolveTradeCsv(tempDir.toFile(), "123", "mql5"));
    }

    @Test
    void archiveAmbiguityIsNotResolvedByModificationTime() throws IOException {
        archive("old", "P_123", "<div data-id='123' data-mt='5'></div>", MT5_HEADER);
        archive("new", "P_123", "<div data-id='123' data-mt='5'></div>", MT5_HEADER);
        assertNull(ProviderFiles.resolveTradeCsv(tempDir.toFile(), "123", "mql5"));
        assertNull(ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
    }

    @Test
    void normalCacheWinsAndMissingNormalMetricsDoNotMixWithArchive() throws IOException {
        archive("archive", "P_123", "<div data-id='123' data-mt='5'></div>", MT5_HEADER);
        Path normal = tempDir.resolve("mql5");Files.createDirectories(normal);
        Path csv = normal.resolve("Normal_123.csv");Files.write(csv, new byte[]{1});
        assertEquals(csv.toFile(), ProviderFiles.resolveTradeCsv(tempDir.toFile(), "123", "mql5"));
        assertNull(ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
        Path txt = normal.resolve("Normal_123_root.txt");Files.write(txt, new byte[]{1});
        assertEquals(txt.toFile(), ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
    }

    @Test
    void normalMetricsAloneDoNotOverrideMetricsOfSelectedArchiveTrades() throws IOException {
        Path archived = archive("archive", "P_123", "<div data-id='123' data-mt='5'></div>", MT5_HEADER);
        Path normal = tempDir.resolve("mql5");Files.createDirectories(normal);
        Files.write(normal.resolve("Other_123_root.txt"), "Balance=999".getBytes("UTF-8"));
        assertEquals(archived.toFile(), ProviderFiles.resolveTradeCsv(tempDir.toFile(), "123", "mql5"));
        assertEquals(archived.resolveSibling("P_123_root.txt").toFile(),
                ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
        Files.delete(archived.resolveSibling("P_123_root.txt"));
        assertNull(ProviderFiles.resolveRootTxt(tempDir.toFile(), "123", "mql5"));
    }

    @Test
    void mt4ArchiveRequiresMt4EvidenceAndOrderbookFormat() throws IOException {
        Path csv = archive("archive", "P_55", "<div data-id='55' data-mt='4'></div>", MT4_HEADER);
        assertEquals(csv.toFile(), ProviderFiles.resolveTradeCsv(tempDir.toFile(), "55", "mt4"));
        assertNull(ProviderFiles.resolveTradeCsv(tempDir.toFile(), "55", "mt5"));
    }

    @Test
    void findsTradeCsvBySignalId() throws IOException {
        Path mql5 = tempDir.resolve("mql5");
        Files.createDirectories(mql5);
        Files.write(mql5.resolve("My_Provider_123.csv"), "a;b\n1;2".getBytes("UTF-8"));
        Files.write(mql5.resolve("Other_999.csv"), "a;b\n1;2".getBytes("UTF-8"));

        File found = ProviderFiles.findTradeCsv(mql5.toFile(), "123");
        assertEquals("My_Provider_123.csv", found.getName());
    }

    @Test
    void signalIdMatchesOnlyOnDigitBoundary() throws IOException {
        Path mql5 = tempDir.resolve("mql5");
        Files.createDirectories(mql5);
        Files.write(mql5.resolve("Provider_1234.csv"), "a".getBytes("UTF-8"));

        assertNull(ProviderFiles.findTradeCsv(mql5.toFile(), "123"));
        assertEquals("Provider_1234.csv",
                ProviderFiles.findTradeCsv(mql5.toFile(), "1234").getName());
    }

    @Test
    void numbersInProviderNameCannotSelectAnotherSignalsTradesOrMetrics() throws IOException {
        Files.write(tempDir.resolve("Anson_Account_1_2381807.csv"), new byte[]{1});
        Files.write(tempDir.resolve("Anson_Account_1_2381807_root.txt"), new byte[]{1});
        Files.write(tempDir.resolve("Strategy_55_999.csv"), new byte[]{1});
        Files.write(tempDir.resolve("Strategy_55_999_root.txt"), new byte[]{1});
        assertNull(ProviderFiles.findTradeCsv(tempDir.toFile(), "1"));
        assertNull(ProviderFiles.findRootTxt(tempDir.toFile(), "1"));
        assertNull(ProviderFiles.findTradeCsv(tempDir.toFile(), "55"));
        assertNull(ProviderFiles.findRootTxt(tempDir.toFile(), "55"));
        assertEquals("Anson_Account_1_2381807.csv",
                ProviderFiles.findTradeCsv(tempDir.toFile(), "2381807").getName());
        assertEquals("Strategy_55_999_root.txt",
                ProviderFiles.findRootTxt(tempDir.toFile(), "999").getName());
    }

    @Test
    void picksNewestTradeCsvWhenMultipleMatch() throws IOException, InterruptedException {
        Path mql5 = tempDir.resolve("mql5");
        Files.createDirectories(mql5);
        File older = mql5.resolve("P_123.csv").toFile();
        assertTrue(older.createNewFile());
        Thread.sleep(50);
        File newer = mql5.resolve("P_neu_123.csv").toFile();
        assertTrue(newer.createNewFile());
        newer.setLastModified(System.currentTimeMillis() + 1000);

        assertEquals("P_neu_123.csv", ProviderFiles.findTradeCsv(mql5.toFile(), "123").getName());
    }

    @Test
    void findsRootTxt() throws IOException {
        Path mql4 = tempDir.resolve("mql4");
        Files.createDirectories(mql4);
        Files.write(mql4.resolve("P_55_root.txt"), "Balance=1".getBytes("UTF-8"));
        Files.write(mql4.resolve("P_55_root.html"), "<html>".getBytes("UTF-8"));

        assertEquals("P_55_root.txt",
                ProviderFiles.findRootTxt(mql4.toFile(), "55").getName());
    }

    @Test
    void findsReportsByIdPatternSorted() throws IOException {
        Path analyse = tempDir.resolve("analyse");
        Files.createDirectories(analyse);
        Files.write(analyse.resolve("report_123_a.pdf"), new byte[]{1});
        Files.write(analyse.resolve("report_123_b.pdf"), new byte[]{2});
        Files.write(analyse.resolve("report_999.pdf"), new byte[]{3});
        Files.write(analyse.resolve("notes.txt"), new byte[]{4});

        List<File> reports = ProviderFiles.findReports(analyse.toString(), "123");
        assertEquals(2, reports.size());
        assertEquals("report_123_a.pdf", reports.get(0).getName());
        assertEquals("report_123_b.pdf", reports.get(1).getName());
    }

    @Test
    void extractsSignalIdFromFileName() {
        assertEquals("123", ProviderFiles.extractSignalId("My_Provider_123.csv"));
        assertEquals("123", ProviderFiles.extractSignalId("My_Provider_123_root.txt"));
        assertEquals("123", ProviderFiles.extractSignalId("My_Provider_123_ROOT.TXT"));
        assertNull(ProviderFiles.extractSignalId("no-id-here.csv"));
        assertNull(ProviderFiles.extractSignalId("trailing_underscore_.csv"));
        assertNull(ProviderFiles.extractSignalId(null));
    }

    @Test
    void findByNameRejectsTraversalAndUnknownNames() {
        File dir = tempDir.toFile();
        List<File> files = new java.util.ArrayList<>();
        File real = new File(dir, "a.pdf");
        files.add(real);

        assertEquals(real, ProviderFiles.findByName(files, "a.pdf"));
        assertNull(ProviderFiles.findByName(files, "..\\a.pdf"));
        assertNull(ProviderFiles.findByName(files, "../a.pdf"));
        assertNull(ProviderFiles.findByName(files, "b.pdf"));
        assertNull(ProviderFiles.findByName(files, null));
    }

    @Test
    void normalizesVersionFolder() {
        assertEquals("mql4", ProviderFiles.normalizeVersionFolder("mt4"));
        assertEquals("mql4", ProviderFiles.normalizeVersionFolder("MQL4"));
        assertEquals("mql5", ProviderFiles.normalizeVersionFolder("mt5"));
        assertEquals("mql5", ProviderFiles.normalizeVersionFolder("mql5"));
        assertNull(ProviderFiles.normalizeVersionFolder("mql7"));
        assertNull(ProviderFiles.normalizeVersionFolder(null));
    }

    @Test
    void directChildCheckBlocksNestedPaths() throws IOException {
        Path base = tempDir.resolve("base");
        Files.createDirectories(base);
        Path nested = base.resolve("sub");
        Files.createDirectories(nested);
        Files.write(nested.resolve("x.csv"), new byte[]{1});
        Files.write(base.resolve("ok.csv"), new byte[]{1});

        assertTrue(ProviderFiles.isDirectChild(base.toFile(), base.resolve("ok.csv").toFile()));
        assertFalse(ProviderFiles.isDirectChild(base.toFile(), nested.resolve("x.csv").toFile()));
    }
}
