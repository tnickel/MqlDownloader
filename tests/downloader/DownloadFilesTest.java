package downloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class DownloadFilesTest {
    private static final String MT4="Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment";
    @Test void cacheRequiresExactIdAndVersion(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("Alpha_101_root.html"),new byte[]{1});
        Files.write(dir.resolve("AlphaOther_999.csv"),MT4.getBytes(StandardCharsets.UTF_8));
        assertFalse(DownloadFiles.isRecent(dir.toFile(),"101","Alpha","mt4",1,System.currentTimeMillis()));
        Files.write(dir.resolve("Alpha_101.csv"),MT4.getBytes(StandardCharsets.UTF_8));
        assertTrue(DownloadFiles.isRecent(dir.toFile(),"101","Alpha","mt4",1,System.currentTimeMillis()));
        assertFalse(DownloadFiles.isRecent(dir.toFile(),"101","Alpha","mt5",1,System.currentTimeMillis()));
        assertFalse(DownloadFiles.isRecent(dir.toFile(),"101","Alpha","mt4",0,System.currentTimeMillis()));
    }
    @Test void downloadMatchesRequestedIdIncludingBrowserSuffix(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("999.csv"),new byte[]{1});
        assertNull(DownloadFiles.findDownload(dir.toFile(),"101"));
        Files.write(dir.resolve("101 (1).csv"),new byte[]{1});
        assertEquals("101 (1).csv",DownloadFiles.findDownload(dir.toFile(),"101").getName());
    }
    @Test void actualMqlHistoryAndPositionsNamesKeepExactIdAndPlatform(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("1101.history.csv"), new byte[]{1});
        Files.write(dir.resolve("101.positions.csv"), new byte[]{1});
        assertNull(DownloadFiles.findDownload(dir.toFile(), "101", "mt4"));
        assertEquals("101.positions.csv", DownloadFiles.findDownload(dir.toFile(), "101", "mt5").getName());
        Files.write(dir.resolve("101.history.csv"), new byte[]{1});
        assertEquals("101.history.csv", DownloadFiles.findDownload(dir.toFile(), "101", "mt4").getName());
        Files.delete(dir.resolve("101.positions.csv"));
        Files.write(dir.resolve("101.positions (2).csv"), new byte[]{1});
        assertEquals("101.positions (2).csv", DownloadFiles.findDownload(dir.toFile(), "101", "mql5").getName());
        assertNull(DownloadFiles.findDownload(dir.toFile(), "101", "unknown"));
    }
}
