package gui;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriberStatisticsClickHandlerTest {
    private static final int RISK_COLUMN = 1;
    private static final int REPORTS_COLUMN = 2;
    private static final int URL_COLUMN = 3;

    @Test
    void aDoubleClickOnAUrlDispatchesOneUrlActionAndNoHistory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture();
            fixture.doubleClick(fixture.cellCenter(0, URL_COLUMN));
            assertEquals(Arrays.asList(0), fixture.actions.urlRows);
            assertEquals(0, fixture.actions.reportClicks.size());
            assertEquals(0, fixture.actions.historyRows.size());
        });
    }

    @Test
    void pdfAndOverflowDoubleClicksEachDispatchOnlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture();
            Rectangle cell = fixture.table.getCellRect(0, REPORTS_COLUMN, false);
            ReportCellLayout layout = ReportCellLayout.forCell(cell.width, cell.height, 5,
                    fixture.table.getFontMetrics(ReportCellLayout.badgeFont(fixture.table.getFont())));
            assertTrue(layout.visibleCount > 0);
            assertNotNull(layout.overflowBounds);
            Rectangle icon = layout.iconBounds(0);
            Point pdf = new Point(cell.x + icon.x + 1, cell.y + icon.y + 1);
            Point overflow = new Point(cell.x + layout.overflowBounds.x + 1,
                    cell.y + layout.overflowBounds.y + 1);
            fixture.doubleClick(pdf);
            fixture.doubleClick(overflow);
            assertEquals(Arrays.asList(pdf, overflow), fixture.actions.reportClicks);
            assertEquals(0, fixture.actions.urlRows.size());
            assertEquals(0, fixture.actions.historyRows.size());
        });
    }

    @Test
    void historyRequiresADoubleClickOutsideEditableRiskAndLinkColumns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture();
            fixture.click(fixture.cellCenter(0, 0), 1, MouseEvent.BUTTON1);
            assertTrue(fixture.actions.historyRows.isEmpty());
            fixture.click(fixture.cellCenter(0, 0), 2, MouseEvent.BUTTON1);
            fixture.doubleClick(fixture.cellCenter(0, RISK_COLUMN));
            assertEquals(Arrays.asList(0), fixture.actions.historyRows);
            assertTrue(fixture.actions.urlRows.isEmpty());
            assertTrue(fixture.actions.reportClicks.isEmpty());
        });
    }

    @Test
    void resolvesThePointerCellAfterSortingAndColumnMovesDespiteStaleSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture();
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(fixture.model);
            fixture.table.setRowSorter(sorter);
            sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
            fixture.table.moveColumn(URL_COLUMN, 0);
            fixture.table.changeSelection(1, 2, false, false);
            fixture.doubleClick(fixture.cellCenter(0, 0));
            assertEquals(Arrays.asList(1), fixture.actions.urlRows);
            assertTrue(fixture.actions.historyRows.isEmpty());
            assertTrue(fixture.actions.reportClicks.isEmpty());
        });
    }

    @Test
    void ignoresNonLeftButtonsAndPointsOutsideCellsEvenWithASelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture();
            fixture.table.changeSelection(0, URL_COLUMN, false, false);
            for (int button : new int[]{MouseEvent.BUTTON2, MouseEvent.BUTTON3}) {
                fixture.click(fixture.cellCenter(0, URL_COLUMN), 1, button);
                fixture.click(fixture.cellCenter(0, 0), 2, button);
            }
            fixture.doubleClick(new Point(-1, 10));
            fixture.doubleClick(new Point(10, 90));
            fixture.doubleClick(new Point(1000, 10));
            assertTrue(fixture.actions.urlRows.isEmpty());
            assertTrue(fixture.actions.reportClicks.isEmpty());
            assertTrue(fixture.actions.historyRows.isEmpty());
        });
    }

    private static final class Fixture {
        final DefaultTableModel model = new DefaultTableModel(new Object[][]{
                {"B", "", "", ""}, {"A", "", "", ""}
        }, new Object[]{"Name", "Risk", "Reports", "URL"});
        final JTable table = new JTable(model);
        final RecordingActions actions = new RecordingActions();
        final SubscriberStatisticsClickHandler handler = new SubscriberStatisticsClickHandler(
                table, RISK_COLUMN, REPORTS_COLUMN, URL_COLUMN, actions);

        Fixture() {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setRowHeight(36);
            for (int column = 0; column < table.getColumnCount(); column++) {
                table.getColumnModel().getColumn(column).setWidth(85);
            }
            table.setSize(340, 100);
        }

        Point cellCenter(int row, int viewColumn) {
            Rectangle cell = table.getCellRect(row, viewColumn, false);
            return new Point(cell.x + cell.width / 2, cell.y + cell.height / 2);
        }

        void doubleClick(Point point) {
            click(point, 1, MouseEvent.BUTTON1);
            click(point, 2, MouseEvent.BUTTON1);
        }

        void click(Point point, int count, int button) {
            handler.mouseClicked(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0,
                    point.x, point.y, count, false, button));
        }
    }

    private static final class RecordingActions implements SubscriberStatisticsClickHandler.Actions {
        final List<Integer> urlRows = new ArrayList<>();
        final List<Point> reportClicks = new ArrayList<>();
        final List<Integer> historyRows = new ArrayList<>();

        @Override
        public void openUrl(int modelRow) {
            urlRows.add(modelRow);
        }

        @Override
        public void openReports(int viewRow, int viewColumn, int modelRow, Point click) {
            reportClicks.add(click);
        }

        @Override
        public void openHistory(int modelRow) {
            historyRows.add(modelRow);
        }
    }
}
