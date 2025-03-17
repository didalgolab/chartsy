/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.benchmarking;

import one.chartsy.SymbolIdentity;
import one.chartsy.core.event.ListenerList;
import one.chartsy.simulation.engine.SimulationAccount;
import one.chartsy.simulation.reporting.EquityInformation;
import one.chartsy.trade.*;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.LegacyPositionValueChangeListener;
import one.chartsy.trade.strategy.SimulatorOptions;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Beware that it's pretty-much DRAFT only.
@State(Scope.Thread)
public class PositionValueChangeListenerBenchmarkTest {

    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    long time = 0;
    Account account = new SimulationAccount(SimulatorOptions.builder().build());
    Position[] positions = new Position[10];
    ListenerList<LegacyPositionValueChangeListener> listeners = ListenerList.of(LegacyPositionValueChangeListener.class);

    double positionResult = 0;

    private EquityInformation.Builder equityBuilder = EquityInformation.builder(BalanceState.ZERO);

    @Benchmark
    @Warmup(time = 3)
    @Measurement(time = 10, iterations = 10)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public Double positionValueChanged() throws IOException {
        Position position = positions[rnd.nextInt(positions.length)];
        position.updateProfit(rnd.nextDouble(), time++);

        if (!listeners.isEmpty()) {
            listeners.fire().positionValueChanged(account, position);
        }
        count.incrementAndGet();
        return positionResult;
    }

    static AtomicLong count = new AtomicLong();
    long startNanos, startCount;
    long z;

    @TearDown(Level.Iteration)
    public void tearDown() {
        long elapsedTime = System.nanoTime() - startNanos;
        long deltaCount = count.get() - startCount;
        System.out.println("-- COUNT: " + deltaCount*1000_000_000L/elapsedTime + "/sec - z: "+z);
    }

    @Setup(Level.Iteration)
    public void setup() throws IOException {
        this.startCount = count.get();
        this.startNanos = System.nanoTime();
        this.time = 0;
        for (int i = 0; i < positions.length; i++) {
            SymbolIdentity symb = SymbolIdentity.of("S." + i);
            positions[i] = new Position(1, symb, Direction.LONG, 1.0, 2.0, new Order(symb, OrderType.MARKET, Order.Side.BUY), 0.0, -1);
        }
        listeners = ListenerList.of(LegacyPositionValueChangeListener.class);
        listeners.addListener(equityBuilder);
//        ByteBufferMutableHLCDataset equityDS = new ByteBufferMutableHLCDataset(1L, ByteBuffer.allocate(256_000));
//        listeners.addListener(new PositionValueChangeListener2() {
//
//            @Override
//            public void positionValueChanged(Account account, Position position) {
//                equityDS.add(position.getCurrTime(), account.getEquity());
//            }
//
//            @Override
//            public void positionValueChanged(PositionValueChangeEvent event) {
//                equityDS.add(event.getTime(), event.getAccount().getEquity());
//            }
//        });
        for (int i = 0; i < 1; i++) {
            int k = i;
            listeners.addListener(new LegacyPositionValueChangeListener() {
                @Override
                public void positionValueChanged(Account account, Position position) {
                    positionResult += k * k/2 * position.getProfit();
                }
            });
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(PositionValueChangeListenerBenchmarkTest.class.getName() + ".*")
                // Set the following options as needed
                .timeUnit(TimeUnit.MICROSECONDS)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .jvmArgsAppend("-XX:MaxDirectMemorySize=12g")
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                //.addProfiler(StackProfiler.class)
                .build();

        new Runner(opt).run();
    }

    public static interface LegacyPositionValueChangeListener2 extends LegacyPositionValueChangeListener {
        void positionValueChanged(Account account, Position position);
    }
}
