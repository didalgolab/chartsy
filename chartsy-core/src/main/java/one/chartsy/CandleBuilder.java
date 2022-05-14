/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.market.Tick;

public interface CandleBuilder<C extends Candle, T extends Tick> extends Incomplete<C> {

    void clear();

    void addCandle(C candle);

    void addTick(T tick);
}