/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.Manageable;
import one.chartsy.messaging.MarketMessageHandler;

public interface MarketSupplier extends Manageable {

    int poll(MarketMessageHandler handler, int messageLimit);
}
