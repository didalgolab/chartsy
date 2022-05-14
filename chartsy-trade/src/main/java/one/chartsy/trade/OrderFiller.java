/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.Candle;

@FunctionalInterface
public interface OrderFiller {

	Execution fillOrder(Order order, Candle ohlc, double price);

}
