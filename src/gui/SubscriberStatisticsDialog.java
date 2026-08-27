package gui;

import database.DatabaseManager;
import database.SubscriberStat;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class SubscriberStatisticsDialog extends JDialog {
    private static final String UNKNOWN_CHANGE_TOOLTIP =
            "Kein belastbarer Vergleichswert für diesen Zeitraum vorhanden.";
    private static final Comparator<Integer> NULL_SAFE_INTEGER_COMPARATOR =
            Comparator.nullsFirst(Integer::compareTo);

    private final DatabaseManager databaseManager;
    private JTable statsTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private JButton refreshButton;
    private SwingWorker<List<SubscriberStat>, Void> loadWorker;
    private List<SubscriberStat> currentStatsList = new ArrayList<>();

    public SubscriberStatisticsDialog(JFrame parent, DatabaseManager databaseManager) {
        super(parent, "Abonnentenstatistik", true);
        this.databaseManager = databaseManager;
        initializeComponents();
        loadData();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));

        // Top Panel: Search and Instructions
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        JLabel instructionLabel = new JLabel("<html><b>Hinweis:</b> – bedeutet, dass für den Zeitraum kein belastbarer Vergleichswert vorhanden ist. "
                + "Klick auf einen Spaltenkopf sortiert die Tabelle; Doppelklick öffnet die Verlaufskurve.</html>");
        instructionLabel.setToolTipText(UNKNOWN_CHANGE_TOOLTIP);
        instructionLabel.setFont(systemFont("Label.font", Font.PLAIN, 12f));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        filterPanel.add(new JLabel("Provider suchen:"));
        searchField = new JTextField(25);
        searchField.setFont(systemFont("TextField.font", Font.PLAIN, 13f));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        filterPanel.add(searchField);

        topPanel.add(instructionLabel, BorderLayout.NORTH);
        topPanel.add(filterPanel, BorderLayout.SOUTH);

        // Table Setup
        String[] columnNames = {
                "Signalprovider Name",
                "Version",
                "Abonnenten",
                "Seit letzter Messung",
                "7 Tage",
                "30 Tage",
                "Letzte Messung",
                "URL"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Table read-only
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                        return Integer.class;
                    default:
                        return String.class;
                }
            }
        };

        statsTable = new JTable(tableModel);
        statsTable.setFont(systemFont("Table.font", Font.PLAIN, 12f));
        statsTable.setRowHeight(24);
        statsTable.getTableHeader().setFont(systemFont("TableHeader.font", Font.BOLD, 12f));
        statsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        // Custom Comparators for proper numerical sorting
        sorter.setComparator(2, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(3, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(4, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(5, NULL_SAFE_INTEGER_COMPARATOR);
        statsTable.setRowSorter(sorter);

        // Renderers
        statsTable.getColumnModel().getColumn(1).setCellRenderer(new AlignmentCellRenderer(SwingConstants.CENTER));
        statsTable.getColumnModel().getColumn(2).setCellRenderer(new AlignmentCellRenderer(SwingConstants.RIGHT));
        statsTable.getColumnModel().getColumn(3).setCellRenderer(new ChangeCellRenderer());
        statsTable.getColumnModel().getColumn(4).setCellRenderer(new ChangeCellRenderer());
        statsTable.getColumnModel().getColumn(5).setCellRenderer(new ChangeCellRenderer());
        statsTable.getColumnModel().getColumn(7).setCellRenderer(new UrlCellRenderer());

        // Column widths
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(210);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        statsTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        statsTable.getColumnModel().getColumn(3).setPreferredWidth(105);
        statsTable.getColumnModel().getColumn(4).setPreferredWidth(95);
        statsTable.getColumnModel().getColumn(5).setPreferredWidth(95);
        statsTable.getColumnModel().getColumn(6).setPreferredWidth(145);
        statsTable.getColumnModel().getColumn(7).setPreferredWidth(125);

        // Click & Double Click Listener
        statsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = statsTable.getSelectedRow();
                int col = statsTable.getSelectedColumn();
                if (row != -1 && col != -1) {
                    int modelRow = statsTable.convertRowIndexToModel(row);
                    int modelCol = statsTable.convertColumnIndexToModel(col);
                    if (modelRow >= 0 && modelRow < currentStatsList.size()) {
                        SubscriberStat stat = currentStatsList.get(modelRow);
                        if (modelCol == 7) { // Click on URL column
                            openUrlInBrowser(stat.getUrl());
                        } else if (e.getClickCount() == 2) {
                            openHistoryChart(stat);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        statusLabel = new JLabel("Lade Daten...");
        statusLabel.setFont(systemFont("Label.font", Font.ITALIC, 12f));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton openBrowserBtn = new JButton("Im Browser \u00f6ffnen");
        openBrowserBtn.setFont(systemFont("Button.font", Font.BOLD, 12f));
        openBrowserBtn.setBackground(new Color(0, 120, 215));
        openBrowserBtn.setForeground(Color.WHITE);
        openBrowserBtn.setFocusPainted(false);
        openBrowserBtn.addActionListener(e -> {
            int selectedRow = statsTable.getSelectedRow();
            if (selectedRow != -1) {
                int modelRow = statsTable.convertRowIndexToModel(selectedRow);
                if (modelRow >= 0 && modelRow < currentStatsList.size()) {
                    SubscriberStat stat = currentStatsList.get(modelRow);
                    openUrlInBrowser(stat.getUrl());
                }
            } else {
                JOptionPane.showMessageDialog(this, "Bitte w\u00e4hlen Sie einen Provider in der Tabelle aus.", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        refreshButton = new JButton("Aktualisieren");
        refreshButton.addActionListener(e -> loadData());

        JButton closeButton = new JButton("Schlie\u00dfen");
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(openBrowserBtn);
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(1020, 600);
        setLocationRelativeTo(getParent());
    }

    public void loadData() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::loadData);
            return;
        }

        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }

        tableModel.setRowCount(0);
        currentStatsList.clear();
        statusLabel.setText("Lade Daten...");
        refreshButton.setEnabled(false);

        if (databaseManager == null) {
            statusLabel.setText("Keine Datenbankverbindung verf\u00fcgbar.");
            refreshButton.setEnabled(true);
            return;
        }

        loadWorker = new SwingWorker<List<SubscriberStat>, Void>() {
            @Override
            protected List<SubscriberStat> doInBackground() {
                List<SubscriberStat> stats = databaseManager.getAllSubscriberStatistics();
                return stats != null ? new ArrayList<>(stats) : new ArrayList<>();
            }

            @Override
            protected void done() {
                if (this != loadWorker) {
                    return;
                }

                refreshButton.setEnabled(true);
                if (isCancelled()) {
                    return;
                }

                try {
                    applyData(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Laden wurde unterbrochen.");
                } catch (CancellationException ignored) {
                    // A newer refresh replaced this request.
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Daten konnten nicht geladen werden: " + safeMessage(cause));
                } catch (RuntimeException ex) {
                    statusLabel.setText("Daten konnten nicht angezeigt werden: " + safeMessage(ex));
                }
            }
        };
        loadWorker.execute();
    }

    private void applyData(List<SubscriberStat> stats) {
        currentStatsList.clear();
        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        for (SubscriberStat stat : stats) {
            if (stat == null) {
                continue;
            }
            currentStatsList.add(stat);
            String dateStr = stat.getLastUpdated() != null ? sdf.format(stat.getLastUpdated()) : "–";
            tableModel.addRow(new Object[]{
                    stat.getSignalName(),
                    stat.getMqlVersion() != null ? stat.getMqlVersion().toUpperCase() : "–",
                    stat.getSubscribers(),
                    stat.getLatestChange(),
                    stat.getWeekChange(),
                    stat.getMonthChange(),
                    dateStr,
                    stat.getUrl()
            });
        }

        // Sort descending by Month column (5) by default, then Week column (4)
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(5, SortOrder.DESCENDING));
        sortKeys.add(new RowSorter.SortKey(4, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);

        statusLabel.setText("Gesamt Provider in Datenbank: " + currentStatsList.size());
    }

    @Override
    public void dispose() {
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        super.dispose();
    }

    private void filter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
        }
    }

    private void openHistoryChart(SubscriberStat stat) {
        SubscriberHistoryDialog historyDialog = new SubscriberHistoryDialog(this, stat, databaseManager);
        historyDialog.setVisible(true);
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

    // Cell Renderer for Change Columns
    private static class ChangeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean cellHasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, cellHasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setFont(table.getFont().deriveFont(Font.PLAIN));
            label.setToolTipText(null);

            if (value instanceof Integer) {
                int change = (Integer) value;
                if (change > 0) {
                    label.setText("+" + change);
                    label.setForeground(new Color(0, 110, 0));
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                } else if (change < 0) {
                    label.setText(String.valueOf(change));
                    label.setForeground(Color.RED);
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                } else {
                    label.setText("0");
                    label.setForeground(Color.GRAY);
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                }
            } else {
                label.setText("–");
                label.setForeground(isSelected ? table.getSelectionForeground() : Color.GRAY);
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                label.setToolTipText(UNKNOWN_CHANGE_TOOLTIP);
            }

            if (isSelected) {
                label.setForeground(table.getSelectionForeground());
            }

            return label;
        }
    }

    // Cell Renderer for URL Column
    private static class UrlCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean cellHasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, cellHasFocus, row, column);
            String url = value != null ? value.toString() : "";
            label.setText(url.isEmpty() ? "" : "\u2197 " + compactUrl(url));
            label.setToolTipText(url.isEmpty() ? null : url);
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (!isSelected) {
                label.setForeground(new Color(0, 102, 180));
            }
            return label;
        }

        private static String compactUrl(String value) {
            try {
                URI uri = new URI(value.trim());
                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    return "Link \u00f6ffnen";
                }
                return host.startsWith("www.") ? host.substring(4) : host;
            } catch (Exception ignored) {
                return "Link \u00f6ffnen";
            }
        }
    }

    // Cell Renderer for alignment
    private static class AlignmentCellRenderer extends DefaultTableCellRenderer {
        public AlignmentCellRenderer(int alignment) {
            setHorizontalAlignment(alignment);
        }
    }
}
