package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.When;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.random.RandomWalk;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationDriver;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.time.Chronological;
import one.chartsy.trade.MetaStrategy;
import one.chartsy.trade.Strategy;
import org.openide.util.Lookup;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

public class StrategyBenchmarkTest {

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public int simulationRound(BenchmarkState state) {
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        SimulationRunner runner = new SimpleSimulationRunner(context);
        AtomicLong cnt = new AtomicLong();
        DoubleAdder cnt2 = new DoubleAdder();
        SimulationDriver driver = new SimulationDriver() {
            @Override public void initSimulation(SimulationContext context) { }
            @Override public void onTradingDayStart(LocalDate date) { }
            @Override public void onTradingDayEnd(LocalDate date) { }
            @Override public void onData(When when, Chronological next, boolean timeTick) { }

            @Override
            public void onData(When when, Chronological last) {
                cnt.addAndGet(last.getTime());
                cnt2.add(((Candle) last).close());
            }
        };
        class MyStrategy extends Strategy<Candle> {
            @Override
            public void entryOrders(When when, Chronological data) {
                cnt.addAndGet(data.getTime());
                cnt2.add(((Candle) data).close());
                if (when.index() % 10 == 0) {
                    if (series.get(when.index()).isBullish())
                        buy();
                    else
                        sell();
                }
            }
        }
        int r = 0;
        ////for (int i = 0; i < 900; i++)
        r += runner.run(state.seriesList, new TradingSimulator(new MetaStrategy(MyStrategy::new))).remainingOrderCount();
        //runner.run(state.seriesList, driver);
        return cnt2.intValue() + r;
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class BenchmarkState
    {
        List<Integer> list;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Series<Candle>> seriesList = new ArrayList<>();

        @Setup(Level.Trial) public void
        initialize() {
            for (int i = 0; i < 1; i++) {
                List<Candle> candles = RandomWalk.candles(Duration.ofMinutes(5), LocalDateTime.of(1900, 1, 1, 0, 0))
                        .limit(12_000_000)
                        .collect(Collectors.toList());
                Collections.reverse(candles);
                seriesList.add(CandleSeries.of(SymbolResource.of("RANDOM", TimeFrame.Period.M15), candles));
            }
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(StrategyBenchmarkTest.class.getName() + ".*")
                // Set the following options as needed
                .mode (Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds(3))
                .measurementIterations(10)
                .threads(2)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                .addProfiler(StackProfiler.class)
                .build();

        new Runner(opt).run();
    }
}
