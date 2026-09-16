package gui;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.util.EventObject;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

/** Preserves a draft while table geometry changes, including drafts rejected by validation. */
class RiskEditingTable extends JTable {
    private static final long serialVersionUID = 1L;
    private int editingModelColumn = -1;
    private KeyboardFocusManager focusManager;
    private final PropertyChangeListener focusListener = event -> commitAfterFocusChange();

    RiskEditingTable(TableModel model) {
        super(model);
    }

    @Override
    public boolean editCellAt(int row, int column, EventObject event) {
        if (!super.editCellAt(row, column, event)) {
            return false;
        }
        editingModelColumn = convertColumnIndexToModel(getEditingColumn());
        focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener("permanentFocusOwner", focusListener);
        return true;
    }

    private void commitAfterFocusChange() {
        if (!isEditing() || focusManager == null) {
            return;
        }
        // Read the current owner, rather than interpreting a late FocusEvent from
        // a text field that may already have been reused for another cell.
        Component owner = focusManager.getPermanentFocusOwner();
        if (owner != null && !SwingUtilities.isDescendingFrom(owner, this)
                && SwingUtilities.getRoot(owner) == SwingUtilities.getRoot(this)) {
            // A rejected edit stays in place. JTable's focus-removal option would cancel it.
            getCellEditor().stopCellEditing();
        }
    }

    @Override
    public void removeEditor() {
        if (focusManager != null) {
            focusManager.removePropertyChangeListener("permanentFocusOwner", focusListener);
            focusManager = null;
        }
        editingModelColumn = -1;
        super.removeEditor();
    }

    @Override
    public void columnMoved(TableColumnModelEvent event) {
        if (isEditing()) {
            // Column events arrive after the column model has already moved.
            // Committing at the old view index could update a different model column.
            setEditingColumn(convertColumnIndexToView(editingModelColumn));
            updateEditorBounds();
        }
        repaint();
    }

    @Override
    public void columnMarginChanged(ChangeEvent event) {
        // Keep JTable's sizing behavior without its stop-then-cancel editing policy.
        TableColumn resizingColumn = getTableHeader() == null ? null : getTableHeader().getResizingColumn();
        if (resizingColumn != null && getAutoResizeMode() == AUTO_RESIZE_OFF) {
            resizingColumn.setPreferredWidth(resizingColumn.getWidth());
        }
        updateEditorBounds();
        resizeAndRepaint();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateEditorBounds();
    }

    private void updateEditorBounds() {
        if (isEditing() && getEditorComponent() != null && getEditingRow() >= 0 && getEditingColumn() >= 0) {
            getEditorComponent().setBounds(getCellRect(getEditingRow(), getEditingColumn(), false));
        }
    }
}
