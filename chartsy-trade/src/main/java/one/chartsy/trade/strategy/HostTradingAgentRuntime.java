package one.chartsy.trade.strategy;

import one.chartsy.scheduling.EventScheduler;
import one.chartsy.time.Clock;
import one.chartsy.trade.Account;
import one.chartsy.trade.OrderBroker;
import one.chartsy.trade.TradingOptions;
import one.chartsy.trade.TradingService;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@ServiceProvider(service = TradingAgentRuntime.class)
public class HostTradingAgentRuntime implements TradingAgentRuntime {

    private final Clock hostClock = Clock.systemDefaultZone();
    private final EventScheduler scheduler = new Scheduler();

    @Override
    public Clock clock() {
        return hostClock;
    }

    @Override
    public EventScheduler scheduler() {
        return scheduler;
    }

    @Override
    public TradingService tradingService() {
        return FakeTradingService.INSTANCE;
    }

    @Override
    public TradingOptions options() {
        return TradingOptions.getDefault();
    }

    @Override
    public Lookup getLookup() {
        return Lookup.getDefault();
    }

    @Override
    public TradingAgentRuntime withClock(Clock clock) {
        return ImmutableTradingAgentRuntime.builder().from(this)
                .clock(clock)
                .build();
    }

    @Override
    public TradingAgentRuntime withScheduler(EventScheduler scheduler) {
        return ImmutableTradingAgentRuntime.builder().from(this)
                .scheduler(scheduler)
                .build();
    }

    /**
     * Class-private scheduler.
     */
    final class Scheduler implements EventScheduler {
        @Override
        public void schedule(long triggerTime, Runnable event) {
            long delay = triggerTime - hostClock.time();
            executor.schedule(event, delay, TimeUnit.MICROSECONDS);
        }

        static final class DaemonThreadFactory implements ThreadFactory {
            public Thread newThread(Runnable r) {
                var t = new Thread(r);
                t.setDaemon(true);
                t.setName("HostTradingAgentRuntimeScheduler");
                return t;
            }
        }

        static final ScheduledThreadPoolExecutor executor;
        static {
            (executor = new ScheduledThreadPoolExecutor(
                    1, new DaemonThreadFactory())).
                    setRemoveOnCancelPolicy(true);
        }
    }

    private static class FakeTradingService implements TradingService {
        private static final FakeTradingService INSTANCE = new FakeTradingService();

        @Override
        public OrderBroker getOrderBroker() {
            throw new UnsupportedOperationException("OrderBroker");
        }

        @Override
        public List<Account> getAccounts() {
            return List.of();
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }
    }
}
