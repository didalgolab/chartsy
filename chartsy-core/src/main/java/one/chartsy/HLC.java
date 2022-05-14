/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.time.Chronological;

/**
 * Represents a triple of high-low-close aggregated snapshot of prices or values.
 * Usually aggregated over a specified time slot or at specific time.
 *
 * @author Mariusz Bernacki
 *
 */
public record HLC(long time, double high, double low, double close) implements Chronological {

    public HLC(long time, double value) {
        this(time, value, value, value);
    }

    @Override
    public long getTime() {
        return time;
    }
}
