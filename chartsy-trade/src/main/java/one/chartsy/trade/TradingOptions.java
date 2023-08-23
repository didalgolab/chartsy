/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.trade.services.DefaultTradingOptionsProvider;
import org.immutables.value.Value;
import org.openide.util.Lookup;

import java.util.Map;

@Value.Immutable
public interface TradingOptions {

    double customValue1();

    double customValue2();

    Map<String, ?> globalVariables();

    double simulationStartingEquity();

    static TradingOptions getDefault() {
        return Lookup.getDefault().lookup(DefaultTradingOptionsProvider.class).getTradingOptions();
    }
}
