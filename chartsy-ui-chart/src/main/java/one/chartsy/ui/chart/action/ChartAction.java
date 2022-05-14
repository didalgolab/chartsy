/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.util.ServiceLookup;

import javax.swing.*;

public interface ChartAction {

    private static ChartActionServices actions() {
        return ServiceLookup.getOrDefault(ChartActionServices.class, EmptyChartActionServices::new);
    }

    static Action find(String name, Object... args) {
        return actions().find(name, args);
    }

    static void openSettingsWindow(Indicator indicator) {
        actions().execute("SettingsWindowOpen", indicator);
    }

    static void openSettingsWindow(Overlay overlay) {
        actions().execute("SettingsWindowOpen", overlay);
    }
}
