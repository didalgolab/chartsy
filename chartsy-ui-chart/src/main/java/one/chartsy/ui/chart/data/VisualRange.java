/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.data;

import one.chartsy.core.Range;

public record VisualRange(Range range, boolean isLogarithmic) {

    public VisualRange(Range range) {
        this(range, false);
    }

    public double getMin() {
        return range().min();
    }

    public double getMax() {
        return range().max();
    }

    public VisualRange asLogarithmic() {
        if (isLogarithmic())
            return this;

        return new VisualRange(range, true);
    }
}
