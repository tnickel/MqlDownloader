package gui;

import config.ConfigurationManager;
import converter.HtmlConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

public class ConversionManager {
    private static final Logger logger = LogManager.getLogger(ConversionManager.class);
    private final ConfigurationManager configManager;
    private final LogHandler logHandler;
    private final ButtonPanelManager buttonManager;
    private Thread conversionThread;
    private volatile Exception conversionFailure;

    public ConversionManager(ConfigurationManager configManager, LogHandler logHandler, ButtonPanelManager buttonManager) {
        this.configManager = configManager;
        this.logHandler = logHandler;
        this.buttonManager = buttonManager;
    }

    public void startConversion() {
        startConversion(null, null);
    }

    public void startProviderConversion(String version, String providerId) {
        startConversion(version, providerId);
    }

    private void startConversion(String version, String providerId) {
        conversionFailure = null;
        logHandler.log("Starte Konvertierungsprozess...");
        setupUIForConversion();

        conversionThread = new Thread(() -> {
            try {
                String basePath = configManager.getRootDirPath() + "\\download";
                HtmlConverter converter = new HtmlConverter(basePath, configManager);
                
                converter.setProgressCallback((progress, status) -> {
                    SwingUtilities.invokeLater(() -> {
                        buttonManager.getConvertProgress().setValue(progress);
                        buttonManager.getConvertStatusLabel().setText(status);
                        logHandler.log(status);
                    });
                });
                
                if (providerId == null) converter.convertAllHtmlFiles();
                else converter.convertProvider(version, providerId);
                if (converter.getFailedProvidersCount() > 0) {
                    throw new java.io.IOException(converter.getFailedProvidersCount() + " Provider konnten nicht konvertiert werden");
                }
                
                SwingUtilities.invokeLater(() -> {
                    logHandler.log("Konvertierung erfolgreich abgeschlossen!");
                });
            } catch (Exception e) {
                conversionFailure = e;
                logHandler.logError("Fehler w\u00e4hrend der Konvertierung: " + e.getMessage(), e);
            } finally {
                cleanupConversion();
            }
        });
        
        conversionThread.start();
    }

    private void setupUIForConversion() {
        SwingUtilities.invokeLater(() -> {
            buttonManager.getConvertButton().setEnabled(false);
            buttonManager.getConvertProgress().setValue(0);
            buttonManager.getConvertProgress().setVisible(true);
            buttonManager.getConvertStatusLabel().setVisible(true);
            Window window = SwingUtilities.getWindowAncestor(buttonManager.getConvertButton());
            if (window != null) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        });
    }

    private void cleanupConversion() {
        SwingUtilities.invokeLater(() -> {
            buttonManager.getConvertButton().setEnabled(true);
            Window window = SwingUtilities.getWindowAncestor(buttonManager.getConvertButton());
            if (window != null) {
                window.setCursor(Cursor.getDefaultCursor());
            }
            buttonManager.getConvertProgress().setVisible(false);
            buttonManager.getConvertStatusLabel().setVisible(false);
        });
    }

    public boolean isConversionRunning() {
        return conversionThread != null && conversionThread.isAlive();
    }

    public void waitForConversionCompletion() throws java.io.IOException {
        try {
            while (conversionThread != null && conversionThread.isAlive()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("Warten auf Konvertierung unterbrochen", e);
        }
        if (conversionFailure != null) throw new java.io.IOException("Konvertierung fehlgeschlagen", conversionFailure);
    }
}
