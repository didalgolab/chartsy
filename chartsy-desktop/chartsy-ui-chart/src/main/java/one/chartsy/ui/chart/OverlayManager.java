/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;

/**
 * Provides access to registered chart overlays.
 *
 * @author Mariusz Bernacki
 */
public class OverlayManager {

    public static OverlayManager getDefault() {
        return Holder.INSTANCE;
    }

    private OverlayManager() {
    }

    public Overlay getOverlay(String key) {
        return StudyRegistry.getDefault().getOverlay(key);
    }

    public List<Overlay> getOverlaysList() {
        return StudyRegistry.getDefault().getOverlaysList();
    }

    public List<String> getOverlays() {
        return StudyRegistry.getDefault().getOverlays();
    }

    private static final class Holder {
        private static final OverlayManager INSTANCE = new OverlayManager();
    }
}
