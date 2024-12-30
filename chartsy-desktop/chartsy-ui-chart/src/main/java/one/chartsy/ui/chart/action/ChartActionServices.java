/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import javax.swing.*;

public interface ChartActionServices {

    Action find(String name, Object... args);

    void execute(String action, Object... args);
}
