package gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class RiskCellEditorTest {
    @Test
    void mouseEditingRequiresADoubleClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(1, 1);
            RiskCellEditor editor = new RiskCellEditor();
            assertFalse(editor.isCellEditable(new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    0, 0, 1, 1, 1, false)));
            assertTrue(editor.isCellEditable(new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    0, 0, 1, 1, 2, false)));
        });
    }

    @Test
    void commitsEmptyAndBoundaryValuesToTheTableModel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = riskTable();
            for (int length : new int[] {0, 199, 200}) {
                JTextField field = beginEditing(table);
                String value = repeated('x', length);
                field.setText(value);
                assertTrue(RiskCellEditor.commitEditing(table));
                assertFalse(table.isEditing());
                assertEquals(value, table.getModel().getValueAt(0, 0));
            }
            assertTrue(RiskCellEditor.commitEditing(table));
        });
    }

    @Test
    void overlongPasteRemainsEditableAndPreventsDiscardingTheRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = riskTable();
            JTextField field = beginEditing(table);
            Border normalBorder = field.getBorder();
            String pasted = repeated('p', 201);
            field.selectAll();
            field.replaceSelection(pasted);

            assertEquals(pasted, field.getText());
            assertNotSame(normalBorder, field.getBorder());
            assertTrue(field.getToolTipText().contains("200"));
            assertTrue(field.getToolTipText().contains("201"));
            assertFalse(RiskCellEditor.commitEditing(table));
            if (RiskCellEditor.commitEditing(table)) {
                ((DefaultTableModel) table.getModel()).setRowCount(0);
            }
            assertEquals(1, table.getRowCount());
            assertEquals("alt", table.getModel().getValueAt(0, 0));
            assertTrue(table.isEditing());
            assertSame(field, table.getEditorComponent());

            field.select(200, 201);
            field.replaceSelection("");
            assertSame(normalBorder, field.getBorder());
            assertTrue(RiskCellEditor.commitEditing(table));
            assertEquals(repeated('p', 200), table.getModel().getValueAt(0, 0));
        });
    }

    @Test
    void validatesTheResultOfReplacingAndDeletingASelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = riskTable();
            JTextField field = beginEditing(table);
            field.setText(repeated('x', 200));
            field.select(50, 100);
            field.replaceSelection(repeated('y', 50));
            assertTrue(RiskCellEditor.commitEditing(table));
            String expected = repeated('x', 50) + repeated('y', 50) + repeated('x', 100);
            assertEquals(expected, table.getModel().getValueAt(0, 0));

            field = beginEditing(table);
            field.select(50, 100);
            field.replaceSelection(repeated('z', 51));
            assertFalse(RiskCellEditor.commitEditing(table));
            assertEquals(expected, table.getModel().getValueAt(0, 0));
            field.selectAll();
            field.replaceSelection("");
            assertTrue(RiskCellEditor.commitEditing(table));
            assertEquals("", table.getModel().getValueAt(0, 0));
        });
    }

    @Test
    void allowsActionsWhenThereIsNoActiveEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = riskTable();
            assertTrue(RiskCellEditor.commitEditing(table));
            assertEquals("alt", table.getModel().getValueAt(0, 0));
        });
    }

    @Test
    void validationListenerReportsLengthAndClearsAfterCorrectionOrCancel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = riskTable();
            JTextField field = beginEditing(table);
            RiskCellEditor editor = (RiskCellEditor) table.getCellEditor();
            AtomicReference<String> message = new AtomicReference<>();
            editor.setValidationListener(message::set);
            assertNull(editor.getValidationMessage());
            field.setText(repeated('x', 201));
            assertEquals(editor.getValidationMessage(), message.get());
            assertTrue(message.get().contains("200"));
            assertTrue(message.get().contains("201"));
            assertTrue(message.get().contains("Escape"));
            assertFalse(RiskCellEditor.commitEditing(table));

            field.setText("neu");
            assertNull(message.get());
            field.setText(repeated('x', 201));
            editor.cancelCellEditing();
            assertNull(message.get());
            assertNull(editor.getValidationMessage());
            assertFalse(table.isEditing());
            assertEquals("alt", table.getModel().getValueAt(0, 0));
        });
    }

    private static JTable riskTable() {
        JTable table = new JTable(new DefaultTableModel(new Object[][] {{"alt"}}, new Object[] {"Risiko"}));
        table.getColumnModel().getColumn(0).setCellEditor(new RiskCellEditor());
        return table;
    }

    private static JTextField beginEditing(JTable table) {
        assertTrue(table.editCellAt(0, 0));
        return (JTextField) table.getEditorComponent();
    }

    private static String repeated(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
