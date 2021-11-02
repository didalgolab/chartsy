package one.chartsy.trade.services;

import one.chartsy.trade.ImmutableTradingOptions;
import one.chartsy.trade.TradingOptions;
import org.openide.util.lookup.ServiceProvider;

import java.util.Collections;

@ServiceProvider(service = DefaultTradingOptionsProvider.class)
public class DefaultTradingOptionsProvider implements TradingOptionsProvider {

    private TradingOptions cached;

    @Override
    public TradingOptions getTradingOptions() {
        if (cached == null)
            cached = getTradingOptionsBuilder().build();
        return cached;
    }

    public ImmutableTradingOptions.Builder getTradingOptionsBuilder() {
        return ImmutableTradingOptions.builder()
                .customValue1(0.0)
                .customValue2(0.0)
                .globalVariables(Collections.emptyMap())
                .simulationStartingEquity(10000.0)
                ;
    }
}
