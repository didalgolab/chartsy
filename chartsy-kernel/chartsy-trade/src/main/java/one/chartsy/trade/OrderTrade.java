/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.SymbolIdentity;

public interface OrderTrade {

    long time();

    boolean isBuy();

    SymbolIdentity symbol();

    double tradePrice();

    double tradeQuantity();
}
