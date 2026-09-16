package gui;

import config.ConfigurationManager;
import database.DatabaseManager;
import database.SubscriberHistoryPoint;
import database.SubscriberStat;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SubscriberStatisticsDialog extends JDialog {
    private static final String UNKNOWN_CHANGE_TOOLTIP =
            "Kein belastbarer Vergleichswert für diesen Zeitraum vorhanden.";
    private static final Comparator<Integer> NULL_SAFE_INTEGER_COMPARATOR =
            Comparator.nullsFirst(Integer::compareTo);
    private static final int RISK_COLUMN = 3;
    private static final int SPARKLINE_COLUMN = 8;
    private static final int REPORTS_COLUMN = 9;
    private static final int URL_COLUMN = 10;

    // Stabile Farb-Schl\u00fcssel f\u00fcr die Datenbank und die zugeh\u00f6rigen Anzeigefarben
    private static final String COLOR_LIGHT_GREEN = "LIGHT_GREEN";
    private static final String COLOR_YELLOW = "YELLOW";
    private static final String COLOR_ORANGE = "ORANGE";
    private static final String COLOR_RED = "RED";
    private static final Color ROW_COLOR_LIGHT_GREEN = new Color(198, 239, 206);
    private static final Color ROW_COLOR_YELLOW = new Color(255, 235, 156);
    private static final Color ROW_COLOR_ORANGE = new Color(255, 204, 153);
    private static final Color ROW_COLOR_RED = new Color(255, 153, 153);

    private final DatabaseManager databaseManager;
    private final ConfigurationManager configManager;
    private final ExecutorService dbSaveExecutor = Executors.newSingleThreadExecutor();
    private JTable statsTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private String tableStatus = "Lade Daten...";
    private String validationMessage;
    private final Map<String, String> saveFailures = new LinkedHashMap<>();
    private JButton dismissSaveErrorsButton;
    private JButton refreshButton;
    private SwingWorker<StatsLoadResult, Void> loadWorker;
    private SwingWorker<Void, Void> closeWorker;
    private boolean closing;
    private boolean disposed;
    private boolean filterPending;
    private List<SubscriberStat> currentStatsList = new ArrayList<>();
    /** Zeilenfarben (Farb-Schl\u00fcssel oder null), parallel zu currentStatsList indiziert. */
    private final List<String> rowColorNames = new ArrayList<>();

    public SubscriberStatisticsDialog(JFrame parent, DatabaseManager databaseManager, ConfigurationManager configManager) {
        super(parent, "Abonnentenstatistik", true);
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        initializeComponents();
        loadData();
    }

    private void initializeComponents() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
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
                "Risiko",
                "Seit letzter Messung",
                "7 Tage",
                "30 Tage",
                "Letzte Messung",
                "30 Tage Verlauf",
                "Testreports",
                "URL"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == RISK_COLUMN; // nur Risiko manuell editierbar
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                super.setValueAt(aValue, row, column);
                if (column == RISK_COLUMN) {
                    persistRisk(row, aValue);
                }
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 2:
                    case 4:
                    case 5:
                    case 6:
                        return Integer.class;
                    case SPARKLINE_COLUMN:
                        return SparklineData.class;
                    case REPORTS_COLUMN:
                        return TestReports.class;
                    default:
                        return String.class;
                }
            }
        };

        statsTable = new RiskEditingTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int viewRow, int viewCol) {
                Component component = super.prepareRenderer(renderer, viewRow, viewCol);
                Color rowColor = rowColorForModelRow(convertRowIndexToModel(viewRow));
                if (rowColor != null && !isCellSelected(viewRow, viewCol)) {
                    component.setBackground(rowColor);
                }
                return component;
            }
        };
        statsTable.setFont(systemFont("Table.font", Font.PLAIN, 12f));
        statsTable.setRowHeight(36);
        statsTable.getTableHeader().setFont(systemFont("TableHeader.font", Font.BOLD, 12f));
        statsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        // Custom Comparators for proper numerical sorting
        sorter.setComparator(2, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(4, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(5, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(6, NULL_SAFE_INTEGER_COMPARATOR);
        sorter.setComparator(SPARKLINE_COLUMN, Comparator.nullsFirst(
                Comparator.comparingInt(SparklineData::netChange)));
        sorter.setComparator(REPORTS_COLUMN, Comparator.nullsFirst(
                Comparator.comparingInt(TestReports::count)));
        statsTable.setRowSorter(sorter);

        // Renderers. Wichtig: keine DefaultTableCellRenderer-Instanzen verwenden!
        // Deren setBackground() cacht die Farbe in "unselectedBackground", sodass sich
        // eine in prepareRenderer gesetzte Zeilenfarbe auf nachfolgende Zeilen ausbreitet.
        statsTable.getColumnModel().getColumn(0).setCellRenderer(new AlignedTextRenderer(SwingConstants.LEFT));
        statsTable.getColumnModel().getColumn(1).setCellRenderer(new AlignedTextRenderer(SwingConstants.CENTER));
        statsTable.getColumnModel().getColumn(2).setCellRenderer(new AlignedTextRenderer(SwingConstants.RIGHT));
        statsTable.getColumnModel().getColumn(RISK_COLUMN).setCellRenderer(new RiskTextRenderer());
        statsTable.getColumnModel().getColumn(4).setCellRenderer(new ChangeTextRenderer());
        statsTable.getColumnModel().getColumn(5).setCellRenderer(new ChangeTextRenderer());
        statsTable.getColumnModel().getColumn(6).setCellRenderer(new ChangeTextRenderer());
        statsTable.getColumnModel().getColumn(7).setCellRenderer(new AlignedTextRenderer(SwingConstants.LEFT));
        statsTable.getColumnModel().getColumn(SPARKLINE_COLUMN).setCellRenderer(new SparklineCellRenderer());
        statsTable.getColumnModel().getColumn(REPORTS_COLUMN).setCellRenderer(new ReportsCellRenderer());
        statsTable.getColumnModel().getColumn(URL_COLUMN).setCellRenderer(new UrlTextRenderer());

        // Risk editor: Doppelklick startet die Eingabe, Enter/Fokusverlust schließt ab
        RiskCellEditor riskEditor = new RiskCellEditor();
        riskEditor.setValidationListener(message -> {
            validationMessage = message;
            updateStatusLabel();
        });
        riskEditor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent event) {
                validationMessage = null;
                updateStatusLabel();
                applyPendingFilter();
            }

            @Override
            public void editingCanceled(ChangeEvent event) {
                validationMessage = null;
                updateStatusLabel();
                applyPendingFilter();
            }
        });
        statsTable.getColumnModel().getColumn(RISK_COLUMN).setCellEditor(riskEditor);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "discardRiskEdit");
        getRootPane().getActionMap().put("discardRiskEdit", new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return statsTable.isEditing() && statsTable.getCellEditor() instanceof RiskCellEditor;
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                if (isEnabled()) {
                    statsTable.getCellEditor().cancelCellEditing();
                }
            }
        });

        // Column widths
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(55);
        statsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        statsTable.getColumnModel().getColumn(RISK_COLUMN).setPreferredWidth(90);
        statsTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        statsTable.getColumnModel().getColumn(5).setPreferredWidth(85);
        statsTable.getColumnModel().getColumn(6).setPreferredWidth(85);
        statsTable.getColumnModel().getColumn(7).setPreferredWidth(135);
        statsTable.getColumnModel().getColumn(SPARKLINE_COLUMN).setPreferredWidth(143);
        statsTable.getColumnModel().getColumn(REPORTS_COLUMN).setPreferredWidth(85);
        statsTable.getColumnModel().getColumn(URL_COLUMN).setPreferredWidth(115);

        statsTable.addMouseListener(new SubscriberStatisticsClickHandler(statsTable,
                RISK_COLUMN, REPORTS_COLUMN, URL_COLUMN, new SubscriberStatisticsClickHandler.Actions() {
                    @Override
                    public void openUrl(int modelRow) {
                        if (modelRow < currentStatsList.size()) {
                            openUrlInBrowser(currentStatsList.get(modelRow).getUrl());
                        }
                    }

                    @Override
                    public void openReports(int viewRow, int viewColumn, int modelRow, Point click) {
                        openReportAt(viewRow, viewColumn, modelRow, click);
                    }

                    @Override
                    public void openHistory(int modelRow) {
                        if (modelRow < currentStatsList.size()) {
                            openHistoryChart(currentStatsList.get(modelRow));
                        }
                    }
                }));

        // Rechtsklick: Kontextmen\u00fc zum Setzen der Zeilenfarbe
        JPopupMenu rowColorPopup = new JPopupMenu();
        rowColorPopup.add(colorMenuItem("Hellgr\u00fcn", ROW_COLOR_LIGHT_GREEN, COLOR_LIGHT_GREEN));
        rowColorPopup.add(colorMenuItem("Gelb", ROW_COLOR_YELLOW, COLOR_YELLOW));
        rowColorPopup.add(colorMenuItem("Orange", ROW_COLOR_ORANGE, COLOR_ORANGE));
        rowColorPopup.add(colorMenuItem("Rot", ROW_COLOR_RED, COLOR_RED));
        rowColorPopup.addSeparator();
        JMenuItem noColorItem = new JMenuItem("Keine Farbe");
        noColorItem.setToolTipText("Markierung der Zeile entfernen");
        noColorItem.addActionListener(e -> applyRowColor(null));
        rowColorPopup.add(noColorItem);
        statsTable.setComponentPopupMenu(rowColorPopup);
        statsTable.addMouseListener(new MouseAdapter() {
            private void selectRowAt(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int viewRow = statsTable.rowAtPoint(e.getPoint());
                    if (viewRow != -1 && !statsTable.isRowSelected(viewRow)) {
                        statsTable.setRowSelectionInterval(viewRow, viewRow);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                selectRowAt(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectRowAt(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        statusLabel = new JLabel("Lade Daten...");
        statusLabel.setFont(systemFont("Label.font", Font.ITALIC, 12f));
        dismissSaveErrorsButton = new JButton("Fehler quittieren");
        dismissSaveErrorsButton.setVisible(false);
        dismissSaveErrorsButton.setToolTipText(
                "Blendet Speicherfehler aus. Fehlgeschlagene Änderungen werden dadurch nicht gespeichert.");
        dismissSaveErrorsButton.addActionListener(event -> {
            saveFailures.clear();
            updateStatusLabel();
        });
        JPanel statusPanel = new JPanel(new BorderLayout(6, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(dismissSaveErrorsButton, BorderLayout.EAST);

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

        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(1180, 600);
        setLocationRelativeTo(getParent());
    }

    public void loadData() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::loadData);
            return;
        }

        if (disposed || closing || !commitRiskEditing()) {
            return;
        }

        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }

        tableModel.setRowCount(0);
        currentStatsList.clear();
        rowColorNames.clear();
        setStatus("Lade Daten...");
        refreshButton.setEnabled(false);

        if (databaseManager == null) {
            setStatus("Keine Datenbankverbindung verf\u00fcgbar.");
            refreshButton.setEnabled(true);
            return;
        }

        // Queue the barrier on the EDT, after the editor has submitted its final value.
        // Waiting happens in the worker so a slow database never blocks the UI.
        Future<?> pendingSaves = dbSaveExecutor.submit(() -> { });
        loadWorker = new SwingWorker<StatsLoadResult, Void>() {
            @Override
            protected StatsLoadResult doInBackground() throws InterruptedException, ExecutionException {
                pendingSaves.get();
                List<SubscriberStat> stats = databaseManager.getAllSubscriberStatistics();
                Map<String, List<SubscriberHistoryPoint>> histories = databaseManager.getAllSubscriberHistories();
                ReportFiles.ScanResult reports = scanAnalyseReports(stats != null ? stats : Collections.<SubscriberStat>emptyList());
                return new StatsLoadResult(
                        stats != null ? new ArrayList<>(stats) : new ArrayList<>(),
                        histories != null ? histories : Collections.emptyMap(),
                        reports.reports, reports.warning);
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
                    setStatus("Laden wurde unterbrochen.");
                } catch (CancellationException ignored) {
                    // A newer refresh replaced this request.
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setStatus("Daten konnten nicht geladen werden: " + safeMessage(cause));
                } catch (RuntimeException ex) {
                    setStatus("Daten konnten nicht angezeigt werden: " + safeMessage(ex));
                }
            }
        };
        loadWorker.execute();
    }

    private void applyData(StatsLoadResult result) {
        currentStatsList.clear();
        rowColorNames.clear();
        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        Map<String, List<SubscriberHistoryPoint>> histories =
                result.histories != null ? result.histories : Collections.emptyMap();

        for (int i = 0; i < result.stats.size(); i++) {
            SubscriberStat stat = result.stats.get(i);
            if (stat == null) {
                continue;
            }
            currentStatsList.add(stat);
            rowColorNames.add(stat.getRowColor());
            String dateStr = stat.getLastUpdated() != null ? sdf.format(stat.getLastUpdated()) : "–";
            String key = DatabaseManager.subscriberHistoryKey(stat.getSignalId(), stat.getMqlVersion());
            List<SubscriberHistoryPoint> history = histories.getOrDefault(key, Collections.emptyList());
            TestReports reports = result.reports != null && i < result.reports.size()
                    ? result.reports.get(i) : TestReports.EMPTY;
            tableModel.addRow(new Object[]{
                    stat.getSignalName(),
                    stat.getMqlVersion() != null ? stat.getMqlVersion().toUpperCase(Locale.ROOT) : "–",
                    stat.getSubscribers(),
                    stat.getRisk() != null ? stat.getRisk() : "",
                    stat.getLatestChange(),
                    stat.getWeekChange(),
                    stat.getMonthChange(),
                    dateStr,
                    SparklineData.fromLast30Days(history, stat.getSubscribers(), stat.getLastUpdated()),
                    reports,
                    stat.getUrl()
            });
        }

        // Sort descending by Month column (6) by default, then Week column (5)
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(6, SortOrder.DESCENDING));
        sortKeys.add(new RowSorter.SortKey(5, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);

        String status = "Gesamt Provider in Datenbank: " + currentStatsList.size();
        if (result.reportWarning != null) {
            status += "  |  " + result.reportWarning;
        }
        setStatus(status);
    }

    @Override
    public void dispose() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::dispose);
            return;
        }
        if (disposed || closing || !commitRiskEditing()) {
            return;
        }
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }
        loadWorker = null;
        setClosing(true);
        setStatus("Speichere \u00c4nderungen\u2026");

        Future<?> pendingSaves = dbSaveExecutor.submit(() -> { });
        closeWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws InterruptedException, ExecutionException, InvocationTargetException {
                pendingSaves.get();
                // SwingWorker batches done() callbacks and may overtake invokeLater().
                // Process the preceding save-result callbacks before deciding to close.
                SwingUtilities.invokeAndWait(() -> { });
                return null;
            }

            @Override
            protected void done() {
                if (this != closeWorker) {
                    return;
                }
                closeWorker = null;
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setClosing(false);
                    setStatus("Warten auf das Speichern wurde unterbrochen.");
                    return;
                } catch (CancellationException ex) {
                    setClosing(false);
                    setStatus("Schlie\u00dfen wurde abgebrochen.");
                    return;
                } catch (ExecutionException ex) {
                    setClosing(false);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setStatus("Speichern konnte nicht abgeschlossen werden: " + safeMessage(cause));
                    return;
                }

                // The worker waited for both the save queue and its EDT result callbacks.
                // Keep the dialog available for a retry or an explicit acknowledgement.
                if (!saveFailures.isEmpty()) {
                    setClosing(false);
                    setStatus("Dialog bleibt wegen Speicherfehlern ge\u00f6ffnet.");
                    return;
                }
                disposed = true;
                dbSaveExecutor.shutdown();
                SubscriberStatisticsDialog.super.dispose();
            }
        };
        closeWorker.execute();
    }

    private void setClosing(boolean closing) {
        this.closing = closing;
        boolean editable = !closing && !disposed;
        statsTable.setEnabled(editable);
        searchField.setEnabled(editable);
        refreshButton.setEnabled(editable && (loadWorker == null || loadWorker.isDone()));
    }

    private void filter() {
        if (disposed || closing) {
            return;
        }
        if (!commitRiskEditing()) {
            filterPending = true;
            return;
        }
        filterPending = false;
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
        }
    }

    private void applyPendingFilter() {
        if (filterPending) {
            SwingUtilities.invokeLater(() -> {
                if (filterPending) {
                    filter();
                }
            });
        }
    }

    private boolean commitRiskEditing() {
        boolean committed = RiskCellEditor.commitEditing(statsTable);
        if (committed) {
            validationMessage = null;
        } else if (statsTable.getCellEditor() instanceof RiskCellEditor) {
            validationMessage = ((RiskCellEditor) statsTable.getCellEditor()).getValidationMessage();
        }
        updateStatusLabel();
        return committed;
    }

    /**
     * Sucht im konfigurierten Analyse-Verzeichnis je Signal PDF-Dateien, deren
     * Dateiname die exakte Signal-ID enth\u00e4lt (als eigene Zifferngruppe).
     * Die R\u00fcckgabe ist parallel zur \u00fcbergebenen Statistik-Liste indiziert.
     */
    private ReportFiles.ScanResult scanAnalyseReports(List<SubscriberStat> stats) {
        String analyseDirPath = configManager != null ? configManager.getAnalysePath() : "";
        return ReportFiles.scan(analyseDirPath, stats);
    }

    /** Verwendet dieselben Icon- und Men\u00fcgrenzen wie der Renderer. */
    private void openReportAt(int viewRow, int viewCol, int modelRow, Point click) {
        Object value = tableModel.getValueAt(modelRow, REPORTS_COLUMN);
        if (!(value instanceof TestReports)) {
            return;
        }
        List<Path> files = ((TestReports) value).getFiles();
        Rectangle cellRect = statsTable.getCellRect(viewRow, viewCol, false);
        ReportCellLayout layout = ReportCellLayout.forCell(cellRect.width, cellRect.height, files.size(),
                statsTable.getFontMetrics(ReportCellLayout.badgeFont(statsTable.getFont())));
        int index = layout.hitAt(click.x - cellRect.x, click.y - cellRect.y);
        if (index >= 0) {
            openPdfFile(files.get(index));
        } else if (index == ReportCellLayout.OVERFLOW) {
            JPopupMenu menu = new JPopupMenu();
            JPopupMenu page = menu;
            for (int i = layout.visibleCount; i < files.size(); i++) {
                // Keep every entry reachable even when a signal has many reports.
                if (i > layout.visibleCount && (i - layout.visibleCount) % 15 == 0) {
                    JMenu more = new JMenu("Weitere Reports (" + (files.size() - i) + ")");
                    page.add(more);
                    page = more.getPopupMenu();
                }
                Path file = files.get(i);
                JMenuItem item = new JMenuItem(file.getFileName().toString());
                item.addActionListener(event -> openPdfFile(file));
                page.add(item);
            }
            menu.show(statsTable, cellRect.x + layout.overflowBounds.x,
                    cellRect.y + layout.overflowBounds.y + layout.overflowBounds.height);
        }
    }

    private void openPdfFile(Path pdfPath) {
        try {
            File file = pdfPath.toFile();
            if (!file.isFile()) {
                JOptionPane.showMessageDialog(this,
                        "Datei wurde nicht gefunden:\n" + file.getAbsolutePath(),
                        "Testreport \u00f6ffnen", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
            } else {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", file.getAbsolutePath()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Fehler beim \u00d6ffnen des Testreports: " + safeMessage(ex),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void persistRisk(int modelRow, Object value) {
        if (disposed || closing || modelRow < 0 || modelRow >= currentStatsList.size()) {
            return;
        }
        SubscriberStat stat = currentStatsList.get(modelRow);
        if (stat == null) {
            return;
        }
        String newRisk = value != null ? value.toString().trim() : "";
        // Queue every completed edit: the loaded snapshot does not include pending saves.
        dbSaveExecutor.submit(() -> {
            try {
                boolean saved = databaseManager.updateSignalRisk(stat.getSignalId(), stat.getMqlVersion(), newRisk);
                reportSaveResult("Risiko", stat, saved, null);
            } catch (RuntimeException ex) {
                reportSaveResult("Risiko", stat, false, ex);
            }
        });
    }

    private void reportSaveResult(String fieldName, SubscriberStat stat, boolean saved, RuntimeException cause) {
        String key = fieldName + ":" + DatabaseManager.subscriberHistoryKey(stat.getSignalId(), stat.getMqlVersion());
        SwingUtilities.invokeLater(() -> {
            if (saved) {
                saveFailures.remove(key);
            } else {
                String message = fieldName + " konnte nicht gespeichert werden (Signal " + stat.getSignalId()
                        + ", " + stat.getMqlVersion() + ").";
                if (cause != null) {
                    message += " " + safeMessage(cause);
                }
                saveFailures.put(key, message);
            }
            updateStatusLabel();
        });
    }

    private void setStatus(String status) {
        tableStatus = status;
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        if (statusLabel == null) {
            return;
        }
        // A refresh must not immediately hide a failure from a preceding save.
        String failures = String.join("  |  ", saveFailures.values());
        String status = failures.isEmpty() ? tableStatus : failures + "  |  " + tableStatus;
        if (validationMessage != null) {
            status = validationMessage + "  |  " + status;
        }
        statusLabel.setText(status);
        statusLabel.setToolTipText(status);
        if (dismissSaveErrorsButton != null) {
            dismissSaveErrorsButton.setVisible(!saveFailures.isEmpty());
            dismissSaveErrorsButton.setEnabled(!closing);
        }
    }

    /** L\u00f6st den Farb-Schl\u00fcssel einer Modellzeile in die Anzeigefarbe auf (oder null). */
    private Color rowColorForModelRow(int modelRow) {
        if (modelRow < 0 || modelRow >= rowColorNames.size()) {
            return null;
        }
        String name = rowColorNames.get(modelRow);
        if (name == null) {
            return null;
        }
        switch (name) {
            case COLOR_LIGHT_GREEN: return ROW_COLOR_LIGHT_GREEN;
            case COLOR_YELLOW: return ROW_COLOR_YELLOW;
            case COLOR_ORANGE: return ROW_COLOR_ORANGE;
            case COLOR_RED: return ROW_COLOR_RED;
            default: return null;
        }
    }

    /** Setzt die Zeilenfarbe der ausgew\u00e4hlten Zeile und speichert sie (null = entfernen). */
    private void applyRowColor(String colorKey) {
        if (disposed || closing) {
            return;
        }
        int viewRow = statsTable.getSelectedRow();
        if (viewRow == -1) {
            return;
        }
        int modelRow = statsTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentStatsList.size() || modelRow >= rowColorNames.size()) {
            return;
        }
        rowColorNames.set(modelRow, colorKey);
        SubscriberStat stat = currentStatsList.get(modelRow);
        dbSaveExecutor.submit(() -> {
            try {
                boolean saved = databaseManager.updateRowColor(stat.getSignalId(), stat.getMqlVersion(), colorKey);
                reportSaveResult("Zeilenfarbe", stat, saved, null);
            } catch (RuntimeException ex) {
                reportSaveResult("Zeilenfarbe", stat, false, ex);
            }
        });
        statsTable.repaint();
    }

    private JMenuItem colorMenuItem(String text, Color color, String colorKey) {
        JMenuItem item = new JMenuItem(text, colorIcon(color));
        item.addActionListener(e -> applyRowColor(colorKey));
        return item;
    }

    private static Icon colorIcon(Color color) {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(color);
            g2.fillRect(0, 0, 12, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(0, 0, 11, 11);
        } finally {
            g2.dispose();
        }
        return new ImageIcon(image);
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

    private static final class StatsLoadResult {
        private final List<SubscriberStat> stats;
        private final Map<String, List<SubscriberHistoryPoint>> histories;
        private final List<TestReports> reports;
        private final String reportWarning;

        private StatsLoadResult(List<SubscriberStat> stats, Map<String, List<SubscriberHistoryPoint>> histories,
                                List<TestReports> reports, String reportWarning) {
            this.stats = stats;
            this.histories = histories;
            this.reports = reports;
            this.reportWarning = reportWarning;
        }
    }

    /** Im Analyse-Verzeichnis gefundene Testreport-PDFs zu einem Signal. */
    static final class TestReports {
        static final TestReports EMPTY = new TestReports(Collections.emptyList());

        private final List<Path> files;

        TestReports(List<Path> files) {
            this.files = files != null ? files : Collections.<Path>emptyList();
        }

        List<Path> getFiles() {
            return files;
        }

        int count() {
            return files.size();
        }
    }

    /** Zeichnet je gefundener Testreport-PDF ein Klickbares PDF-Icon. */
    private static class ReportsCellRenderer extends JComponent implements TableCellRenderer {
        private TestReports reports = TestReports.EMPTY;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            this.reports = value instanceof TestReports ? (TestReports) value : TestReports.EMPTY;
            setFont(table.getFont());
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            // Hintergrund live lesen (getBackground), damit prepareRenderer die Zeilenfarbe setzen kann
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            setToolTipText(buildTooltip());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return this;
        }

        private String buildTooltip() {
            if (reports.count() == 0) {
                return null;
            }
            StringBuilder tip = new StringBuilder("<html><b>Testreport(s) \u00f6ffnen:</b>");
            for (Path file : reports.getFiles()) {
                tip.append("<br>&nbsp;&bull;&nbsp;")
                   .append(escapeHtml(file.getFileName().toString()));
            }
            tip.append("<br><i>Icon: PDF \u00f6ffnen. +N: weitere Reports ausw\u00e4hlen.</i></html>");
            return tip.toString();
        }

        private static String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (reports.count() == 0) {
                    return;
                }
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Font badgeFont = ReportCellLayout.badgeFont(getFont());
                ReportCellLayout layout = ReportCellLayout.forCell(getWidth(), getHeight(), reports.count(),
                        getFontMetrics(badgeFont));
                for (int i = 0; i < layout.visibleCount; i++) {
                    Rectangle icon = layout.iconBounds(i);
                    drawPdfIcon(g2, icon.x, icon.y);
                }
                if (layout.overflowBounds != null) {
                    Rectangle badge = layout.overflowBounds;
                    g2.setColor(new Color(0, 0, 0, 20));
                    g2.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 6, 6);
                    g2.setColor(getForeground());
                    g2.drawRoundRect(badge.x, badge.y, badge.width - 1, badge.height - 1, 6, 6);
                    g2.setFont(badgeFont);
                    FontMetrics metrics = g2.getFontMetrics();
                    if (badge.height >= metrics.getHeight()) {
                        g2.drawString(layout.overflowText,
                                badge.x + (badge.width - metrics.stringWidth(layout.overflowText)) / 2,
                                badge.y + (badge.height - metrics.getHeight()) / 2 + metrics.getAscent());
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawPdfIcon(Graphics2D g2, int x, int y) {
            int iconWidth = ReportCellLayout.ICON_WIDTH;
            int iconHeight = ReportCellLayout.ICON_HEIGHT;
            // weiße Dokumentseite
            g2.setColor(Color.WHITE);
            g2.fillRect(x, y, iconWidth, iconHeight);
            g2.setColor(new Color(120, 120, 120));
            g2.drawRect(x, y, iconWidth - 1, iconHeight - 1);
            // angedeutete Textzeilen
            g2.setColor(new Color(175, 175, 175));
            for (int line = 0; line < 3; line++) {
                int lineY = y + 4 + line * 3;
                g2.drawLine(x + 3, lineY, x + iconWidth - 3, lineY);
            }
            // rotes PDF-Band
            g2.setColor(new Color(200, 35, 35));
            g2.fillRect(x, y + iconHeight - 8, iconWidth, 8);
            g2.setColor(Color.WHITE);
            Font originalFont = g2.getFont();
            g2.setFont(originalFont.deriveFont(Font.BOLD, 6.5f));
            FontMetrics fm = g2.getFontMetrics();
            String label = "PDF";
            g2.drawString(label, x + (iconWidth - fm.stringWidth(label)) / 2, y + iconHeight - 2);
            g2.setFont(originalFont);
        }
    }

    /** Compact subscriber series for the 30-day sparkline column. */
    static final class SparklineData {
        private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

        private final int[] values;
        /** Epoch millis parallel to values; empty when only a fallback point exists. */
        private final long[] times;

        private SparklineData(int[] values, long[] times) {
            this.values = values != null ? values : new int[0];
            this.times = times != null ? times : new long[0];
        }

        static SparklineData fromLast30Days(List<SubscriberHistoryPoint> history, int fallbackSubscribers,
                                           java.sql.Timestamp lastUpdated) {
            if (history == null || history.isEmpty()) {
                return new SparklineData(new int[]{fallbackSubscribers}, new long[0]);
            }

            long referenceMs = lastUpdated != null
                    ? lastUpdated.getTime()
                    : System.currentTimeMillis();
            for (int i = history.size() - 1; i >= 0; i--) {
                SubscriberHistoryPoint point = history.get(i);
                if (point != null && point.getTimestamp() != null) {
                    referenceMs = Math.max(referenceMs, point.getTimestamp().getTime());
                    break;
                }
            }
            long cutoffMs = referenceMs - THIRTY_DAYS_MS;

            List<Integer> filteredValues = new ArrayList<>();
            List<Long> filteredTimes = new ArrayList<>();
            for (SubscriberHistoryPoint point : history) {
                if (point == null || point.getTimestamp() == null) {
                    continue;
                }
                long timeMs = point.getTimestamp().getTime();
                if (timeMs >= cutoffMs) {
                    filteredValues.add(point.getSubscribers());
                    filteredTimes.add(timeMs);
                }
            }
            if (filteredValues.isEmpty()) {
                return new SparklineData(new int[]{fallbackSubscribers}, new long[0]);
            }
            int[] values = new int[filteredValues.size()];
            long[] times = new long[filteredTimes.size()];
            for (int i = 0; i < filteredValues.size(); i++) {
                values[i] = filteredValues.get(i);
                times[i] = filteredTimes.get(i);
            }
            return new SparklineData(values, times);
        }

        int netChange() {
            if (values.length < 2) {
                return 0;
            }
            return values[values.length - 1] - values[0];
        }

        int pointCount() {
            return values.length;
        }

        boolean hasTimeAxis() {
            return times.length == values.length && times.length >= 2;
        }
    }

    // Cell Renderer for Change Columns
    /** Basis für Textspalten: Selektion und Zebra-Streifen, ohne Hintergrund-Cache. */
    private abstract static class TextCellRenderer extends JLabel implements TableCellRenderer {
        TextCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int viewRow, int column) {
            setText(value != null ? value.toString() : "");
            setFont(table.getFont());

            Color background;
            Color foreground;
            if (isSelected) {
                background = table.getSelectionBackground();
                foreground = table.getSelectionForeground();
            } else {
                Color alternate = UIManager.getColor("Table.alternateRowColor");
                background = viewRow % 2 == 1 && alternate != null ? alternate : table.getBackground();
                foreground = table.getForeground();
            }
            setBackground(background);
            setForeground(foreground);
            style(table, value, isSelected);
            return this;
        }

        /** Spaltenspezifische Ausrichtung, Text und Tooltip. */
        protected void style(JTable table, Object value, boolean isSelected) {
        }
    }

    /** Text-Renderer mit fester Ausrichtung. */
    private static final class AlignedTextRenderer extends TextCellRenderer {
        private final int alignment;

        AlignedTextRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        protected void style(JTable table, Object value, boolean isSelected) {
            setHorizontalAlignment(alignment);
        }
    }

    /** Risiko-Spalte: linksbündig mit Bearbeitungs-Hinweis. */
    private static final class RiskTextRenderer extends TextCellRenderer {
        @Override
        protected void style(JTable table, Object value, boolean isSelected) {
            setHorizontalAlignment(SwingConstants.LEFT);
            setToolTipText("Doppelklick: Risiko-Wert eingeben (maximal 200 Zeichen, wird dauerhaft gespeichert)");
        }
    }

    /** Renderer für die Änderungs-Spalten (+grün / -rot / 0 grau / – ohne Vergleichswert). */
    private static final class ChangeTextRenderer extends TextCellRenderer {
        @Override
        protected void style(JTable table, Object value, boolean isSelected) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(getFont().deriveFont(Font.PLAIN));
            setToolTipText(null);
            if (value instanceof Integer) {
                int change = (Integer) value;
                if (change > 0) {
                    setText("+" + change);
                    setForeground(new Color(0, 110, 0));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (change < 0) {
                    setText(String.valueOf(change));
                    setForeground(Color.RED);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setText("0");
                    setForeground(Color.GRAY);
                }
            } else {
                setText("–");
                setForeground(Color.GRAY);
                setToolTipText(UNKNOWN_CHANGE_TOOLTIP);
            }
            if (isSelected) {
                setForeground(table.getSelectionForeground());
            }
        }
    }

    /** URL-Spalte: kompakter Link mit Tooltip und Hand-Cursor. */
    private static final class UrlTextRenderer extends TextCellRenderer {
        @Override
        protected void style(JTable table, Object value, boolean isSelected) {
            String url = value != null ? value.toString() : "";
            setText(url.isEmpty() ? "" : "\u2197 " + compactUrl(url));
            setToolTipText(url.isEmpty() ? null : url);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (!isSelected) {
                setForeground(new Color(0, 102, 180));
            }
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

    /** Draws a compact subscriber sparkline in the Verlauf column. */
    private static class SparklineCellRenderer implements TableCellRenderer {
        private static final Color UP_COLOR = new Color(0, 128, 0);
        private static final Color DOWN_COLOR = new Color(200, 40, 40);
        private static final Color FLAT_COLOR = new Color(90, 90, 90);
        private static final Stroke LINE_STROKE = new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        private final SparklinePanel panel = new SparklinePanel();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean cellHasFocus, int row, int column) {
            Color background = isSelected ? table.getSelectionBackground() : table.getBackground();
            Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
            panel.setBackground(background);
            panel.setForeground(foreground);
            panel.setOpaque(true);
            panel.setSelected(isSelected);

            if (value instanceof SparklineData) {
                SparklineData data = (SparklineData) value;
                panel.setData(data);
                int change = data.netChange();
                String tip = "30 Tage: " + data.pointCount() + " Messpunkte";
                if (data.pointCount() >= 2) {
                    tip += ", \u00c4nderung: " + (change >= 0 ? "+" : "") + change;
                }
                tip += " (Doppelklick: gro\u00dfe Verlaufskurve)";
                panel.setToolTipText(tip);
            } else {
                panel.setData(null);
                panel.setToolTipText(null);
            }
            return panel;
        }

        private static class SparklinePanel extends JPanel {
            private SparklineData data;
            private boolean selected;

            void setData(SparklineData data) {
                this.data = data;
            }

            void setSelected(boolean selected) {
                this.selected = selected;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (data == null || data.values.length == 0) {
                    drawPlaceholder(g);
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int padX = 5;
                    int padY = 4;
                    int width = getWidth() - padX * 2;
                    int height = getHeight() - padY * 2;
                    if (width < 4 || height < 4) {
                        return;
                    }

                    int[] values = data.values;
                    int min = values[0];
                    int max = values[0];
                    for (int value : values) {
                        if (value < min) min = value;
                        if (value > max) max = value;
                    }

                    Color lineColor;
                    int net = data.netChange();
                    if (net > 0) {
                        lineColor = UP_COLOR;
                    } else if (net < 0) {
                        lineColor = DOWN_COLOR;
                    } else {
                        lineColor = FLAT_COLOR;
                    }
                    if (selected) {
                        lineColor = brighterForSelection(lineColor);
                    }

                    g2.setColor(lineColor);
                    g2.setStroke(LINE_STROKE);

                    if (values.length == 1 || min == max) {
                        int y = padY + height / 2;
                        g2.drawLine(padX, y, padX + width, y);
                        return;
                    }

                    double range = max - min;
                    boolean useTime = data.hasTimeAxis();
                    long timeStart = useTime ? data.times[0] : 0L;
                    long timeSpan = useTime ? Math.max(1L, data.times[data.times.length - 1] - timeStart) : 1L;

                    Path2D.Float path = new Path2D.Float();
                    for (int i = 0; i < values.length; i++) {
                        float x;
                        if (useTime) {
                            x = padX + (float) (data.times[i] - timeStart) / timeSpan * width;
                        } else {
                            x = padX + (float) i / (values.length - 1) * width;
                        }
                        float y = padY + height - (float) ((values[i] - min) / range * height);
                        if (i == 0) {
                            path.moveTo(x, y);
                        } else {
                            path.lineTo(x, y);
                        }
                    }
                    g2.draw(path);
                } finally {
                    g2.dispose();
                }
            }

            private void drawPlaceholder(Graphics g) {
                g.setColor(Color.GRAY);
                FontMetrics fm = g.getFontMetrics();
                String text = "–";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(text, x, y);
            }

            private static Color brighterForSelection(Color color) {
                return new Color(
                        Math.min(255, color.getRed() + 40),
                        Math.min(255, color.getGreen() + 40),
                        Math.min(255, color.getBlue() + 40));
            }
        }
    }
}
