package gui;

import javax.swing.*;
import java.awt.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import browser.WebDriverManager;
import config.ConfigurationManager;
import downloader.SignalDownloader;

public class MqlDownloaderGui extends JFrame {
    private static final Logger logger = LogManager.getLogger(MqlDownloaderGui.class);
    private final ConfigurationManager configManager;

    public MqlDownloaderGui() {
        configManager = new ConfigurationManager("C:\\Forex\\MqlAnalyzer");
        initializeGui();
    }

    private void initializeGui() {
        setTitle("MQL Signal Downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(2, 1, 10, 10));
        setSize(300, 150);
        setLocationRelativeTo(null);

        JButton mql4Button = new JButton("MQL4 Download");
        JButton mql5Button = new JButton("MQL5 Download");

        mql4Button.setFont(new Font("Arial", Font.BOLD, 14));
        mql5Button.setFont(new Font("Arial", Font.BOLD, 14));

        mql4Button.addActionListener(e -> {
            String downloadPath = configManager.getRootDirPath() + "\\download\\mql4";
            configManager.setDownloadPath(downloadPath);
            configManager.setBaseUrl("https://www.mql5.com/en/signals/mt4/list");
            startDownload("MQL4");
        });

        mql5Button.addActionListener(e -> {
            String downloadPath = configManager.getRootDirPath() + "\\download\\mql5";
            configManager.setDownloadPath(downloadPath);
            configManager.setBaseUrl("https://www.mql5.com/en/signals/mt5/list");
            startDownload("MQL5");
        });

        JPanel mql4Panel = new JPanel(new FlowLayout());
        JPanel mql5Panel = new JPanel(new FlowLayout());

        mql4Panel.add(mql4Button);
        mql5Panel.add(mql5Button);

        add(mql4Panel);
        add(mql5Panel);
    }

    private void startDownload(String mqlVersion) {
        try {
            configManager.initializeDirectories();
            new Thread(() -> {
                WebDriver driver = null;
                try {
                    WebDriverManager webDriverManager = new WebDriverManager(configManager.getDownloadPath());
                    driver = webDriverManager.initializeDriver();

                    SignalDownloader downloader = new SignalDownloader(driver, configManager, configManager.getCredentials());
                    downloader.startDownloadProcess();

                    JOptionPane.showMessageDialog(this,
                        mqlVersion + " Download erfolgreich beendet",
                        "Download Status",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    logger.error("Fehler beim " + mqlVersion + " Download:", e);
                    JOptionPane.showMessageDialog(this,
                        "Fehler beim Download: " + e.getMessage(),
                        "Fehler",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    if (driver != null) {
                        driver.quit();
                    }
                }
            }).start();
        } catch (Exception e) {
            logger.error("Fehler beim Initialisieren des Downloads:", e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MqlDownloaderGui gui = new MqlDownloaderGui();
            gui.setVisible(true);
        });
    }
}