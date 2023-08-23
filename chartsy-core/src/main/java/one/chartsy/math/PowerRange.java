/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.math;

import java.util.stream.DoubleStream;

public interface PowerRange {

    static OfDouble of(double maximum) {
        return of(1.0, maximum);
    }

    static OfDouble of(double first, double maximum) {
        return of(first, maximum, 10.0);
    }

    static OfDouble of(double first, double maximum, double factor) {
        return new OfDouble(first, maximum, factor);
    }

    record OfDouble(double first, double maximum, double factor) implements PowerRange {

        public OfDouble {
            if (Math.signum(first) != Math.signum(factor))
                throw new IllegalArgumentException(
                        String.format("Arguments `first` (%s) and `maximum` (%s) must be both positive or both negative", first, maximum));

            if (factor <= 1.0)
                throw new IllegalArgumentException(
                        String.format("Argument `factor` (%s) must be greater than 1", factor));

        }

        public DoubleStream stream() {
            return DoubleStream.iterate(first, (next -> next <= maximum), (next -> next*factor));
        }
    }
}
