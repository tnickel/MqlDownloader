package main;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gui.MqlDownloaderGui;

public class MqlDownloaderApp {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderApp.class);

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                MqlDownloaderGui gui = new MqlDownloaderGui();
                gui.setVisible(true);
            } catch (Exception e) {
                logger.error("Fehler beim Starten der GUI", e);
            }
        });
    }
}