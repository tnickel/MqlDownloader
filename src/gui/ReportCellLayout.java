package gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;

/** Shared bounds for report painting and hit testing, including the overflow menu. */
final class ReportCellLayout {
    static final int ICON_WIDTH = 19;
    static final int ICON_HEIGHT = 22;
    static final int ICON_GAP = 5;
    static final int ICON_PAD = 5;
    static final int NO_HIT = -1;
    static final int OVERFLOW = -2;

    final int visibleCount;
    final int hiddenCount;
    final Rectangle overflowBounds;
    final String overflowText;
    private final int iconX;
    private final int iconY;

    private ReportCellLayout(int visibleCount, int hiddenCount, int iconX, int iconY,
                             Rectangle overflowBounds, String overflowText) {
        this.visibleCount = visibleCount;
        this.hiddenCount = hiddenCount;
        this.iconX = iconX;
        this.iconY = iconY;
        this.overflowBounds = overflowBounds;
        this.overflowText = overflowText;
    }

    static Font badgeFont(Font tableFont) {
        return tableFont.deriveFont(Font.BOLD, 10f);
    }

    static ReportCellLayout forCell(int width, int height, int count, FontMetrics metrics) {
        int padding = Math.min(ICON_PAD, Math.max(0, width / 8));
        int available = Math.max(0, width - 2 * padding);
        int iconY = Math.max(0, (height - ICON_HEIGHT) / 2);
        if (count <= 0 || available == 0 || height <= 0) {
            return new ReportCellLayout(0, Math.max(0, count), padding, iconY, null, "");
        }
        long allIconsWidth = (long) count * (ICON_WIDTH + ICON_GAP) - ICON_GAP;
        if (height >= ICON_HEIGHT && allIconsWidth <= available) {
            return new ReportCellLayout(count, 0, padding, iconY, null, "");
        }

        int visible = height >= ICON_HEIGHT
                ? Math.min(count - 1, available / (ICON_WIDTH + ICON_GAP)) : 0;
        while (visible > 0 && visible * (ICON_WIDTH + ICON_GAP)
                + badgeWidth(count - visible, metrics) > available) {
            visible--;
        }
        int hidden = count - visible;
        int badgeX = padding + visible * (ICON_WIDTH + ICON_GAP);
        int badgeWidth = Math.min(badgeWidth(hidden, metrics), available - (badgeX - padding));
        int badgeHeight = Math.min(ICON_HEIGHT, height);
        Rectangle badge = new Rectangle(badgeX, Math.max(0, (height - badgeHeight) / 2),
                badgeWidth, badgeHeight);
        String label = "+" + hidden;
        if (metrics.stringWidth(label) + 4 > badgeWidth) {
            label = metrics.stringWidth("+") + 2 <= badgeWidth ? "+" : "";
        }
        return new ReportCellLayout(visible, hidden, padding, iconY, badge, label);
    }

    private static int badgeWidth(int hiddenCount, FontMetrics metrics) {
        return Math.max(24, metrics.stringWidth("+" + hiddenCount) + 10);
    }

    Rectangle iconBounds(int index) {
        if (index < 0 || index >= visibleCount) {
            throw new IndexOutOfBoundsException("Report icon " + index);
        }
        return new Rectangle(iconX + index * (ICON_WIDTH + ICON_GAP), iconY, ICON_WIDTH, ICON_HEIGHT);
    }

    int hitAt(int x, int y) {
        if (overflowBounds != null && overflowBounds.contains(x, y)) {
            return OVERFLOW;
        }
        if (x < iconX || y < iconY || y >= iconY + ICON_HEIGHT) {
            return NO_HIT;
        }
        int index = (x - iconX) / (ICON_WIDTH + ICON_GAP);
        return index < visibleCount && iconBounds(index).contains(x, y) ? index : NO_HIT;
    }
}
