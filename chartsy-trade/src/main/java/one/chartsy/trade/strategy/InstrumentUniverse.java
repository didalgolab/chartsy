/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

public interface InstrumentUniverse {

    int totalSymbolCount();

    int activeSymbolCountSince(long lastTradeTime);

}
