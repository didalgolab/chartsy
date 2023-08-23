/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.random;

public record RandomCandleSpecification(
        double gappiness, double drift, double stddev
) {

    public RandomCandleSpecification() {
        this(0.0, 0.0, 1.0);
    }

    public RandomCandleSpecification withGappiness(double gappiness) {
        return new RandomCandleSpecification(gappiness, drift, stddev);
    }

    public RandomCandleSpecification withDistribution(double drift, double stddev) {
        return new RandomCandleSpecification(gappiness, drift, stddev);
    }

    static final RandomCandleSpecification BASIC = new RandomCandleSpecification();

}
