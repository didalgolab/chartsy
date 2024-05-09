/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import lombok.Getter;
import one.chartsy.base.function.ThrowingRunnable;

@Getter
public class ChartRenderingSystem {
    private static final ChartRenderingSystem current = new ChartRenderingSystem();

    public static ChartRenderingSystem current() {
        return current;
    }

    protected boolean rerender = true;

    public <T extends Exception> void withoutRerender(ThrowingRunnable<T> action) throws T {
        try {
            this.rerender = false;
            action.run();
        } finally {
            this.rerender = true;
        }
    }
}
