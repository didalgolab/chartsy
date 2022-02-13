package one.chartsy.trade;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.ExtendedCandle;
import one.chartsy.data.Series;
import one.chartsy.data.market.SimpleTick;
import one.chartsy.data.market.Tick;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openide.util.Lookup;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static one.chartsy.time.Chronological.toEpochMicros;
import static one.chartsy.trade.StrategyInstantiator.probeDataType;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaStrategyTest {
    static final SymbolResource<Candle> TEST_SYMBOL1 = SymbolResource.of("TEST_SYMBOL1", TimeFrame.Period.DAILY);
    static final SymbolResource<Candle> TEST_SYMBOL2 = SymbolResource.of("TEST_SYMBOL2", TimeFrame.Period.DAILY);


    @SuppressWarnings({"InstantiatingObjectToGetClassObject", "unchecked", "rawtypes"})
    @Test void probeDataType_gives_best_detected_data_point_type_of_Strategy() {
        class MyCandleStrategy extends Strategy<Candle> { }
        assertEquals(Candle.class, probeDataType(MyCandleStrategy.class));
        assertEquals(Candle.class, probeDataType(new MyCandleStrategy().getClass()));
        assertEquals(Candle.class, probeDataType((new MyCandleStrategy() { }).getClass()));
        assertEquals(ExtendedCandle.class,
                probeDataType((new Strategy<ExtendedCandle>() { }).getClass()));

        class MyParameterizedTickStrategy1<U,T extends Tick> extends Strategy<T> { }
        assertEquals(SimpleTick.class,
                probeDataType((new MyParameterizedTickStrategy1<Void, SimpleTick>() { }).getClass()));

        class MyParameterizedTickStrategy2<U,T extends Serializable & Cloneable & Tick> extends Strategy<T> { }
        assertEquals(Tick.class,
                probeDataType((Class) MyParameterizedTickStrategy2.class));
    }

    @ParameterizedTest
    @MethodSource("runners")
    void creates_separate_Strategy_instance_for_each_Symbol_in_a_Series(SimulationRunner runner) {
        var inputSeries = multiAssetSeriesOf(TEST_SYMBOL1, TEST_SYMBOL2);

        var usedSymbols = new ArrayList<>();
        var usedDatasets = new ArrayList<>();
        var usedDataTypes = new ArrayList<>();
        class MyStrategy extends Strategy<Candle> {
            {
                usedSymbols.add(this.symbol);
                usedDatasets.add(this.series);
                usedDataTypes.add(this.getPrimaryDataType());
            }
        }
        MetaStrategy metaStrategy = new MetaStrategy(MyStrategy::new);
        TradingSimulator simulator = new TradingSimulator(metaStrategy);
        runner.run(inputSeries, simulator);

        assertEquals(List.of(TEST_SYMBOL1.symbol(), TEST_SYMBOL2.symbol()), usedSymbols);
        assertEquals(inputSeries, usedDatasets);
        assertEquals(List.of(Candle.class, Candle.class), usedDataTypes);
    }


    static Candle candle() {
        return Candle.of(toEpochMicros(LocalDateTime.of(2021, 12, 2, 0, 0)), 1.0);
    }

    static Series<Candle> seriesOf(Candle... cs) {
        return CandleSeries.of(TEST_SYMBOL1, List.of(cs));
    }

    static List<Series<Candle>> multiAssetSeriesOf(SymbolResource... sr) {
        return Arrays.stream(sr)
                .map(res -> (Series<Candle>) CandleSeries.of(res, List.of(candle())))
                .toList();
    }

    static Stream<SimulationRunner> runners() {
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        return Stream.of(new SimpleSimulationRunner(context));
    }
}
