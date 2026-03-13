/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal.engine;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Scale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class EngineChartHostTest {

    @Test
    void closingASynchronizedHostDetachesXAxisDelegation() {
        var masterHost = new EngineChartHost(new Scale());
        var indicatorHost = new EngineChartHost(new Scale());
        try {
            indicatorHost.chart().synchronizeAxis(masterHost.chart(), Axis.X_AXIS, true);

            indicatorHost.close();

            assertThatNoException()
                    .isThrownBy(() -> masterHost.chart().getXAxis().setVisibleRange(10.0, 42.0));
        } finally {
            masterHost.close();
        }
    }
}
