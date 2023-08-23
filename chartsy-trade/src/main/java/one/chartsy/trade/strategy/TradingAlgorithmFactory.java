/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

@FunctionalInterface
public interface TradingAlgorithmFactory<T extends TradingAlgorithm> {

    T create(TradingAlgorithmContext context);
}
