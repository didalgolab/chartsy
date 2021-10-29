package one.chartsy.trade;

import one.chartsy.data.Series;

import java.util.Collection;

public interface TradingUniverse {

    Collection<? extends Series<?>> dataSeries();

    static TradingUniverse of(Collection<? extends Series<?>> dataSeries) {
        return new TradingUniverses.ImmutableTradingUniverse(dataSeries);
    }
}
