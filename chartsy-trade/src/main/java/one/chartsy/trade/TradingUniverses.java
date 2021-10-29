package one.chartsy.trade;

import one.chartsy.data.Series;

import java.util.Collection;

public final class TradingUniverses {

    static record ImmutableTradingUniverse(Collection<? extends Series<?>> dataSeries) implements TradingUniverse { }

    private TradingUniverses() { }//cannot instantiate
}
