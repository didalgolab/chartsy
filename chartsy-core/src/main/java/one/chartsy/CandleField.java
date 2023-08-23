/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.function.ToDoubleFunction;

public enum CandleField implements FinancialField, ToDoubleFunction<Candle> {
    TIME (Candle::getTime),
    OPEN (Candle::open),
    HIGH (Candle::high),
    LOW (Candle::low),
    CLOSE (Candle::close),
    VOLUME (Candle::volume),
    COUNT (Candle::count),
    ;

    CandleField(ToDoubleFunction<Candle> doubleValue) {
        this.doubleValue = doubleValue;
    }

    private final ToDoubleFunction<Candle> doubleValue;

    @Override
    public double applyAsDouble(Candle c) {
        return getFrom(c);
    }

    public double getFrom(Candle c) {
        return doubleValue.applyAsDouble(c);
    }
}
