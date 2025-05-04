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
     * L�scht die Protokolldatei f�r die angegebene MQL-Version
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
            log(mqlVersion, "Protokoll wurde zur�ckgesetzt am " + LocalDateTime.now().format(formatter));
            logger.info("Protokoll f�r {} wurde zur�ckgesetzt", mqlVersion);
        } catch (IOException e) {
            logger.error("Fehler beim Zur�cksetzen des Protokolls f�r {}: {}", mqlVersion, e.getMessage());
        }
    }

    /**
     * L�scht alle Protokolldateien (MQL4 und MQL5)
     */
    public void resetAllProtokolle() {
        resetProtokoll("mql4");
        resetProtokoll("mql5");
        logger.info("Alle Protokolle wurden zur�ckgesetzt");
    }

    /**
     * Protokolliert eine Nachricht f�r die angegebene MQL-Version
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
            
            String logEntry = LocalDateTime.now().format(formatter) + " - " + message + System.lineSeparator();
            Files.write(path, logEntry.getBytes(), StandardOpenOption.APPEND);
            logger.debug("Protokolleintrag f�r {}: {}", mqlVersion, message);
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben in das Protokoll f�r {}: {}", mqlVersion, e.getMessage());
        }
    }

    /**
     * Protokolliert einen erfolgreichen Download
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param index Der Index des Providers in der Liste
     */
    public void logSuccess(String mqlVersion, String signalProvider, int index) {
        log(mqlVersion, String.format("[%d] Erfolgreich geladen: %s", index, signalProvider));
    }
    
    /**
     * Protokolliert einen erfolgreichen Download (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     */
    public void logSuccess(String mqlVersion, String signalProvider) {
        log(mqlVersion, "Erfolgreich geladen: " + signalProvider);
    }

    /**
     * Protokolliert einen fehlgeschlagenen Download mit Grund
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund f�r den Fehlschlag
     * @param index Der Index des Providers in der Liste
     */
    public void logFailure(String mqlVersion, String signalProvider, String reason, int index) {
        log(mqlVersion, String.format("[%d] Fehler beim Laden von %s: %s", index, signalProvider, reason));
    }
    
    /**
     * Protokolliert einen fehlgeschlagenen Download mit Grund (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund f�r den Fehlschlag
     */
    public void logFailure(String mqlVersion, String signalProvider, String reason) {
        log(mqlVersion, "Fehler beim Laden von " + signalProvider + ": " + reason);
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
        log(mqlVersion, String.format("[%d] �bersprungen: %s - %s", index, signalProvider, reason));
    }
    
    /**
     * Protokolliert, dass eine Datei nicht neu geladen wurde (ohne Index)
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @param signalProvider Der Name des Signalproviders
     * @param reason Der Grund, warum die Datei nicht neu geladen wurde
     */
    public void logSkipped(String mqlVersion, String signalProvider, String reason) {
        log(mqlVersion, "�bersprungen: " + signalProvider + " - " + reason);
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
        log(mqlVersion, String.format("[%d] Versuche Provider zu laden: %s (ID: %s)", index, signalProvider, providerId));
    }

    /**
     * Gibt den Pfad zur Protokolldatei f�r die angegebene MQL-Version zur�ck
     *
     * @param mqlVersion Die MQL-Version (mql4 oder mql5)
     * @return Der vollst�ndige Pfad zur Protokolldatei
     */
    private String getFilename(String mqlVersion) {
        return Paths.get(downloadPath, mqlVersion.toLowerCase() + "download.txt").toString();
    }
}