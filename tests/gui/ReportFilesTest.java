package gui;

import database.SubscriberStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportFilesTest {
    @TempDir
    Path directory;

    @Test
    void matchesOnlyTheCompleteDigitGroup() {
        assertTrue(ReportFiles.matchesSignalId("23792.pdf", "23792"));
        assertTrue(ReportFiles.matchesSignalId("Report_23792_2026.pdf", " 23792 "));
        assertTrue(ReportFiles.matchesSignalId("abc23792def.pdf", "23792"));
        assertFalse(ReportFiles.matchesSignalId("2379208.pdf", "23792"));
        assertFalse(ReportFiles.matchesSignalId("123792.pdf", "23792"));
        assertFalse(ReportFiles.matchesSignalId("report.pdf", "23792"));
    }

    @Test
    void handlesMissingIdsAndTreatsRegexCharactersLiterally() {
        assertFalse(ReportFiles.matchesSignalId(null, "23792"));
        assertFalse(ReportFiles.matchesSignalId("23792.pdf", null));
        assertFalse(ReportFiles.matchesSignalId("23792.pdf", " "));
        assertTrue(ReportFiles.matchesSignalId("id-a.b.pdf", "a.b"));
        assertFalse(ReportFiles.matchesSignalId("id-axb.pdf", "a.b"));
    }

    @Test
    void discoversSortedPdfFilesAndPreservesStatPositions() throws Exception {
        Path second = Files.createFile(directory.resolve("z_23792.PDF"));
        Path first = Files.createFile(directory.resolve("a_23792.pdf"));
        Path other = Files.createFile(directory.resolve("2379208.pdf"));
        Files.createFile(directory.resolve("23792.txt"));
        Files.createDirectory(directory.resolve("directory_23792.pdf"));

        ReportFiles.ScanResult result = ReportFiles.scan(directory.toString(),
                Arrays.asList(stat("23792"), null, stat("2379208"), stat("99")));

        assertNull(result.warning);
        assertEquals(4, result.reports.size());
        assertEquals(Arrays.asList(first, second), result.reports.get(0).getFiles());
        assertEquals(0, result.reports.get(1).count());
        assertEquals(Collections.singletonList(other), result.reports.get(2).getFiles());
        assertEquals(0, result.reports.get(3).count());
    }

    @Test
    void missingAndInvalidDirectoriesReturnWarningsAndAlignedEmptyReports() throws Exception {
        Path regularFile = Files.createFile(directory.resolve("not-a-directory"));
        List<SubscriberStat> stats = Arrays.asList(stat("1"), stat("2"));
        for (Path invalid : Arrays.asList(directory.resolve("missing"), regularFile)) {
            ReportFiles.ScanResult result = ReportFiles.scan(invalid.toString(), stats);
            assertTrue(result.warning.contains("nicht erreichbar"));
            assertEquals(2, result.reports.size());
            assertTrue(result.reports.stream().allMatch(reports -> reports.count() == 0));
        }
    }

    @Test
    void distinguishesUnconfiguredFromEmptyAndUnavailableDirectories() {
        assertTrue(ReportFiles.scan(" ", Collections.emptyList()).warning.contains("nicht konfiguriert"));
        assertNull(ReportFiles.scan(directory.toString(), Collections.emptyList()).warning);
        assertTrue(ReportFiles.scan(directory.resolve("missing").toString(), Collections.emptyList())
                .warning.contains("nicht erreichbar"));
    }

    private static SubscriberStat stat(String id) {
        return new SubscriberStat(id, "mql5", "Provider", 0, 0, null, null);
    }
}
