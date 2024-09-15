/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

public interface MarketMessageHandler {

    void onMarketMessage(MarketMessage msg);
}
