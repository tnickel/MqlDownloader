package gui;

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Constructor;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriberStatisticsRenderingTest {
    @Test
    void selectionPreservesChangeTextTooltipAndWeightWhileUsingSelectionColors() throws Exception {
        Class<?> rendererClass = Class.forName("gui.SubscriberStatisticsDialog$ChangeTextRenderer");
        Constructor<?> constructor = rendererClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        TableCellRenderer renderer = (TableCellRenderer) constructor.newInstance();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(1, 1);
            table.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            table.setForeground(Color.BLACK);
            table.setBackground(Color.WHITE);
            table.setSelectionForeground(Color.YELLOW);
            table.setSelectionBackground(Color.BLUE);
            Object[] values = {null, 0, 7, -3, null};
            String[] texts = {"\u2013", "0", "+7", "-3", "\u2013"};
            Color[] colors = {Color.GRAY, Color.GRAY, new Color(0, 110, 0), Color.RED, Color.GRAY};
            for (boolean selected : new boolean[]{false, true, false}) {
                for (int i = 0; i < values.length; i++) {
                    JLabel label = (JLabel) renderer.getTableCellRendererComponent(
                            table, values[i], selected, false, 0, 0);
                    assertEquals(texts[i], label.getText());
                    assertEquals(selected ? Color.YELLOW : colors[i], label.getForeground());
                    assertEquals(selected ? Color.BLUE : Color.WHITE, label.getBackground());
                    assertEquals(values[i] instanceof Integer && ((Integer) values[i]) != 0,
                            label.getFont().isBold());
                    if (values[i] == null) {
                        assertNotNull(label.getToolTipText());
                        assertTrue(label.getToolTipText().contains("Vergleichswert"));
                    } else {
                        assertNull(label.getToolTipText());
                    }
                }
            }
        });
    }
}
