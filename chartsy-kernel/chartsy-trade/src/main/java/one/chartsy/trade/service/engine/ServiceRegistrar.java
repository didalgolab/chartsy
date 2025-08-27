/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.engine;

import one.chartsy.service.Service;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.service.connector.TradeConnector;

public interface ServiceRegistrar {

    void addAlgorithm(Algorithm algorithm);

    void addTradeConnector(TradeConnector connector);

    void addCustomService(Service service);
}
