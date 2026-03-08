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

    private final ChartPluginRegistry<Overlay> overlays = new ChartPluginRegistry<>(Overlay.class);

    public static OverlayManager getDefault() {
        return Holder.INSTANCE;
    }

    private OverlayManager() {
    }

    public Overlay getOverlay(String key) {
        return overlays.get(key);
    }

    public List<Overlay> getOverlaysList() {
        return overlays.getPlugins();
    }

    public List<String> getOverlays() {
        return overlays.getNames();
    }

    private static final class Holder {
        private static final OverlayManager INSTANCE = new OverlayManager();
    }
}
