/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

import one.chartsy.time.Chronological;

public interface HighLowCandle extends Chronological {
    double high();
    double low();

    default double range() {
        return high() - low();
    }

    static HighLowCandle of(long time, double high, double low) {
        return new Of(time, high, low);
    }

    record Of(long getTime, double high, double low) implements HighLowCandle { }
}
