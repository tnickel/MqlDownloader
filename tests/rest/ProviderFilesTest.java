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

    @TempDir
    Path tempDir;

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
