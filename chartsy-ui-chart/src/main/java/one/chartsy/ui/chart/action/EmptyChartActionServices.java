/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import javax.swing.*;
import java.awt.event.ActionEvent;

class EmptyChartActionServices implements ChartActionServices {

    @Override
    public Action find(String name, Object... args) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                execute(name, args);
            }
        };
    }

    @Override
    public void execute(String action, Object... args) {
        throw new UnsupportedOperationException();
    }
}
