package one.chartsy.trade;

import one.chartsy.data.Series;
import org.immutables.value.Value;
import org.openide.util.Lookup;

import java.util.List;

@Value.Immutable
public interface TradingStrategyContext extends Lookup.Provider {

    List<? extends Series<?>> dataSeries();

    TradingService tradingService();

    TradingStrategyContext withTradingService(TradingService service);
}
