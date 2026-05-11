/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

public final class PixelPerfectCandleGeometry {
    public static final int MIN_BODY_WIDTH = 1;
    public static final int MAX_BODY_WIDTH = 63;
    public static final int SLOT_GAP = 1;
    public static final int DEFAULT_BODY_WIDTH = 7;

    private PixelPerfectCandleGeometry() {
    }

    public static int snapBodyWidth(double candidateWidth) {
        int width = (int) Math.round(candidateWidth);
        width = Math.clamp(width, MIN_BODY_WIDTH, MAX_BODY_WIDTH);
        if ((width & 1) == 0) {
            if (width == MAX_BODY_WIDTH)
                width--;
            else
                width++;
        }
        return width;
    }

    public static int slotStep(int bodyWidth) {
        return snapBodyWidth(bodyWidth) + SLOT_GAP;
    }

    public static int slotStep(double bodyWidth) {
        return slotStep(snapBodyWidth(bodyWidth));
    }

    public static double fillPercent(int bodyWidth) {
        int normalizedBodyWidth = snapBodyWidth(bodyWidth);
        return normalizedBodyWidth * 100.0 / slotStep(normalizedBodyWidth);
    }

    public static double fillPercent(double bodyWidth) {
        return fillPercent(snapBodyWidth(bodyWidth));
    }

    public static double zoomIn(double currentBodyWidth) {
        return snapBodyWidth(snapBodyWidth(currentBodyWidth) + 2);
    }

    public static double zoomOut(double currentBodyWidth) {
        return snapBodyWidth(snapBodyWidth(currentBodyWidth) - 2);
    }
}
