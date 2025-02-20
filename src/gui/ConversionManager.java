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

    public ConversionManager(ConfigurationManager configManager, LogHandler logHandler, ButtonPanelManager buttonManager) {
        this.configManager = configManager;
        this.logHandler = logHandler;
        this.buttonManager = buttonManager;
    }

    public void startConversion() {
        logHandler.log("Starte Konvertierungsprozess...");
        setupUIForConversion();

        conversionThread = new Thread(() -> {
            try {
                String basePath = configManager.getRootDirPath() + "\\download";
                HtmlConverter converter = new HtmlConverter(basePath);
                
                converter.setProgressCallback((progress, status) -> {
                    SwingUtilities.invokeLater(() -> {
                        buttonManager.getConvertProgress().setValue(progress);
                        buttonManager.getConvertStatusLabel().setText(status);
                        logHandler.log(status);
                    });
                });
                
                converter.convertAllHtmlFiles();
                
                SwingUtilities.invokeLater(() -> {
                    logHandler.log("Konvertierung erfolgreich abgeschlossen!");
                });
            } catch (Exception e) {
                logHandler.logError("Fehler während der Konvertierung: " + e.getMessage(), e);
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

    public void waitForConversionCompletion() {
        try {
            while (conversionThread != null && conversionThread.isAlive()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}