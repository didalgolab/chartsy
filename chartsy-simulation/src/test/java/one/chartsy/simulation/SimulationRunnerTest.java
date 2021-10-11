package one.chartsy.simulation;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.simulation.impl.SimpleSimulationRunner;
import one.chartsy.trade.TradingStrategyContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SimulationRunnerTest {
    final SymbolResource<Candle> TEST_SYMBOL = SymbolResource.of("TEST_SYMBOL", TimeFrame.Period.DAILY);
    final CandleSeries emptySeries = CandleSeries.of(TEST_SYMBOL, List.of());

    @ParameterizedTest
    @MethodSource("runners")
    void gives_SimulationResult_even_when_given_empty_dataset(SimulationRunner runner) {
        List<CandleSeries> datasets = List.of(emptySeries);
        SimulationDriver strategy = Mockito.spy(SimulationDriver.class);

        SimulationResult result = runner.run(datasets, strategy);
        assertNotNull(result, "SimulationResult");
    }

    static Stream<SimulationRunner> runners() {
        TradingStrategyContext context = new TradingStrategyContext() {};
        return Stream.of(new SimpleSimulationRunner(context));
    }
}