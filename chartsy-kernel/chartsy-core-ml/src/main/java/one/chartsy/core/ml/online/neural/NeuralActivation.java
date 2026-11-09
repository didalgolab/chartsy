/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

/** Smooth scalar responses used by the small regression network. */
public enum NeuralActivation {
    LINEAR {
        @Override public double value(double x) { return x; }
        @Override public double derivativeFromOutput(double y) { return 1; }
    },
    LOGISTIC {
        @Override public double value(double x) { return 1 / (1 + Math.exp(-x)); }
        @Override public double derivativeFromOutput(double y) { return y * (1 - y); }
    },
    FAST_SIGMOID {
        @Override public double value(double x) { return x / (1 + Math.abs(x)); }
        @Override public double derivativeFromOutput(double y) {
            double remaining = 1 - Math.abs(y);
            return remaining * remaining;
        }
    },
    TANH {
        @Override public double value(double x) { return Math.tanh(x); }
        @Override public double derivativeFromOutput(double y) { return 1 - y * y; }
    };

    public abstract double value(double x);
    public abstract double derivativeFromOutput(double output);
}
