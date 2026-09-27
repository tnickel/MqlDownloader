package config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Instanz-Kennung (instanceName): Der SignalKiScanner unterscheidet mehrere
 * Downloader-Instanzen anhand des Feldes "instance" aus /api/v1/health.
 */
class ConfigurationManagerTest {

    @TempDir
    Path tempDirectory;

    private ConfigurationManager manager;

    @BeforeEach
    void setUp() {
        // Der Manager erwartet ein vorhandenes config-Verzeichnis.
        assertTrue(new File(tempDirectory.toFile(), "config").mkdirs());
        manager = new ConfigurationManager(tempDirectory.toString());
    }

    @Test
    void instanzKennungHatNichtLeerenDefault() {
        String name = manager.getInstanceName();
        assertTrue(name != null && !name.trim().isEmpty(),
                "Default (Rechnername) darf nie leer sein");
    }

    @Test
    void instanzKennungWirdGespeichertUndPersistiert() {
        manager.setInstanceName("Buerolaptop-DL");
        assertEquals("Buerolaptop-DL", manager.getInstanceName());
        // Neue Manager-Instanz auf demselben Verzeichnis liest denselben Wert.
        assertEquals("Buerolaptop-DL",
                new ConfigurationManager(tempDirectory.toString()).getInstanceName());
    }

    @Test
    void leereInstanzKennungFaelltAufDefaultZurueck() {
        manager.setInstanceName("Erst-Setzen");
        manager.setInstanceName("   ");
        assertNotEquals("Erst-Setzen", manager.getInstanceName());
        assertFalse(manager.getInstanceName().trim().isEmpty());
    }
}
