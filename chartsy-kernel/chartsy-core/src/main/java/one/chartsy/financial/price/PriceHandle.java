/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.price;

import one.chartsy.SymbolIdentity;

public interface PriceHandle {

    SymbolIdentity getSymbol();

    double getLastTradePrice();

    long getLastTradeTime();
}
