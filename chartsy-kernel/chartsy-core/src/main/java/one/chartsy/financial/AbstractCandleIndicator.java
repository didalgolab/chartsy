/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

import one.chartsy.Candle;

import java.util.function.Consumer;

/**
 * Abstract base class for indicators that consume {@link Candle} objects and
 * produce single double-valued output only.
 */
public abstract class AbstractCandleIndicator implements ValueIndicator.OfDouble, Consumer<Candle> {

    /**
     * Processes a new {@link Candle} object for the indicator.
     *
     * @param bar the new data bar to process
     */
    @Override
    public abstract void accept(Candle bar);
}