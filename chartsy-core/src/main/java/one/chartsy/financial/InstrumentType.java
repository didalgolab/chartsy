/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

/**
 * The type of financial instrument that is priced and quoted in the market.
 *
 * @author Mariusz Bernacki
 *
 */
public enum InstrumentType implements IdentityType {
    EQUITY,
    OPTION,
    FUTURE,
    BOND,
    FX,
    ETF,
    CFD,
    COMBINATION,
    CRYPTOCURRENCY,
    INDEX,
    CUSTOM;

    @Override
    public boolean isTradable() {
        return INDEX != this;
    }
}