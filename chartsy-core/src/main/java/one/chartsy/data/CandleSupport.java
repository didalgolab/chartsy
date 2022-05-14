/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;

public final class CandleSupport {

    public static Candle merge(CandleSeries series) {
        var merger = SimpleCandleBuilder.create();
        for (int i = series.length() - 1; i >= 0; i--)
            merger.addCandle(series.get(i));

        return merger.get();
    }

    private CandleSupport() {}// cannot instantiate
}
