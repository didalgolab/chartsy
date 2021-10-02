package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import org.openide.util.Lookup;

import javax.swing.*;

public interface ChartAction {

    private static ChartActionServices actions() {
        return Lookup.getDefault().lookup(ChartActionServices.class);
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
