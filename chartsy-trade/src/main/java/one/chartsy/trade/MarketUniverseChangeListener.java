/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

@FunctionalInterface
public interface MarketUniverseChangeListener {

    void onMarketUniverseChange(MarketUniverseChangeEvent change);
}