/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.api.messages;

import one.chartsy.Candle;
import one.chartsy.messaging.MarketEvent;

public interface BarMessage extends MarketEvent {

    Candle bar();

    @Override
    default long getTime() {
        return bar().getTime();
    }
}
