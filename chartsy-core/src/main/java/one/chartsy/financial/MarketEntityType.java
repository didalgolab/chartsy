/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

/**
 * The type of non-priced entities within the trading system.
 *
 * @author Mariusz Bernacki
 *
 */
public enum MarketEntityType implements IdentityType {
    EXCHANGE,
    TRADING_SESSION,
    SYSTEM;

    @Override
    public boolean isTradable() {
        return false;
    }
}