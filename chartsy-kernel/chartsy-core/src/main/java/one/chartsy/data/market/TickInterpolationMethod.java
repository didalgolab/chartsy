/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.market;

public enum TickInterpolationMethod {
    CUBIC_SPLINE("Cubic Spline"),
    FOUR_TICKS_AT_OHLC("4 ticks at OHLC"),
    TICK_ON_OPEN("Tick on Open"),
    TICK_ON_CLOSE("Tick on Close");

    private final String description;

    TickInterpolationMethod(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}