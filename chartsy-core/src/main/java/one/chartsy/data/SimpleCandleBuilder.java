/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.data.market.Tick;

public class SimpleCandleBuilder extends AbstractCandleBuilder<Candle, Tick> {

    @Override
    public SimpleCandle get() {
        return getAsSimpleCandle();
    }

    public static SimpleCandleBuilder create() {
        return new SimpleCandleBuilder();
    }
}
