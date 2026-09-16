package gui;

import database.DatabaseManager;
import database.SubscriberStat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SubscriberStatisticsDialogTest {
    private static final int RISK_COLUMN = 3;
    private static final long TIMEOUT_SECONDS = 10;

    @TempDir
    Path tempDirectory;

    private ControlledDatabaseManager database;
    private SubscriberStatisticsDialog dialog;
    private JTable table;
    private ExecutorService saves;
    private CountDownLatch releaseSaves;
    private Future<?> pausedSave;

    @BeforeEach
    void requireGraphicsEnvironment() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "JDialog requires a graphics environment");
    }

    @AfterEach
    void closeDialogAndDatabase() throws Exception {
        if (releaseSaves != null) {
            releaseSaves.countDown();
        }
        if (dialog != null) {
            Future<?> remainingSaves = onEdt(() -> saves.isShutdown() ? null : saves.submit(() -> { }));
            if (remainingSaves != null) {
                remainingSaves.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            CloseObservation cleanup = onEdt(() -> {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }
                // Tests of failed writes acknowledge their errors during cleanup.
                field("saveFailures", Map.class).clear();
                dialog.dispose();
                return observeClose();
            });
            awaitCloseAttempt(cleanup);
        }
        if (saves != null) {
            assertTrue(saves.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "The dialog's save executor must terminate");
        }
        if (Files.exists(tempDirectory.resolve("config/subscribers.mv.db"))) {
            String databasePath = tempDirectory.resolve("config/subscribers").toString().replace('\\', '/');
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + databasePath + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE", "sa", "");
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void clearsRiskAfterTwoEditsQueuedBeforeEitherWriteCompletes() throws Exception {
        openDialog(null);
        pauseSaves();

        onEdt(() -> {
            table.getModel().setValueAt("hoch", 0, RISK_COLUMN);
            table.getModel().setValueAt("", 0, RISK_COLUMN);
            return null;
        });
        resumeSaves();
        awaitSaves();

        assertNull(savedStat().getRisk());
        assertEquals("", onEdt(() -> table.getValueAt(0, RISK_COLUMN)));
    }

    @Test
    void restoresInitialRiskAfterTwoEditsQueuedBeforeEitherWriteCompletes() throws Exception {
        openDialog("alt");
        pauseSaves();

        onEdt(() -> {
            table.getModel().setValueAt("neu", 0, RISK_COLUMN);
            table.getModel().setValueAt("alt", 0, RISK_COLUMN);
            return null;
        });
        resumeSaves();
        awaitSaves();

        assertEquals("alt", savedStat().getRisk());
        assertEquals("alt", onEdt(() -> table.getValueAt(0, RISK_COLUMN)));
    }

    @Test
    void refreshCommitsTheActiveEditorAndWaitsForItsQueuedWrite() throws Exception {
        openDialog("alt");
        pauseSaves();
        beginRiskEdit("neu");
        int readsBeforeRefresh = database.statisticsReads.get();

        LoadObservation refresh = reload();

        assertFalse(onEdt(() -> table.isEditing()));
        assertEquals(0, onEdt(() -> table.getRowCount()));
        assertThrows(TimeoutException.class, () -> refresh.worker.get(200, TimeUnit.MILLISECONDS));
        assertEquals(readsBeforeRefresh, database.statisticsReads.get(),
                "Refresh must not read statistics while the earlier write is queued");

        resumeSaves();
        awaitLoaded(refresh);
        assertEquals("neu", savedStat().getRisk());
        assertEquals("neu", onEdt(() -> table.getValueAt(0, RISK_COLUMN)));
    }

    @Test
    void disposeWaitsForTheActiveEditBeforeClosingAndReopening() throws Exception {
        assertCloseSavesEditor(SubscriberStatisticsDialog::dispose);
    }

    @Test
    void windowClosingWaitsForTheActiveEditBeforeClosingAndReopening() throws Exception {
        assertCloseSavesEditor(window -> window.dispatchEvent(
                new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
    }

    @Test
    void colorSaveFailureSurvivesRefreshAndClearsAfterSuccessfulRetry() throws Exception {
        openDialog(null);
        database.rejectColorUpdates = true;

        applyRowColor("RED");
        awaitSaves();
        assertTrue(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));
        assertNull(savedStat().getRowColor());

        awaitLoaded(reload());
        assertTrue(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));

        database.rejectColorUpdates = false;
        applyRowColor("RED");
        awaitSaves();
        assertEquals("RED", savedStat().getRowColor());
        assertFalse(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));
    }

    @Test
    void unexpectedColorSaveFailureIsShownInTheStatus() throws Exception {
        openDialog(null);
        database.colorFailure = new IllegalStateException("Simulierter Speicherfehler");

        applyRowColor("RED");
        awaitSaves();

        assertTrue(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));
        assertTrue(status().contains("Simulierter Speicherfehler"));
        assertNull(savedStat().getRowColor());
    }

    @Test
    void failedSaveKeepsTheDialogOpenUntilARepeatedEditSavesSuccessfully() throws Exception {
        openDialog("alt");
        database.rejectRiskUpdates = true;
        beginRiskEdit("neu");

        awaitCloseAttempt(requestClose(SubscriberStatisticsDialog::dispose));

        assertTrue(onEdt(() -> dialog.isDisplayable()));
        assertFalse(onEdt(() -> field("closing", Boolean.class)));
        assertTrue(onEdt(() -> table.isEnabled()));
        assertTrue(onEdt(() -> field("searchField", JTextField.class).isEnabled()));
        assertTrue(onEdt(() -> field("refreshButton", JButton.class).isEnabled()));
        assertFalse(saves.isShutdown());
        assertTrue(status().contains("Risiko konnte nicht gespeichert werden"));
        assertEquals("alt", savedStat().getRisk());

        database.rejectRiskUpdates = false;
        beginRiskEdit("neu");
        awaitCloseAttempt(requestClose(SubscriberStatisticsDialog::dispose));

        assertFalse(onEdt(() -> dialog.isDisplayable()));
        assertTrue(saves.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("neu", savedStat().getRisk());
    }

    @Test
    void acknowledgingASaveFailureAllowsClosingWithoutPretendingTheValueWasSaved() throws Exception {
        openDialog(null);
        database.rejectColorUpdates = true;
        applyRowColor("RED");
        awaitSaves();

        awaitCloseAttempt(requestClose(SubscriberStatisticsDialog::dispose));
        assertTrue(onEdt(() -> dialog.isDisplayable()));
        assertFalse(saves.isShutdown());

        onEdt(() -> {
            field("dismissSaveErrorsButton", JButton.class).doClick();
            return null;
        });
        assertFalse(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));
        assertNull(savedStat().getRowColor());

        awaitCloseAttempt(requestClose(SubscriberStatisticsDialog::dispose));
        assertFalse(onEdt(() -> dialog.isDisplayable()));
        assertTrue(saves.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNull(savedStat().getRowColor());
    }

    @Test
    void closeWaitsForSaveFailureCallbacksEvenWhenTheEdtIsBusy() throws Exception {
        openDialog(null);
        database.rejectColorUpdates = true;
        pauseSaves();
        applyRowColor("RED");

        CountDownLatch releaseEdt = new CountDownLatch(1);
        CompletableFuture<CloseObservation> closeStarted = new CompletableFuture<>();
        FutureTask<Void> busyEdt = new FutureTask<>(() -> {
            dialog.dispose();
            closeStarted.complete(observeClose());
            assertTrue(releaseEdt.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return null;
        });
        SwingUtilities.invokeLater(busyEdt);
        CloseObservation close;
        try {
            close = closeStarted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            resumeSaves();
            // The write has finished, but its invokeLater result cannot run yet.
            saves.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> close.worker.get(200, TimeUnit.MILLISECONDS),
                    "The close worker must wait for the save result to be applied on the EDT");
        } finally {
            releaseEdt.countDown();
        }
        busyEdt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        awaitCloseAttempt(close);
        assertTrue(onEdt(() -> dialog.isDisplayable()));
        assertFalse(saves.isShutdown());
        assertTrue(status().contains("Zeilenfarbe konnte nicht gespeichert werden"));
        assertNull(savedStat().getRowColor());
    }

    @Test
    void invalidRiskBlocksRefreshAndCloseUntilTheGlobalEscapeActionDiscardsIt() throws Exception {
        openDialog("alt");
        String invalidRisk = new String(new char[201]).replace('\0', 'x');
        beginRiskEdit(invalidRisk);
        SwingWorker<?, ?> initialLoad = onEdt(() -> field("loadWorker", SwingWorker.class));

        onEdt(() -> {
            dialog.loadData();
            dialog.dispose();
            assertSame(initialLoad, field("loadWorker", SwingWorker.class));
            assertNull(field("closeWorker", SwingWorker.class));
            assertTrue(table.isEditing());
            return null;
        });
        assertTrue(status().contains("200"));
        assertTrue(status().contains("201"));
        assertFalse(saves.isShutdown());
        assertEquals("alt", savedStat().getRisk());

        onEdt(() -> {
            dialog.getRootPane().getActionMap().get("discardRiskEdit")
                    .actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, "escape"));
            assertFalse(table.isEditing());
            assertEquals("alt", table.getValueAt(0, RISK_COLUMN));
            return null;
        });
        assertFalse(status().contains("201"));
        assertEquals("alt", savedStat().getRisk());
    }

    private void openDialog(String initialRisk) throws Exception {
        database = new ControlledDatabaseManager(tempDirectory.toString());
        database.checkAndUpdateSubscribers("test-id", "mql5", "Test provider", 10, null);
        if (initialRisk != null) {
            assertTrue(database.updateSignalRisk("test-id", "mql5", initialRisk));
        }
        openExistingDatabaseDialog();
    }

    private void openExistingDatabaseDialog() throws Exception {
        LoadObservation initialLoad = onEdt(() -> {
            dialog = new SubscriberStatisticsDialog(null, database, null);
            dialog.addNotify(); // Create an invisible peer so actual disposal is observable.
            table = field("statsTable", JTable.class);
            saves = field("dbSaveExecutor", ExecutorService.class);
            // The dialog is never shown. Its first load can only update the model
            // after this EDT callback returns, so the listener cannot miss it.
            return observeLoad();
        });
        awaitLoaded(initialLoad);
    }

    private LoadObservation reload() throws Exception {
        return onEdt(() -> {
            dialog.loadData();
            return observeLoad();
        });
    }

    private LoadObservation observeLoad() throws Exception {
        CountDownLatch applied = new CountDownLatch(1);
        table.getModel().addTableModelListener(event -> {
            // This listener can run before JTable updates its sorter/view mapping.
            if (table.getModel().getRowCount() > 0) {
                applied.countDown();
            }
        });
        return new LoadObservation(field("loadWorker", SwingWorker.class), applied);
    }

    private void awaitLoaded(LoadObservation observation) throws Exception {
        observation.worker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(observation.applied.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The loaded row must be applied on the EDT");
        assertEquals(1, onEdt(() -> table.getRowCount()));
    }

    private void pauseSaves() throws Exception {
        releaseSaves = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        pausedSave = saves.submit(() -> {
            started.countDown();
            if (!releaseSaves.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The test did not release queued saves");
            }
            return null;
        });
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private void resumeSaves() throws Exception {
        releaseSaves.countDown();
        pausedSave.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void awaitSaves() throws Exception {
        saves.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        onEdt(() -> null); // Apply status callbacks queued by the finished saves.
    }

    private void beginRiskEdit(String value) throws Exception {
        onEdt(() -> {
            assertTrue(table.editCellAt(0, RISK_COLUMN));
            ((JTextField) table.getEditorComponent()).setText(value);
            return null;
        });
    }

    private void assertCloseSavesEditor(Consumer<SubscriberStatisticsDialog> closeAction) throws Exception {
        openDialog("alt");
        pauseSaves();
        beginRiskEdit("neu");

        CloseObservation closing = requestClose(closeAction);
        onEdt(() -> {
            assertFalse(table.isEditing());
            assertTrue(dialog.isDisplayable());
            assertFalse(table.isEnabled());
            assertFalse(field("searchField", JTextField.class).isEnabled());
            assertFalse(field("refreshButton", JButton.class).isEnabled());
            assertTrue(field("closing", Boolean.class));
            // Repeated close requests and refresh must not replace the pending close.
            dialog.dispose();
            dialog.loadData();
            assertSame(closing.worker, field("closeWorker", SwingWorker.class));
            return null;
        });
        assertTrue(status().contains("Speichere"));
        assertFalse(saves.isShutdown(), "The executor must remain available if a save fails");
        assertThrows(TimeoutException.class, () -> closing.worker.get(200, TimeUnit.MILLISECONDS));

        resumeSaves();
        awaitCloseAttempt(closing);
        assertFalse(onEdt(() -> dialog.isDisplayable()));
        assertTrue(saves.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("neu", savedStat().getRisk());

        openExistingDatabaseDialog();
        assertEquals("neu", onEdt(() -> table.getValueAt(0, RISK_COLUMN)));
    }

    private CloseObservation requestClose(Consumer<SubscriberStatisticsDialog> closeAction) throws Exception {
        return onEdt(() -> {
            closeAction.accept(dialog);
            return observeClose();
        });
    }

    private CloseObservation observeClose() throws Exception {
        SwingWorker<?, ?> worker = field("closeWorker", SwingWorker.class);
        CountDownLatch finished = new CountDownLatch(worker == null ? 0 : 1);
        if (worker != null) {
            worker.addPropertyChangeListener(event -> {
                if ("state".equals(event.getPropertyName())
                        && event.getNewValue() == SwingWorker.StateValue.DONE) {
                    finished.countDown();
                }
            });
        }
        return new CloseObservation(worker, finished);
    }

    private void awaitCloseAttempt(CloseObservation observation) throws Exception {
        if (observation.worker != null) {
            observation.worker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertTrue(observation.finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The close attempt must finish on the EDT");
        onEdt(() -> null);
    }

    private void applyRowColor(String color) throws Exception {
        onEdt(() -> {
            table.setRowSelectionInterval(0, 0);
            Method applyColor = SubscriberStatisticsDialog.class.getDeclaredMethod("applyRowColor", String.class);
            applyColor.setAccessible(true);
            applyColor.invoke(dialog, color);
            return null;
        });
    }

    private String status() throws Exception {
        return onEdt(() -> field("statusLabel", JLabel.class).getText());
    }

    private SubscriberStat savedStat() {
        List<SubscriberStat> stats = database.getAllSubscriberStatistics();
        assertEquals(1, stats.size());
        return stats.get(0);
    }

    private <T> T field(String name, Class<T> type) throws Exception {
        Field field = SubscriberStatisticsDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(dialog));
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class LoadObservation {
        private final SwingWorker<?, ?> worker;
        private final CountDownLatch applied;

        private LoadObservation(SwingWorker<?, ?> worker, CountDownLatch applied) {
            this.worker = worker;
            this.applied = applied;
        }
    }

    private static final class CloseObservation {
        private final SwingWorker<?, ?> worker;
        private final CountDownLatch finished;

        private CloseObservation(SwingWorker<?, ?> worker, CountDownLatch finished) {
            this.worker = worker;
            this.finished = finished;
        }
    }

    private static final class ControlledDatabaseManager extends DatabaseManager {
        private final AtomicInteger statisticsReads = new AtomicInteger();
        private volatile boolean rejectRiskUpdates;
        private volatile boolean rejectColorUpdates;
        private volatile RuntimeException colorFailure;

        private ControlledDatabaseManager(String rootDirectory) {
            super(rootDirectory);
        }

        @Override
        public List<SubscriberStat> getAllSubscriberStatistics() {
            statisticsReads.incrementAndGet();
            return super.getAllSubscriberStatistics();
        }

        @Override
        public boolean updateSignalRisk(String signalId, String mqlVersion, String risk) {
            return !rejectRiskUpdates && super.updateSignalRisk(signalId, mqlVersion, risk);
        }

        @Override
        public boolean updateRowColor(String signalId, String mqlVersion, String colorName) {
            if (colorFailure != null) {
                throw colorFailure;
            }
            return !rejectColorUpdates && super.updateRowColor(signalId, mqlVersion, colorName);
        }
    }
}
