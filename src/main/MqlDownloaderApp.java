package main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import config.ConfigurationManager;
import gui.MqlDownloaderGui;
import logging.LoggerManager;

public class MqlDownloaderApp {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderApp.class);

    public static void main(String[] args) {
        try {
            // Zuerst ConfigurationManager initialisieren
            ConfigurationManager configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
            configManager.initializeDirectories();
            
            // Logger initialisieren bevor wir ihn verwenden
            LoggerManager.initializeLogger(configManager.getLogConfigPath());
            
            // Jetzt loggen
            logger.info("MqlDownloaderApp wird gestartet");
            
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    MqlDownloaderGui gui = new MqlDownloaderGui();
                    gui.setVisible(true);
                } catch (Exception e) {
                    logger.error("Fehler beim Starten der GUI", e);
                }
            });
        } catch (Exception e) {
            // Fallback, falls Logger nicht initialisiert werden konnte
            System.err.println("Fehler beim Initialisieren der Anwendung: " + e.getMessage());
            e.printStackTrace();
        }
    }
}