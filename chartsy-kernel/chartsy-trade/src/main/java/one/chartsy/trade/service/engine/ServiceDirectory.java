/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.engine;

import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.OrderRequestHandler;

public interface ServiceDirectory {

    OrderRequestHandler getOrderRequestService(String id);

    OrderReportHandler getOrderReportService(String id);
}
