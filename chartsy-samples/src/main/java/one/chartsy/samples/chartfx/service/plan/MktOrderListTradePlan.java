/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.plan;

import static one.chartsy.samples.chartfx.service.StandardTradePlanAttributes.POSITIONS;

import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import one.chartsy.samples.chartfx.dos.OrderExpression;
import one.chartsy.samples.chartfx.dos.Position;
import one.chartsy.samples.chartfx.service.CalendarUtils;
import one.chartsy.samples.chartfx.service.OhlcvChangeListener;
import one.chartsy.samples.chartfx.service.execution.BasicOrderExecutionService;
import one.chartsy.samples.chartfx.service.execution.ExecutionResult;
import de.gsi.dataset.spi.financial.api.attrs.AttributeModel;
import de.gsi.dataset.spi.financial.api.ohlcv.IOhlcvItem;

/**
 * Simple example of trade plan for common execution of backtest market orders.
 *
 * @author afischer
 */
public class MktOrderListTradePlan implements OhlcvChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MktOrderListTradePlan.class);
    private final BasicOrderExecutionService orderExecutionService;
    private final AttributeModel context;
    private final String asset;
    private final List<SimMktOrder> orders;

    private int orderIdx = 0;
    private boolean nextOrder;
    private Calendar timestamp;
    private int quantity;

    public MktOrderListTradePlan(AttributeModel context, String asset,
            BasicOrderExecutionService orderExecutionService,
            List<SimMktOrder> orders) {
        this.context = context;
        this.asset = asset;
        this.orderExecutionService = orderExecutionService;
        this.orders = orders;
        nextOrder = !orders.isEmpty();
    }

    @Override
    public void tickEvent(IOhlcvItem ohlcvItem) throws Exception {
        if (nextOrder) {
            timestamp = CalendarUtils.createByDateTime(orders.get(orderIdx).timestamp);
            quantity = orders.get(orderIdx).buySell;
            nextOrder = false;
        }
        if (timestamp != null && ohlcvItem.getTimeStamp().getTime() >= timestamp.getTime().getTime()) {
            // core process - perform order
            ExecutionResult result = orderExecutionService.performOrder(ohlcvItem.getTimeStamp(), asset,
                    quantity > 0 ? OrderExpression.buyMarket(quantity) : OrderExpression.sellMarket(Math.abs(quantity)));
            // print orders and positions to log
            LOGGER.info("Last order=" + result.getOrder().toString());
            LOGGER.info(String.format("Positions=%n%s", context.getAttribute(POSITIONS).getPositionByMarketSymbol(asset).stream().map(Position::toString).collect(Collectors.joining(System.lineSeparator()))));
            // prepare to next order
            timestamp = null;
            orderIdx++;
            if (orderIdx < orders.size())
                nextOrder = true;
        }
    }

    public static class SimMktOrder {
        public String timestamp; // syntax yyyy/MM/dd HH:mm
        public int buySell; // -N sell contracts : +N buy contracts

        public SimMktOrder(String timestamp, int buySell) {
            this.timestamp = timestamp;
            this.buySell = buySell;
        }
    }
}
