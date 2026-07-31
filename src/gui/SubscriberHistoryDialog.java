package gui;

import database.DatabaseManager;
import database.SubscriberHistoryPoint;
import database.SubscriberStat;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SubscriberHistoryDialog extends JDialog {
    private final SubscriberStat stat;
    private final DatabaseManager databaseManager;

    public SubscriberHistoryDialog(JDialog parent, SubscriberStat stat, DatabaseManager databaseManager) {
        super(parent, "Abonnenten-Verlauf: " + stat.getSignalName(), true);
        this.stat = stat;
        this.databaseManager = databaseManager;
        initializeComponents();
    }

    public SubscriberHistoryDialog(JFrame parent, SubscriberStat stat, DatabaseManager databaseManager) {
        super(parent, "Abonnenten-Verlauf: " + stat.getSignalName(), true);
        this.stat = stat;
        this.databaseManager = databaseManager;
        initializeComponents();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));

        List<SubscriberHistoryPoint> history = databaseManager.getSubscriberHistory(stat.getSignalId());

        TimeSeries series = new TimeSeries("Abonnenten");

        int maxSubscribers = 0;
        int minSubscribers = Integer.MAX_VALUE;
        int firstSubscribers = 0;
        int lastSubscribers = 0;

        if (!history.isEmpty()) {
            firstSubscribers = history.get(0).getSubscribers();
            lastSubscribers = history.get(history.size() - 1).getSubscribers();

            for (SubscriberHistoryPoint pt : history) {
                Date d = new Date(pt.getTimestamp().getTime());
                series.addOrUpdate(new Second(d), pt.getSubscribers());
                if (pt.getSubscribers() > maxSubscribers) maxSubscribers = pt.getSubscribers();
                if (pt.getSubscribers() < minSubscribers) minSubscribers = pt.getSubscribers();
            }
        } else {
            // Fallback if no history yet
            series.addOrUpdate(new Second(new Date(stat.getLastUpdated().getTime())), stat.getSubscribers());
            maxSubscribers = stat.getSubscribers();
            minSubscribers = stat.getSubscribers();
            firstSubscribers = stat.getSubscribers();
            lastSubscribers = stat.getSubscribers();
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Abonnenten-Entwicklung: " + stat.getSignalName() + " (" + stat.getMqlVersion().toUpperCase() + ")",
                "Zeitpunkt",
                "Abonnenten",
                dataset,
                false,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(245, 248, 250));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd.MM.yyyy HH:mm"));
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 11));

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 11));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(0, 102, 204));
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(750, 400));
        chartPanel.setMouseWheelEnabled(true);

        // Header / Summary Info Panel
        JPanel infoPanel = new JPanel(new GridLayout(1, 4, 10, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Zusammenfassung"));
        infoPanel.setBackground(Color.WHITE);

        int netChange = lastSubscribers - firstSubscribers;
        String changeStr = (netChange >= 0 ? "+" : "") + netChange;
        Color changeColor = netChange > 0 ? new Color(0, 128, 0) : (netChange < 0 ? Color.RED : Color.BLACK);

        infoPanel.add(createSummaryLabel("Aktuell:", String.valueOf(stat.getSubscribers()), Color.BLUE));
        infoPanel.add(createSummaryLabel("Gesamt\u00e4nderung:", changeStr, changeColor));
        infoPanel.add(createSummaryLabel("H\u00f6chstwert:", String.valueOf(maxSubscribers), new Color(128, 0, 128)));
        infoPanel.add(createSummaryLabel("Datenpunkte:", String.valueOf(history.size()), Color.DARK_GRAY));

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton openBrowserBtn = new JButton("Im Browser \u00f6ffnen");
        openBrowserBtn.setFont(new Font("Arial", Font.BOLD, 12));
        openBrowserBtn.setBackground(new Color(0, 120, 215));
        openBrowserBtn.setForeground(Color.WHITE);
        openBrowserBtn.setFocusPainted(false);
        openBrowserBtn.addActionListener(e -> openUrlInBrowser(stat.getUrl()));

        JButton closeButton = new JButton("Schlie\u00dfen");
        closeButton.addActionListener(e -> dispose());

        southPanel.add(openBrowserBtn);
        southPanel.add(closeButton);

        add(infoPanel, BorderLayout.NORTH);
        add(chartPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        pack();
        setSize(800, 520);
        setLocationRelativeTo(getParent());
    }

    private void openUrlInBrowser(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(urlString));
            } else {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + urlString);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim \u00d6ffnen der URL: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createSummaryLabel(String labelText, String valueText, Color valueColor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        p.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Arial", Font.PLAIN, 12));
        JLabel val = new JLabel(valueText);
        val.setFont(new Font("Arial", Font.BOLD, 13));
        val.setForeground(valueColor);
        p.add(lbl);
        p.add(val);
        return p;
    }
}
