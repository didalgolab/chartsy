/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.event;

import one.chartsy.trade.account.AccountBalanceEntry;

/**
 * Receives notifications of changed position value as a result of market price change
 * (either in a live or simulated account).
 *
 * @author Mariusz Bernacki
 */
@FunctionalInterface
public interface PositionValueChangeListener {

    void positionValueChanged(AccountBalanceEntry.Position position);
}
