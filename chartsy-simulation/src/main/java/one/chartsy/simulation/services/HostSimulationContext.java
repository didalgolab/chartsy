package one.chartsy.simulation.services;

import one.chartsy.data.Series;
import one.chartsy.simulation.ImmutableSimulationContext;
import one.chartsy.simulation.ImmutableSimulationProperties;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationProperties;
import one.chartsy.trade.Account;
import one.chartsy.trade.OrderBroker;
import one.chartsy.trade.TradingService;
import one.chartsy.trade.TradingStrategyContext;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.Collection;
import java.util.List;

@ServiceProvider(service = SimulationContext.class)
public class HostSimulationContext implements SimulationContext {

    @Override
    public SimulationProperties properties() {
        return ImmutableSimulationProperties.builder().build();
    }

    @Override
    public Collection<? extends Series<?>> dataSeries() {
        return List.of();
    }

    @Override
    public TradingService tradingService() {
        return FakeTradingService.instance;
    }

    @Override
    public TradingStrategyContext withTradingService(TradingService service) {
        return ImmutableSimulationContext.builder()
                .from(this)
                .tradingService(service)
                .build();
    }

    @Override
    public SimulationContext withDataSeries(Collection<? extends Series<?>> ds) {
        return ImmutableSimulationContext.builder()
                .from(this)
                .dataSeries(ds)
                .build();
    }

    private static class FakeTradingService implements TradingService {
        private static final FakeTradingService instance = new FakeTradingService();

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
