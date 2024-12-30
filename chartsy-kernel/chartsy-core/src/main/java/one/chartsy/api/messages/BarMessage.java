/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.api.messages;

import one.chartsy.Candle;
import one.chartsy.messaging.MarketMessage;

public interface BarMessage extends MarketMessage {

    Candle bar();

    @Override
    default long getTime() {
        return bar().getTime();
    }
}
