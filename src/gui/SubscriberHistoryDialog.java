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
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class SubscriberHistoryDialog extends JDialog {
    private final SubscriberStat stat;
    private final DatabaseManager databaseManager;
    private JPanel contentPanel;
    private JLabel loadingLabel;
    private SwingWorker<List<SubscriberHistoryPoint>, Void> loadWorker;

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

        loadingLabel = new JLabel("Verlaufsdaten werden geladen...", SwingConstants.CENTER);
        loadingLabel.setFont(systemFont("Label.font", Font.PLAIN, 13f));
        contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.add(loadingLabel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        setSize(800, 520);
        setLocationRelativeTo(getParent());
        loadHistory();
    }

    private void loadHistory() {
        if (databaseManager == null) {
            showLoadError("Keine Datenbankverbindung verf\u00fcgbar.");
            return;
        }

        loadWorker = new SwingWorker<List<SubscriberHistoryPoint>, Void>() {
            @Override
            protected List<SubscriberHistoryPoint> doInBackground() {
                List<SubscriberHistoryPoint> history = databaseManager.getSubscriberHistory(
                        stat.getSignalId(), stat.getMqlVersion());
                return history != null ? new ArrayList<>(history) : Collections.emptyList();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    showHistory(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showLoadError("Laden wurde unterbrochen.");
                } catch (CancellationException ignored) {
                    // The dialog was closed while its data was loading.
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showLoadError("Verlaufsdaten konnten nicht geladen werden: " + safeMessage(cause));
                } catch (RuntimeException ex) {
                    showLoadError("Verlaufsdaten konnten nicht angezeigt werden: " + safeMessage(ex));
                }
            }
        };
        loadWorker.execute();
    }

    private void showHistory(List<SubscriberHistoryPoint> history) {

        TimeSeries series = new TimeSeries("Abonnenten");

        int maxSubscribers = Integer.MIN_VALUE;
        int firstSubscribers = 0;
        int lastSubscribers = 0;
        boolean firstPoint = true;

        for (SubscriberHistoryPoint pt : history) {
            if (pt != null && pt.getTimestamp() != null) {
                Date d = new Date(pt.getTimestamp().getTime());
                series.addOrUpdate(new Second(d), pt.getSubscribers());
                if (firstPoint) {
                    firstSubscribers = pt.getSubscribers();
                    firstPoint = false;
                }
                lastSubscribers = pt.getSubscribers();
                if (pt.getSubscribers() > maxSubscribers) maxSubscribers = pt.getSubscribers();
            }
        }

        if (series.isEmpty()) {
            // Fallback if no history yet
            Date fallbackDate = stat.getLastUpdated() != null
                    ? new Date(stat.getLastUpdated().getTime())
                    : new Date();
            series.addOrUpdate(new Second(fallbackDate), stat.getSubscribers());
            maxSubscribers = stat.getSubscribers();
            firstSubscribers = stat.getSubscribers();
            lastSubscribers = stat.getSubscribers();
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);

        String version = stat.getMqlVersion() != null ? stat.getMqlVersion().toUpperCase() : "-";
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Abonnenten-Entwicklung: " + stat.getSignalName() + " (" + version + ")",
                "Zeitpunkt",
                "Abonnenten",
                dataset,
                false,
                true,
                false
        );

        chart.setBackgroundPaint(Color.WHITE);
        Font chartFont = systemFont("Label.font", Font.PLAIN, 11f);
        chart.getTitle().setFont(systemFont("Label.font", Font.BOLD, 15f));

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(245, 248, 250));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd.MM.yyyy HH:mm"));
        domainAxis.setTickLabelFont(chartFont);
        domainAxis.setLabelFont(systemFont("Label.font", Font.BOLD, 12f));

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelFont(chartFont);
        rangeAxis.setLabelFont(systemFont("Label.font", Font.BOLD, 12f));

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
        infoPanel.add(createSummaryLabel("Datenpunkte:", String.valueOf(series.getItemCount()), Color.DARK_GRAY));

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton openBrowserBtn = new JButton("Im Browser \u00f6ffnen");
        openBrowserBtn.setFont(systemFont("Button.font", Font.BOLD, 12f));
        openBrowserBtn.setBackground(new Color(0, 120, 215));
        openBrowserBtn.setForeground(Color.WHITE);
        openBrowserBtn.setFocusPainted(false);
        openBrowserBtn.addActionListener(e -> openUrlInBrowser(stat.getUrl()));

        JButton closeButton = new JButton("Schlie\u00dfen");
        closeButton.addActionListener(e -> dispose());

        southPanel.add(openBrowserBtn);
        southPanel.add(closeButton);

        contentPanel.removeAll();
        contentPanel.add(infoPanel, BorderLayout.NORTH);
        contentPanel.add(chartPanel, BorderLayout.CENTER);
        contentPanel.add(southPanel, BorderLayout.SOUTH);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    @Override
    public void dispose() {
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        super.dispose();
    }

    private void showLoadError(String message) {
        loadingLabel.setText("<html><div style='text-align:center'>" + escapeHtml(message) + "</div></html>");
        loadingLabel.setForeground(new Color(170, 35, 35));
    }

    private void openUrlInBrowser(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return;
        try {
            URI uri = new URI(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Nur HTTP- und HTTPS-Adressen werden unterst\u00fctzt.");
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim \u00d6ffnen der URL: " + safeMessage(ex), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createSummaryLabel(String labelText, String valueText, Color valueColor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        p.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(systemFont("Label.font", Font.PLAIN, 12f));
        JLabel val = new JLabel(valueText);
        val.setFont(systemFont("Label.font", Font.BOLD, 13f));
        val.setForeground(valueColor);
        p.add(lbl);
        p.add(val);
        return p;
    }

    private static Font systemFont(String uiKey, int style, float size) {
        Font font = UIManager.getFont(uiKey);
        if (font == null) {
            font = new Font(Font.DIALOG, Font.PLAIN, Math.round(size));
        }
        return font.deriveFont(style, size);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
