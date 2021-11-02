package one.chartsy.trade.strategy;

import one.chartsy.scheduling.EventScheduler;
import one.chartsy.time.Clock;
import one.chartsy.trade.TradingOptions;
import one.chartsy.trade.TradingService;
import org.immutables.value.Value;
import org.openide.util.Lookup;

import java.time.ZoneId;

@Value.Immutable
public interface TradingAgentRuntime extends Lookup.Provider {

    Clock clock();

    EventScheduler scheduler();

    TradingService tradingService();

    TradingOptions options();


    TradingAgentRuntime withClock(Clock clock);

    default TradingAgentRuntime withClockAtZone(ZoneId zone) {
        return withClock(clock().withZone(zone));
    }

    TradingAgentRuntime withScheduler(EventScheduler scheduler);

}
