package one.chartsy.trade;

import one.chartsy.data.Series;
import org.immutables.value.Value;

import java.util.Collection;

@Value.Immutable
public interface TradingStrategyContext {

    Collection<? extends Series<?>> dataSeries();

    TradingService tradingService();

    TradingStrategyContext withTradingService(TradingService service);
}
