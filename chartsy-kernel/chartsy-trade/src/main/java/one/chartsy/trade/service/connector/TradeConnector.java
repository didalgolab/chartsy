/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.service.Service;
import one.chartsy.trade.service.OrderHandler;

public interface TradeConnector extends Service, OrderHandler {

    String getId();
}
