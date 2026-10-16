package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {
    @TempDir Path dir;

    void put(String name, String value) throws Exception {
        Files.write(dir.resolve(name), value.getBytes(StandardCharsets.UTF_8));
    }
    String read(String name) throws Exception {
        return new String(Files.readAllBytes(dir.resolve(name)), StandardCharsets.UTF_8);
    }

    @Test void sameProviderNameDifferentIdsPreservesAllCsvAndTxt() throws Exception {
        for (String id : List.of("2360068", "2271995", "2362400")) {
            put("RemoteRecovery_" + id + "_root.html", "html " + id);
            put("RemoteRecovery_" + id + ".csv", "trades " + id);
            put("RemoteRecovery_" + id + "_root.txt", "metrics " + id);
        }
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        for (String id : List.of("2360068", "2271995", "2362400")) {
            assertEquals("trades " + id, read("RemoteRecovery_" + id + ".csv"));
            assertEquals("metrics " + id, read("RemoteRecovery_" + id + "_root.txt"));
        }
    }

    @Test void uniqueHtmlAndSingleWrongNumberStillCorrectsCsvAndTxt() throws Exception {
        put("Unique_Name_200_root.html", "html");
        put("Unique_Name_100.csv", "trades");
        put("Unique_Name_100_root.txt", "metrics");
        assertEquals(2, FileUtils.correctFileNumbers(dir.toString()).size());
        assertEquals("trades", read("Unique_Name_200.csv"));
        assertEquals("metrics", read("Unique_Name_200_root.txt"));
        assertFalse(Files.exists(dir.resolve("Unique_Name_100.csv")));
    }

    @Test void existingCsvTargetIsNeverOverwritten() throws Exception {
        put("Name_200_root.html", "html");
        put("Name_100.csv", "wrong-number-history");
        put("Name_200.csv", "existing-history");
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        assertEquals("wrong-number-history", read("Name_100.csv"));
        assertEquals("existing-history", read("Name_200.csv"));
    }

    @Test void existingTxtTargetIsNeverOverwritten() throws Exception {
        put("Name_200_root.html", "html");
        put("Name_100_root.txt", "wrong-number-metrics");
        put("Name_200_root.txt", "existing-metrics");
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        assertEquals("wrong-number-metrics", read("Name_100_root.txt"));
        assertEquals("existing-metrics", read("Name_200_root.txt"));
    }

    @Test void multipleWrongCsvCandidatesAreNotArbitrarilyChosen() throws Exception {
        put("Name_200_root.html", "html");
        put("Name_100.csv", "first");
        put("Name_300.csv", "second");
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        assertEquals("first", read("Name_100.csv"));
        assertEquals("second", read("Name_300.csv"));
        assertFalse(Files.exists(dir.resolve("Name_200.csv")));
    }

    @Test void sameHtmlIdUnderDifferentNamesIsAmbiguous() throws Exception {
        put("AliasA_200_root.html", "a");
        put("AliasB_200_root.html", "b");
        put("AliasA_100.csv", "first");
        put("AliasB_300_root.txt", "second");
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        assertEquals("first", read("AliasA_100.csv"));
        assertEquals("second", read("AliasB_300_root.txt"));
    }

    @Test void directoryAtTargetDoesNotLoseSource() throws Exception {
        put("Name_200_root.html", "html");
        put("Name_100.csv", "history");
        Files.createDirectory(dir.resolve("Name_200.csv"));
        assertTrue(FileUtils.correctFileNumbers(dir.toString()).isEmpty());
        assertEquals("history", read("Name_100.csv"));
        assertTrue(Files.isDirectory(dir.resolve("Name_200.csv")));
    }
}
