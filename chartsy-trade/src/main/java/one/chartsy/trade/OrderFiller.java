package one.chartsy.trade;

import one.chartsy.Candle;

@FunctionalInterface
public interface OrderFiller {

	Execution fillOrder(Order order, Candle ohlc, double price);

}
