package gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stufe 0 (SignalKiScanner doc/23): die 3-Tage-Vermerk-Helfer des
 * MqlDownloader-Update-Jobs (config/update_state.json).
 */
class UpdateVermerkTest {

    @TempDir
    Path tempDir;

    @Test
    void schreibeUndLeseRoundtrip() throws Exception {
        Path vermerk = tempDir.resolve("update_state.json");
        assertNull(MqlDownloaderGui.leseKatalogVermerk(vermerk), "ohne Datei = nie geladen");
        MqlDownloaderGui.schreibeKatalogVermerk(vermerk);
        String gelesen = MqlDownloaderGui.leseKatalogVermerk(vermerk);
        assertNotNull(gelesen);
        // parsebarer ISO-Instant (Grundlage der Frische-Prüfung)
        assertNotNull(Instant.parse(gelesen));
    }

    @Test
    void frischePruefungGrenzen() {
        String jetzt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String vor2h = Instant.now().minus(2, ChronoUnit.HOURS).toString();
        String vor4Tagen = Instant.now().minus(4 * 24, ChronoUnit.HOURS).toString();

        assertTrue(MqlDownloaderGui.katalogFrischGenug(jetzt, 72));
        assertTrue(MqlDownloaderGui.katalogFrischGenug(vor2h, 72), "3-Tage-Regel: 2 h alt = frisch genug");
        assertFalse(MqlDownloaderGui.katalogFrischGenug(vor4Tagen, 72), "4 Tage alt = neu laden");
        assertFalse(MqlDownloaderGui.katalogFrischGenug("kein-datum", 72), "defekt = ehrlich neu laden");
        // 0 Stunden Fenster = immer neu laden (Admin-Setting 0 ist gültig)
        assertFalse(MqlDownloaderGui.katalogFrischGenug(jetzt, 0));
    }

    @Test
    void defekteVermerkDateiLiefertNull() throws Exception {
        Path vermerk = tempDir.resolve("update_state.json");
        Files.writeString(vermerk, "pfad-muell ohne schluessel\n", StandardCharsets.UTF_8);
        assertNull(MqlDownloaderGui.leseKatalogVermerk(vermerk));
    }
}
