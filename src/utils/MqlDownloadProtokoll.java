package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MqlDownloadProtokoll {
    private static final Logger logger = LogManager.getLogger(MqlDownloadProtokoll.class);
    private final String downloadPath;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MqlDownloadProtokoll(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    /**
     * Löscht die Protokolldatei für die angegebene MQL-Version
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     */
    public void resetProtokoll(String mqlVersion) {
        String filename = getFilename(mqlVersion);
        try {
            Path path = Paths.get(filename);
            if (Files.exists(path)) {
                Files.delete(path);
            }
            Files.createFile(path);
            log(mqlVersion, "=".repeat(80));
            log(mqlVersion, "MQL DOWNLOAD PROTOKOLL - " + mqlVersion.toUpperCase());
            log(mqlVersion, "Protokoll zurückgesetzt am " + LocalDateTime.now().format(formatter));
            log(mqlVersion, "=".repeat(80));
            logger.info("Protokoll für {} wurde zurückgesetzt", mqlVersion);
        } catch (IOException e) {
            logger.error("Fehler beim Zurücksetzen des Protokolls für {}: {}", mqlVersion, e.getMessage());
        }
    }

    /**
     * Löscht alle Protokolldateien (MQL4 und MQL5)
     */
    public void resetAllProtokolle() {
        resetProtokoll("mql4");
        resetProtokoll("mql5");
        logger.info("Alle Protokolle wurden zurückgesetzt");
    }

    /**
     * Protokolliert eine Nachricht für die angegebene MQL-Version
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param message Die zu protokollierende Nachricht
     */
    public void log(String mqlVersion, String message) {
        String filename = getFilename(mqlVersion);
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            
            String logEntry = LocalDateTime.now().format(formatter) + " | " + message + System.lineSeparator();
            Files.write(path, logEntry.getBytes(), StandardOpenOption.APPEND);
            logger.debug("Protokolleintrag für {}: {}", mqlVersion, message);
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben in das Protokoll für {}: {}", mqlVersion, e.getMessage());
        }
    }

    /**
     * Protokolliert einen erfolgreichen Download mit detaillierten Informationen
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param index Der Index des Providers in der Liste
     */
    public void logSuccess(String mqlVersion, String signalProvider, int index) {
        log(mqlVersion, String.format("[%03d] SUCCESS: '%s' - HTML + CSV heruntergeladen", index + 1, signalProvider));
    }
    
    /**
     * Protokolliert einen erfolgreichen Download (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     */
    public void logSuccess(String mqlVersion, String signalProvider) {
        log(mqlVersion, "SUCCESS: '" + signalProvider + "' - HTML + CSV heruntergeladen");
    }

    /**
     * Protokolliert einen fehlgeschlagenen Download mit Grund
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund für den Fehlschlag
     * @param index Der Index des Providers in der Liste
     */
    public void logFailure(String mqlVersion, String signalProvider, String reason, int index) {
        log(mqlVersion, String.format("[%03d] ERROR: '%s' - %s", index + 1, signalProvider, reason));
    }
    
    /**
     * Protokolliert einen fehlgeschlagenen Download mit Grund (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund für den Fehlschlag
     */
    public void logFailure(String mqlVersion, String signalProvider, String reason) {
        log(mqlVersion, "ERROR: '" + signalProvider + "' - " + reason);
    }

    /**
     * Protokolliert, dass eine Datei nicht neu geladen wurde
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund, warum die Datei nicht neu geladen wurde
     * @param index Der Index des Providers in der Liste
     */
    public void logSkipped(String mqlVersion, String signalProvider, String reason, int index) {
        log(mqlVersion, String.format("[%03d] SKIPPED: '%s' - %s", index + 1, signalProvider, reason));
    }
    
    /**
     * Protokolliert, dass eine Datei nicht neu geladen wurde (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund, warum die Datei nicht neu geladen wurde
     */
    public void logSkipped(String mqlVersion, String signalProvider, String reason) {
        log(mqlVersion, "SKIPPED: '" + signalProvider + "' - " + reason);
    }
    
    /**
     * Protokolliert den Versuch, einen Provider zu laden
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param providerId Die ID des Providers
     * @param index Der Index des Providers in der Liste
     */
    public void logAttempt(String mqlVersion, String signalProvider, String providerId, int index) {
        log(mqlVersion, String.format("[%03d] STARTING: '%s' (ID: %s)", index + 1, signalProvider, providerId));
    }

    /**
     * Protokolliert Seitennavigation und Fortschritt
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param pageNumber Die aktuelle Seitennummer
     * @param providersOnPage Anzahl Provider auf der Seite
     * @param totalProcessed Gesamtanzahl bisher verarbeiteter Provider
     */
    public void logPageProgress(String mqlVersion, int pageNumber, int providersOnPage, int totalProcessed) {
        log(mqlVersion, String.format("PAGE %d: %d Provider gefunden | Gesamt verarbeitet: %d", 
                                     pageNumber, providersOnPage, totalProcessed));
    }

    /**
     * Protokolliert Statistiken am Ende des Downloads
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param totalProcessed Gesamtanzahl verarbeiteter Provider
     * @param successful Anzahl erfolgreicher Downloads
     * @param skipped Anzahl übersprungener Provider
     * @param failed Anzahl fehlgeschlagener Downloads
     * @param pagesProcessed Anzahl verarbeiteter Seiten
     */
    public void logFinalStatistics(String mqlVersion, int totalProcessed, int successful, int skipped, int failed, int pagesProcessed) {
        log(mqlVersion, "");
        log(mqlVersion, "=".repeat(80));
        log(mqlVersion, "DOWNLOAD-STATISTIK " + mqlVersion.toUpperCase());
        log(mqlVersion, "=".repeat(80));
        log(mqlVersion, String.format("Verarbeitete Seiten: %d", pagesProcessed));
        log(mqlVersion, String.format("Gesamt Provider: %d", totalProcessed));
        log(mqlVersion, String.format("Erfolgreich: %d (%.1f%%)", successful, 
                                     totalProcessed > 0 ? (successful * 100.0 / totalProcessed) : 0.0));
        log(mqlVersion, String.format("Uebersprungen: %d (%.1f%%)", skipped, 
                                     totalProcessed > 0 ? (skipped * 100.0 / totalProcessed) : 0.0));
        log(mqlVersion, String.format("Fehlgeschlagen: %d (%.1f%%)", failed, 
                                     totalProcessed > 0 ? (failed * 100.0 / totalProcessed) : 0.0));
        log(mqlVersion, "=".repeat(80));
    }

    /**
     * Protokolliert detaillierte Dateiinformationen
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param providerId Die Provider-ID
     * @param htmlFile Name der HTML-Datei
     * @param htmlSize Größe der HTML-Datei in KB
     * @param csvFile Name der CSV-Datei (kann null sein)
     * @param csvSize Größe der CSV-Datei in KB (kann 0 sein)
     */
    public void logFileDetails(String mqlVersion, String signalProvider, String providerId, 
                              String htmlFile, long htmlSize, String csvFile, long csvSize) {
        StringBuilder details = new StringBuilder();
        details.append(String.format("FILES fuer '%s' (ID: %s):", signalProvider, providerId));
        details.append(String.format("\n    HTML: %s (%d KB)", htmlFile, htmlSize));
        if (csvFile != null && csvSize > 0) {
            details.append(String.format("\n    CSV:  %s (%d KB)", csvFile, csvSize));
        } else {
            details.append("\n    CSV:  Nicht verfuegbar");
        }
        log(mqlVersion, details.toString());
    }

    /**
     * Protokolliert Fehler-Recovery-Versuche
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param errorType Art des Fehlers
     * @param recoveryAction Durchgeführte Recovery-Aktion
     * @param success Erfolg der Recovery
     */
    public void logRecoveryAttempt(String mqlVersion, String errorType, String recoveryAction, boolean success) {
        String status = success ? "ERFOLGREICH" : "FEHLGESCHLAGEN";
        log(mqlVersion, String.format("RECOVERY %s: %s -> %s", status, errorType, recoveryAction));
    }

    /**
     * Protokolliert wichtige Systemereignisse
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param event Art des Ereignisses
     * @param details Zusätzliche Details
     */
    public void logSystemEvent(String mqlVersion, String event, String details) {
        log(mqlVersion, String.format("SYSTEM: %s - %s", event, details));
    }

    /**
     * Gibt den Pfad zur Protokolldatei für die angegebene MQL-Version zurück
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @return Der vollständige Pfad zur Protokolldatei
     */
    private String getFilename(String mqlVersion) {
        return Paths.get(downloadPath, mqlVersion.toLowerCase() + "_download_protokoll.txt").toString();
    }
}