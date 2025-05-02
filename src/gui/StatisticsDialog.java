package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import config.ConfigurationManager;
import utils.FileStatistics;

/**
 * Dialog zur Anzeige der Downloadstatistik.
 */
public class StatisticsDialog extends JDialog {
    private static final Logger logger = LogManager.getLogger(StatisticsDialog.class);
    private final ConfigurationManager configManager;
    private static final int MAX_DAYS = 30;
    
    /**
     * Konstruktor für den Statistik-Dialog.
     * 
     * @param parent Das übergeordnete Frame
     * @param configManager Der ConfigurationManager
     */
    public StatisticsDialog(JFrame parent, ConfigurationManager configManager) {
        super(parent, "Download Statistik", true);
        this.configManager = configManager;
        initializeComponents();
    }
    
    /**
     * Initialisiert die Komponenten des Dialogs.
     */
    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Layout geändert zu 2 Zeilen, 1 Spalte für untereinander angeordnete Grafiken
        JPanel mainPanel = new JPanel(new GridLayout(2, 1, 10, 20)); 
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // MQL4 Chart
        String mql4Path = configManager.getRootDirPath() + "\\download\\mql4";
        Map<Integer, Integer> mql4Stats = FileStatistics.analyzeFileAge(mql4Path, MAX_DAYS);
        JFreeChart mql4Chart = createAgeChart(mql4Stats, "MQL4 Dateialter", "Alter (Tage)", "Anzahl Dateien");
        ChartPanel mql4ChartPanel = new ChartPanel(mql4Chart);
        mql4ChartPanel.setPreferredSize(new Dimension(700, 250)); // Breiteres Format
        
        // MQL5 Chart
        String mql5Path = configManager.getRootDirPath() + "\\download\\mql5";
        Map<Integer, Integer> mql5Stats = FileStatistics.analyzeFileAge(mql5Path, MAX_DAYS);
        JFreeChart mql5Chart = createAgeChart(mql5Stats, "MQL5 Dateialter", "Alter (Tage)", "Anzahl Dateien");
        ChartPanel mql5ChartPanel = new ChartPanel(mql5Chart);
        mql5ChartPanel.setPreferredSize(new Dimension(700, 250)); // Breiteres Format
        
        // Zum Panel hinzufügen
        mainPanel.add(mql4ChartPanel);
        mainPanel.add(mql5ChartPanel);
        
        // Statistik-Zusammenfassung
        JPanel statsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        statsPanel.add(createSummaryPanel("MQL4 Statistik", mql4Stats));
        statsPanel.add(createSummaryPanel("MQL5 Statistik", mql5Stats));
        
        // Schließen-Button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Schließen");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        
        // Alles zusammenfügen
        add(mainPanel, BorderLayout.CENTER);
        
        // Stats und Button in einem südlichen Panel
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(statsPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
        
        // Dialog konfigurieren
        pack();
        setSize(800, 700); // Größerer Dialog für bessere Lesbarkeit
        setResizable(true);
        setLocationRelativeTo(getParent());
        logger.info("Statistik-Dialog initialisiert");
    }
    
    /**
     * Erstellt ein Panel mit der Zusammenfassung der Statistik.
     * 
     * @param title Der Titel des Panels
     * @param stats Die Statistik-Daten
     * @return Das erstellte Panel
     */
    private JPanel createSummaryPanel(String title, Map<Integer, Integer> stats) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        
        int totalFiles = stats.values().stream().mapToInt(Integer::intValue).sum();
        int filesLastDay = stats.getOrDefault(0, 0);
        int filesLastWeek = countFilesInRange(stats, 0, 7);
        
        JLabel summaryLabel = new JLabel(
            String.format("Gesamt: %d Dateien | Letzte 24h: %d | Letzte Woche: %d", 
                totalFiles, filesLastDay, filesLastWeek));
        panel.add(summaryLabel);
        
        return panel;
    }
    
    /**
     * Zählt die Dateien in einem bestimmten Altersbereich.
     * 
     * @param stats Die Statistik-Daten
     * @param minDays Die minimale Anzahl von Tagen
     * @param maxDays Die maximale Anzahl von Tagen
     * @return Die Anzahl der Dateien im angegebenen Bereich
     */
    private int countFilesInRange(Map<Integer, Integer> stats, int minDays, int maxDays) {
        int count = 0;
        for (int i = minDays; i <= maxDays; i++) {
            count += stats.getOrDefault(i, 0);
        }
        return count;
    }
    
    /**
     * Erstellt ein Balkendiagramm basierend auf den Statistikdaten mit verbesserten Labels.
     * 
     * @param stats Die Statistik-Daten
     * @param title Der Titel des Diagramms
     * @param xAxisLabel Die Beschriftung der X-Achse
     * @param yAxisLabel Die Beschriftung der Y-Achse
     * @return Das erstellte Diagramm
     */
    private JFreeChart createAgeChart(Map<Integer, Integer> stats, String title, String xAxisLabel, String yAxisLabel) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        // Klarere Kategorie-Namen für die ersten 7 Tage
        dataset.addValue(stats.getOrDefault(0, 0), "Dateien", "Heute");
        dataset.addValue(stats.getOrDefault(1, 0), "Dateien", "1 Tag");
        dataset.addValue(stats.getOrDefault(2, 0), "Dateien", "2 Tage");
        dataset.addValue(stats.getOrDefault(3, 0), "Dateien", "3 Tage");
        dataset.addValue(stats.getOrDefault(4, 0), "Dateien", "4 Tage");
        dataset.addValue(stats.getOrDefault(5, 0), "Dateien", "5 Tage");
        dataset.addValue(stats.getOrDefault(6, 0), "Dateien", "6 Tage");
        
        // Wöchentlicher Granularität für den Rest mit verbesserten Labels
        dataset.addValue(countFilesInRange(stats, 7, 13), "Dateien", "1 Woche");
        dataset.addValue(countFilesInRange(stats, 14, 20), "Dateien", "2 Wochen");
        dataset.addValue(countFilesInRange(stats, 21, 27), "Dateien", "3 Wochen");
        dataset.addValue(countFilesInRange(stats, 28, MAX_DAYS), "Dateien", "Älter");
        
        JFreeChart chart = ChartFactory.createBarChart(
            title,
            xAxisLabel,
            yAxisLabel,
            dataset,
            PlotOrientation.VERTICAL,
            false,  // Legende
            true,   // Tooltips
            false   // URLs
        );
        
        // Anpassung des Diagramms
        chart.setBackgroundPaint(Color.white);
        
        // Titel in größerer Schriftart
        TextTitle textTitle = chart.getTitle();
        textTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.white); // Hellerer Hintergrund
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);
        
        // X-Achse besser formatieren
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12)); // Größere Schrift
        domainAxis.setCategoryMargin(0.3); // Mehr Platz zwischen Kategorien
        
        // Y-Achse als ganze Zahlen formatieren
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12)); // Größere Schrift
        
        // Farben für die Balken
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(65, 105, 225));  // Royal Blue
        renderer.setDrawBarOutline(true);
        renderer.setSeriesOutlinePaint(0, Color.DARK_GRAY);
        
        return chart;
    }
}