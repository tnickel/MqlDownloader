package gui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import javax.swing.JLabel;

import static org.junit.jupiter.api.Assertions.*;

class ReportCellLayoutTest {
    private final FontMetrics metrics = new JLabel().getFontMetrics(
            ReportCellLayout.badgeFont(new Font(Font.DIALOG, Font.PLAIN, 12)));

    @Test
    void ignoresPaddingGapsAndClicksOutsideTheIcons() {
        ReportCellLayout layout = ReportCellLayout.forCell(85, 36, 3, metrics);
        assertEquals(3, layout.visibleCount);
        assertEquals(0, layout.hiddenCount);
        assertNull(layout.overflowBounds);
        Rectangle first = layout.iconBounds(0);
        Rectangle last = layout.iconBounds(2);

        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(first.x - 1, first.y));
        assertEquals(0, layout.hitAt(first.x, first.y));
        assertEquals(0, layout.hitAt(first.x + first.width - 1, first.y + first.height - 1));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(first.x + first.width, first.y));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(first.x, first.y - 1));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(first.x, first.y + first.height));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(last.x + last.width, last.y));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(100, last.y));
    }

    @Test
    void replacesClippedReportsWithAnAccessibleOverflowBadge() {
        ReportCellLayout layout = ReportCellLayout.forCell(85, 36, 5, metrics);
        assertEquals(2, layout.visibleCount);
        assertEquals(3, layout.hiddenCount);
        assertEquals("+3", layout.overflowText);
        Rectangle badge = layout.overflowBounds;
        assertNotNull(badge);
        assertEquals(ReportCellLayout.OVERFLOW, layout.hitAt(badge.x, badge.y));
        assertEquals(ReportCellLayout.OVERFLOW,
                layout.hitAt(badge.x + badge.width - 1, badge.y + badge.height - 1));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(badge.x - 1, badge.y));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(badge.x + badge.width, badge.y));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(badge.x, badge.y - 1));
        assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(badge.x, badge.y + badge.height));
    }

    @Test
    void wideningTheColumnMakesEveryReportDirectlyAccessible() {
        ReportCellLayout layout = ReportCellLayout.forCell(130, 36, 5, metrics);
        assertEquals(5, layout.visibleCount);
        assertEquals(0, layout.hiddenCount);
        assertNull(layout.overflowBounds);
        for (int i = 0; i < 5; i++) {
            Rectangle icon = layout.iconBounds(i);
            assertEquals(i, layout.hitAt(icon.x, icon.y));
        }
    }

    @Test
    void narrowOrShortCellsKeepAllReportsInTheMenu() {
        ReportCellLayout narrow = ReportCellLayout.forCell(15, 36, 20, metrics);
        assertEquals(0, narrow.visibleCount);
        assertEquals(20, narrow.hiddenCount);
        assertEquals("+", narrow.overflowText);
        assertEquals(ReportCellLayout.OVERFLOW,
                narrow.hitAt(narrow.overflowBounds.x, narrow.overflowBounds.y));

        ReportCellLayout shortCell = ReportCellLayout.forCell(85, 10, 3, metrics);
        assertEquals(0, shortCell.visibleCount);
        assertEquals(3, shortCell.hiddenCount);
        assertEquals(10, shortCell.overflowBounds.height);
    }

    @Test
    void everyReportHasAnOnscreenTargetAcrossColumnWidthsAndCounts() {
        for (int width = 1; width <= 200; width++) {
            Rectangle cell = new Rectangle(0, 0, width, 36);
            for (int count : new int[]{1, 2, 3, 4, 5, 10, 1000}) {
                ReportCellLayout layout = ReportCellLayout.forCell(width, 36, count, metrics);
                assertEquals(count, layout.visibleCount + layout.hiddenCount);
                for (int i = 0; i < layout.visibleCount; i++) {
                    Rectangle icon = layout.iconBounds(i);
                    assertTrue(cell.contains(icon), "Icon outside width " + width);
                    assertEquals(i, layout.hitAt(icon.x, icon.y));
                }
                if (layout.hiddenCount > 0) {
                    assertNotNull(layout.overflowBounds);
                    assertTrue(cell.contains(layout.overflowBounds), "Badge outside width " + width);
                    assertEquals(ReportCellLayout.OVERFLOW,
                            layout.hitAt(layout.overflowBounds.x, layout.overflowBounds.y));
                }
            }
        }
    }

    @Test
    void emptyAndZeroSizeCellsHaveNoHitTarget() {
        for (ReportCellLayout layout : new ReportCellLayout[]{
                ReportCellLayout.forCell(85, 36, 0, metrics),
                ReportCellLayout.forCell(0, 36, 5, metrics),
                ReportCellLayout.forCell(85, 0, 5, metrics)}) {
            assertEquals(0, layout.visibleCount);
            assertNull(layout.overflowBounds);
            assertEquals(ReportCellLayout.NO_HIT, layout.hitAt(5, 5));
        }
    }
}
