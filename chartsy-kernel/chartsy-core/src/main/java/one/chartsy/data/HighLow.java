/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

/**
 * Represents a high/low range with a highest high and a lowest low value.
 */
public interface HighLow {

    double high();

    double low();

    static HighLow of(double high, double low) {
        return new Of(high, low);
    }

    record Of(double high, double low) implements HighLow {
    }
}