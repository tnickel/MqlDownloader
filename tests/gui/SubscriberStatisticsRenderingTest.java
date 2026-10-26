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

    @Test
    void urlCellShowsPlatformSuffixAndFullUrlTooltip() throws Exception {
        Class<?> rendererClass = Class.forName("gui.SubscriberStatisticsDialog$UrlTextRenderer");
        Constructor<?> constructor = rendererClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        TableCellRenderer renderer = (TableCellRenderer) constructor.newInstance();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(1, 1);
            table.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            table.setForeground(Color.BLACK);
            table.setBackground(Color.WHITE);

            JLabel mql4 = (JLabel) renderer.getTableCellRendererComponent(table,
                    new SubscriberStatisticsDialog.UrlLink("https://www.mql5.com/en/signals/2306053", "mql4"),
                    false, false, 0, 0);
            assertEquals("\u2197 mql5.com \u00b7 MQL4", mql4.getText());
            assertEquals("https://www.mql5.com/en/signals/2306053", mql4.getToolTipText());

            JLabel mql5 = (JLabel) renderer.getTableCellRendererComponent(table,
                    new SubscriberStatisticsDialog.UrlLink("https://www.mql5.com/en/signals/947158", "mql5"),
                    false, false, 0, 0);
            assertEquals("\u2197 mql5.com \u00b7 MQL5", mql5.getText());

            JLabel legacyString = (JLabel) renderer.getTableCellRendererComponent(table,
                    "https://example.com/signal", false, false, 0, 0);
            assertEquals("\u2197 example.com", legacyString.getText());

            JLabel empty = (JLabel) renderer.getTableCellRendererComponent(table,
                    new SubscriberStatisticsDialog.UrlLink("", "mql4"), false, false, 0, 0);
            assertEquals("", empty.getText());
            assertNull(empty.getToolTipText());
        });
    }
}
