package one.chartsy.ui.chart.action;

import javax.swing.*;

public interface ChartActionServices {

    Action find(String name, Object... args);

    void execute(String action, Object... args);
}
