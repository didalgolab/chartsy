/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.trade.strategy.ExitState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.openide.util.Lookup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static one.chartsy.time.Chronological.toEpochNanos;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

class SimulationRunnerTest {
    static final SymbolResource<Candle> TEST_SYMBOL = SymbolResource.of("TEST_SYMBOL", TimeFrame.Period.DAILY);
    final CandleSeries emptySeries = CandleSeries.of(TEST_SYMBOL, List.of());
    final CandleSeries series = CandleSeries.of(TEST_SYMBOL, List.of(
            Candle.of(3L, 1),
            Candle.of(2L, 2),
            Candle.of(1L, 3)
    ));

    @ParameterizedTest
    @MethodSource("runners")
    void under_construction(SimulationRunner runner) {
        SimulationDriver myStrategy = Mockito.spy(SimulationDriver.class);

        runner.run(List.of(series), myStrategy);
        var order = inOrder(myStrategy);
        order.verify(myStrategy).initSimulation(any());
        order.verify(myStrategy).postSimulation(ExitState.COMPLETED);
    }

    @ParameterizedTest
    @MethodSource("runners")
    void run_pushes_data_points_from_given_Series_to_SimulationDriver(SimulationRunner runner) {
        SimulationDriver simDriver = Mockito.spy(SimulationDriver.class);
        Candle dataPoint = candle();
        LocalDate dataPointDate = dataPoint.getDate();

        runner.run(seriesOf(dataPoint), simDriver);

        var order = inOrder(simDriver);
        order.verify(simDriver).initSimulation(any());
        order.verify(simDriver).onTradingDayChange(null, dataPointDate);
        order.verify(simDriver).onData(any(), same(dataPoint));
        order.verify(simDriver).onTradingDayChange(dataPointDate, null);
        order.verify(simDriver).postSimulation(ExitState.COMPLETED);
        Mockito.verifyNoMoreInteractions(simDriver);
    }

    @ParameterizedTest
    @MethodSource("runners")
    void run_passes_given_Series_in_SimulationContext(SimulationRunner runner) {
        SimulationDriver simDriver = Mockito.spy(SimulationDriver.class);
        List<Series<Candle>> series = List.of(seriesOf(candle()));

        runner.run(series, simDriver);
        verify(simDriver).initSimulation(
                argThat(
                        context -> series.equals(List.copyOf(context.partitionSeries().values()))));
    }

    @ParameterizedTest
    @MethodSource("runners")
    void gives_SimulationResult_even_when_given_empty_dataset(SimulationRunner runner) {
        List<CandleSeries> datasets = List.of(emptySeries);
        SimulationDriver strategy = Mockito.spy(SimulationDriver.class);

        SimulationResult result = runner.run(datasets, strategy);
        assertNotNull(result, "SimulationResult");
    }

    static Candle candle() {
        return Candle.of(toEpochNanos(LocalDateTime.of(2021, 12, 2, 0, 0)), 1.0);
    }

    static Series<Candle> seriesOf(Candle... cs) {
        return CandleSeries.of(TEST_SYMBOL, List.of(cs));
    }

    static Stream<SimulationRunner> runners() {
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        return Stream.of(new SimpleSimulationRunner(context));
    }
}