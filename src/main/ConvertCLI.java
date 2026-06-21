package main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import config.ConfigurationManager;
import converter.HtmlConverter;
import logging.LoggerManager;

public class ConvertCLI {
    private static final Logger logger = LogManager.getLogger(ConvertCLI.class);

    public static void main(String[] args) {
        try {
            System.out.println("Starting batch conversion of HTML files...");
            
            // ConfigurationManager mit dem Standardpfad initialisieren
            ConfigurationManager configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
            configManager.initializeDirectories();
            
            // Logger initialisieren
            LoggerManager.initializeLogger(configManager.getLogConfigPath());
            
            logger.info("ConvertCLI started");
            
            String basePath = configManager.getRootDirPath() + "\\download";
            System.out.println("Download Path: " + basePath);
            
            HtmlConverter converter = new HtmlConverter(basePath, configManager);
            converter.setProgressCallback((progress, status) -> {
                System.out.printf("[%d%%] %s\n", progress, status);
            });
            
            System.out.println("Converting files...");
            converter.convertAllHtmlFiles();
            
            System.out.println("Conversion finished successfully!");
        } catch (Exception e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
