package one.chartsy.trade;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.When;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openide.util.Lookup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static one.chartsy.time.Chronological.toEpochMicros;
import static org.junit.jupiter.api.Assertions.*;

public class StrategyTest {
    static final SymbolResource<Candle> TEST_SYMBOL = SymbolResource.of("TEST_SYMBOL", TimeFrame.Period.DAILY);

    @ParameterizedTest
    @MethodSource("runners")
    void gives_proper_SimulationResult_when_backtested(SimulationRunner runner) {
        var c1 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 1, 0, 0)), 1.1, 1.9, 1.0, 1.8);
        var c2 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 2, 0, 0)), 2.1, 2.9, 2.0, 2.8);
        var c3 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 3, 0, 0)), 3.1, 3.9, 3.0, 3.8);
        var inputSeries = seriesOf(c3, c2, c1);

        class MyStrategy extends Strategy<Candle> {
            @Override
            public void entryOrders(When when, Chronological data) {
                assertFalse(isOnMarket(), "not on market");
                assertFalse(isLongOnMarket(), "not long on market");
                assertFalse(isShortOnMarket(), "not short on market");
                assertNull(account.getInstrument(symbol).position(), "no open positions");
            }
        }
        MetaStrategy metaStrategy = new MetaStrategy(MyStrategy::new);
        TradingSimulator simulator = new TradingSimulator(metaStrategy);
        SimulationResult result = runner.run(inputSeries, simulator);

        assertEquals(SimulationResult.State.READY, result.state());
        assertEquals(inputSeries.length(), result.estimatedDataPointCount());
        System.out.println(result.testDuration());
    }

    @ParameterizedTest
    @MethodSource("runners")
    void under_construction(SimulationRunner runner) {
        var c1 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 1, 0, 0)), 1.1, 1.9, 1.0, 1.8);
        var c2 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 2, 0, 0)), 2.1, 2.9, 2.0, 2.8);
        var c3 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 3, 0, 0)), 3.1, 3.9, 3.0, 3.8);
        var inputSeries = seriesOf(c3, c2, c1);

        class MyStrategy extends Strategy<Candle> {
            @Override
            public void entryOrders(When when, Chronological data) {
                if (!isOnMarket()) {
                    buy();
                }
                if (when.index() < inputSeries.length() - 1) {
                    assertTrue(isOnMarket(), "isOnMarket after trade");
                    assertTrue(isLongOnMarket(), "isLongOnMarket after trade");
                    Position position = account.getInstrument(symbol).position();
                    assertNotNull(position);
                    assertEquals(Direction.LONG, position.getDirection(), "opened position direction");
                    assertEquals(c2.open(), position.getEntryPrice(), "position open price");
                    assertEquals(c2.getTime(), position.getEntryTime(), "position open time");

                    System.out.println(Chronological.toDateTime(position.getEntryOrder().getAcceptedTime()));
                }
            }
        }
        MetaStrategy metaStrategy = new MetaStrategy(MyStrategy::new);
        TradingSimulator simulator = new TradingSimulator(metaStrategy);
        runner.run(inputSeries, simulator);

        assertEquals(0, simulator.getMainAccount().getPendingOrders().size(), "remained pending orders");
        var entryPrice = c2.open();
        var lastPrice = c3.close();
        assertEquals((lastPrice - entryPrice), simulator.getMainAccount().getEquity(), "account balance after simulation");
    }

    @ParameterizedTest
    @MethodSource("runners")
    void can_open_positions_using_MarketOrder_with_stop_loss(SimulationRunner runner) {
        var c1 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 1, 0, 0)), 1.1, 1.9, 1.0, 1.8);
        var c2 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 2, 0, 0)), 2.1, 2.9, 2.0, 2.8);
        var c3 = Candle.of(toEpochMicros(LocalDateTime.of(2021, 1, 3, 0, 0)), 3.1, 3.9, 3.0, 3.8);
        var inputSeries = seriesOf(c3, c2, c1);
        var STOP_LOSS = 2.05;

        class MyStrategy extends Strategy<Candle> {
            final AtomicBoolean buyOnce = new AtomicBoolean();

            @Override
            public void entryOrders(When when, Chronological data) {
                if (!buyOnce.getAndSet(true))
                    buy().setExitStop(STOP_LOSS);

                assertFalse(isOnMarket(), "not on market because of same-bar-exit");
            }
        }
        MetaStrategy metaStrategy = new MetaStrategy(MyStrategy::new);
        TradingSimulator simulator = new TradingSimulator(metaStrategy);
        runner.run(inputSeries, simulator);

        var entryPrice = c2.open();
        var exitPrice = STOP_LOSS;
        assertEquals((exitPrice - entryPrice), simulator.getMainAccount().getEquity(), "balance after simulation");
    }

    static Series<Candle> seriesOf(Candle... cs) {
        return CandleSeries.of(TEST_SYMBOL, List.of(cs));
    }

    static Stream<SimulationRunner> runners() {
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        return Stream.of(new SimpleSimulationRunner(context));
    }
}
