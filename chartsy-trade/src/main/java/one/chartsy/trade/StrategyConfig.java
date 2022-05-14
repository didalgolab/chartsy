/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import org.openide.util.Lookup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public interface StrategyConfig {

    Lookup lookup();

    SymbolIdentifier symbol();

    List<? extends Series<?>> dataSources();

    ConcurrentMap<String, Object> sharedVariables();

    Map<String, ?> properties();

    Account account();
}
