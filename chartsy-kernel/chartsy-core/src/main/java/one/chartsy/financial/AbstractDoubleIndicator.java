/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

import java.util.function.DoubleConsumer;

/**
 * Abstract base class for indicators that consume double values and
 * produce single double-valued output only.
 */
public abstract class AbstractDoubleIndicator implements ValueIndicator.OfDouble, DoubleConsumer {

    /**
     * Processes a new input value for the indicator.
     *
     * @param value the new input value to be processed
     */
    @Override
    public abstract void accept(double value);
}