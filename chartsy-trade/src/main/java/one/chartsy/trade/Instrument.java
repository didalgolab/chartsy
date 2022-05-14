/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.data.Position;

import java.util.List;

public interface Instrument {

    SymbolIdentity getSymbol();

    List<Order> orders();

    Position position();

    boolean isActive();

    boolean isActiveSince(long lastTradeTime);

}
