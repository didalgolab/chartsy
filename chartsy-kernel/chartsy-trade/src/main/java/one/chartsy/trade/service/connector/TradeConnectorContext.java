/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.financial.price.MarketPriceService;
import one.chartsy.time.Clock;
import one.chartsy.trade.service.OrderReportHandler;

public class TradeConnectorContext {

    private final String id;
    private final Clock clock;
    private final MarketPriceService priceService;
    private final OrderReportHandler orderReportHandler;

    public TradeConnectorContext(String id, Clock clock, MarketPriceService priceService, OrderReportHandler orderReportHandler) {
        this.id = id;
        this.clock = clock;
        this.priceService = priceService;
        this.orderReportHandler = orderReportHandler;
    }

    public final String getId() {
        return id;
    }

    public final Clock getClock() {
        return clock;
    }

    public final MarketPriceService getPriceService() {
        return priceService;
    }

    public final OrderReportHandler getOrderReportHandler() {
        return orderReportHandler;
    }
}
