package gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.junit.jupiter.api.Test;

class RiskEditingTableTest {
    @Test
    void resizingAColumnKeepsTheInvalidDraftUntilItCanBeCommitted() throws Exception {
        assertGeometryChangePreservesDraft(table -> table.getColumnModel().getColumn(1).setWidth(120));
    }

    @Test
    void tableResizeAndLayoutKeepTheInvalidDraftUntilItCanBeCommitted() throws Exception {
        assertGeometryChangePreservesDraft(table -> {
            table.setSize(600, 100);
            table.doLayout();
        });
    }

    @Test
    void movingTheEditedColumnPreservesItsModelColumn() throws Exception {
        assertGeometryChangePreservesDraft(table -> table.getColumnModel().moveColumn(1, 0));
    }

    @Test
    void movingAnotherColumnAcrossTheEditorPreservesItsModelColumn() throws Exception {
        assertGeometryChangePreservesDraft(table -> table.getColumnModel().moveColumn(2, 0));
    }

    @Test
    void movingColumnsDoesNotCommitAValidDraftIntoTheWrongModelColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int[] move : new int[][] {{1, 0}, {0, 2}, {2, 0}}) {
                RiskEditingTable table = table();
                JTextField field = beginEditing(table, 0, "neu");
                table.getColumnModel().moveColumn(move[0], move[1]);
                assertSame(field, table.getEditorComponent());
                assertEquals(table.convertColumnIndexToView(1), table.getEditingColumn());
                assertTrue(RiskCellEditor.commitEditing(table));
                assertEquals("A", table.getModel().getValueAt(0, 0));
                assertEquals("neu", table.getModel().getValueAt(0, 1));
                assertEquals("otherA", table.getModel().getValueAt(0, 2));
            }
        });
    }

    @Test
    void sortingAndMovingColumnsRetainTheOriginalModelCell() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RiskEditingTable table = table();
            TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
            table.setRowSorter(sorter);
            JTextField field = beginEditing(table, 0, longText());
            sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
            table.getColumnModel().moveColumn(1, 0);
            table.doLayout();
            assertEquals(1, table.getEditingRow());
            assertEquals(table.getCellRect(1, 0, false), field.getBounds());
            field.setText("neuA");
            assertTrue(RiskCellEditor.commitEditing(table));
            assertEquals("neuA", table.getModel().getValueAt(0, 1));
            assertEquals("oldB", table.getModel().getValueAt(1, 1));
        });
    }

    @Test
    void escapeStillExplicitlyDiscardsAnInvalidDraftAfterResizing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RiskEditingTable table = table();
            beginEditing(table, 0, longText());
            RiskCellEditor editor = (RiskCellEditor) table.getCellEditor();
            table.getColumnModel().getColumn(1).setWidth(120);
            KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            Object command = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(escape);
            table.getActionMap().get(command).actionPerformed(
                    new ActionEvent(table, ActionEvent.ACTION_PERFORMED, command.toString()));
            assertFalse(table.isEditing());
            assertEquals("oldA", table.getModel().getValueAt(0, 1));
            assertNull(editor.getValidationMessage());
        });
    }

    @Test
    void permanentFocusOwnerCommitsValidInputAndPreservesInvalidInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KeyboardFocusManager previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            TestFocusManager focusManager = new TestFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager);
            RiskEditingTable table = table();
            JPanel panel = new JPanel();
            JButton button = new JButton("Andere Komponente");
            panel.add(table);
            panel.add(button);
            try {
                JTextField field = beginEditing(table, 0, "neu");
                focusManager.movePermanentFocus(field);
                focusManager.movePermanentFocus(null);
                assertTrue(table.isEditing());
                focusManager.movePermanentFocus(button);
                assertFalse(table.isEditing());
                assertEquals("neu", table.getModel().getValueAt(0, 1));

                field = beginEditing(table, 0, longText());
                focusManager.movePermanentFocus(field);
                focusManager.movePermanentFocus(button);
                assertTrue(table.isEditing());
                assertEquals(longText(), field.getText());
                assertEquals("neu", table.getModel().getValueAt(0, 1));
                table.getCellEditor().cancelCellEditing();
                assertFalse(table.isEditing());
            } finally {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
            }
        });
    }

    @Test
    void aLateFocusEventFromAReusedFieldDoesNotCommitTheNextEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RiskEditingTable table = table();
            JTextField oldField = beginEditing(table, 0, "neuA");
            FocusEvent oldEvent = new FocusEvent(oldField, FocusEvent.FOCUS_LOST, false, new JButton());
            assertTrue(RiskCellEditor.commitEditing(table));
            JTextField newField = beginEditing(table, 1, "EntwurfB");
            assertSame(oldField, newField);
            for (FocusListener listener : oldField.getFocusListeners()) {
                listener.focusLost(oldEvent);
            }
            assertTrue(table.isEditing());
            assertEquals("EntwurfB", newField.getText());
            assertEquals("oldB", table.getModel().getValueAt(1, 1));
            table.getCellEditor().cancelCellEditing();
        });
    }

    private static void assertGeometryChangePreservesDraft(Consumer<RiskEditingTable> change) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RiskEditingTable table = table();
            JTextField field = beginEditing(table, 0, longText());
            assertFalse(RiskCellEditor.commitEditing(table));
            change.accept(table);
            assertTrue(table.isEditing());
            assertSame(field, table.getEditorComponent());
            assertEquals(longText(), field.getText());
            assertEquals(table.convertColumnIndexToView(1), table.getEditingColumn());
            assertEquals(table.getCellRect(table.getEditingRow(), table.getEditingColumn(), false), field.getBounds());
            assertEquals("oldA", table.getModel().getValueAt(0, 1));
            field.setText("neu");
            assertTrue(RiskCellEditor.commitEditing(table));
            assertEquals("A", table.getModel().getValueAt(0, 0));
            assertEquals("neu", table.getModel().getValueAt(0, 1));
            assertEquals("otherA", table.getModel().getValueAt(0, 2));
        });
    }

    private static RiskEditingTable table() {
        RiskEditingTable table = new RiskEditingTable(new DefaultTableModel(
                new Object[][] {{"A", "oldA", "otherA"}, {"B", "oldB", "otherB"}},
                new Object[] {"Name", "Risiko", "Sonstiges"}));
        table.getColumnModel().getColumn(1).setCellEditor(new RiskCellEditor());
        table.setSize(300, 100);
        table.doLayout();
        return table;
    }

    private static JTextField beginEditing(JTable table, int row, String value) {
        assertTrue(table.editCellAt(row, table.convertColumnIndexToView(1)));
        JTextField field = (JTextField) table.getEditorComponent();
        field.setText(value);
        return field;
    }

    private static String longText() {
        char[] chars = new char[201];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    private static final class TestFocusManager extends DefaultKeyboardFocusManager {
        void movePermanentFocus(Component component) {
            setGlobalPermanentFocusOwner(component);
        }
    }
}
