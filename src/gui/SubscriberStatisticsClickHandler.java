package gui;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Routes clicks by the cell under the pointer, independently of table selection. */
final class SubscriberStatisticsClickHandler extends MouseAdapter {
    interface Actions {
        void openUrl(int modelRow);
        void openReports(int viewRow, int viewColumn, int modelRow, Point click);
        void openHistory(int modelRow);
    }

    private final JTable table;
    private final int riskColumn;
    private final int reportsColumn;
    private final int urlColumn;
    private final Actions actions;

    SubscriberStatisticsClickHandler(JTable table, int riskColumn, int reportsColumn, int urlColumn,
                                     Actions actions) {
        this.table = table;
        this.riskColumn = riskColumn;
        this.reportsColumn = reportsColumn;
        this.urlColumn = urlColumn;
        this.actions = actions;
    }

    @Override
    public void mouseClicked(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event)) {
            return;
        }
        int viewRow = table.rowAtPoint(event.getPoint());
        int viewColumn = table.columnAtPoint(event.getPoint());
        if (viewRow < 0 || viewColumn < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        // A double click delivers counts 1 and 2. Link actions run only on the first.
        if (modelColumn == urlColumn) {
            if (event.getClickCount() == 1) {
                actions.openUrl(modelRow);
            }
        } else if (modelColumn == reportsColumn) {
            if (event.getClickCount() == 1) {
                actions.openReports(viewRow, viewColumn, modelRow, event.getPoint());
            }
        } else if (modelColumn != riskColumn && event.getClickCount() == 2) {
            actions.openHistory(modelRow);
        }
    }
}
