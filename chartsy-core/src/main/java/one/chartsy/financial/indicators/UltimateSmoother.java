/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import lombok.Getter;
import one.chartsy.data.structures.RingBuffer;

public class UltimateSmoother {
    private final double a1, b1, c1, c2, c3;
    private final RingBuffer.OfDouble prices;
    private final RingBuffer.OfDouble values;
    @Getter
    private double last = Double.NaN;

    public UltimateSmoother(int period) {
        this.prices = new RingBuffer.OfDouble(2);
        this.values = new RingBuffer.OfDouble(2);
        double sqrt2 = Math.sqrt(2);
        this.a1 = Math.exp(-sqrt2 * Math.PI / period);
        this.b1 = 2 * a1 * Math.cos(sqrt2 * Math.PI / period);
        this.c2 = b1;
        this.c3 = -a1 * a1;
        this.c1 = (1 + c2 - c3) / 4;
    }

    public double smooth(double price) {
        if (!values.isFull()) {
            prices.add(price);
            values.add(price);
            last = price;
            return price;
        }

        double value = (1 - c1) * price
                + (2 * c1 - c2) * prices.get(0)
                - (c1 + c3) * prices.get(1)
                + c2 * values.get(0)
                + c3 * values.get(1);

        prices.add(price);
        values.add(value);
        last = value;
        return value;
    }
}