package one.chartsy.benchmarking.service.invoker;

import one.chartsy.core.event.AbstractInvoker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

public class AbstractInvoker1Benchmark {

    public interface TestHandler1 {
        void handle1(String message, Blackhole blackhole);
    }

    public interface TestHandler2 {
        void handle2(String message, Blackhole blackhole);
    }

    public static class TestService1 implements TestHandler1, TestHandler2 {
        @Override
        public void handle1(String message, Blackhole blackhole) {
            blackhole.consume(message);
        }

        @Override
        public void handle2(String message, Blackhole blackhole) {
            blackhole.consume(message);
        }
    }

    public static class TestService2 implements TestHandler2, TestHandler1 {
        @Override
        public void handle2(String message, Blackhole blackhole) {
            blackhole.consume(message);
        }

        @Override
        public void handle1(String message, Blackhole blackhole) {
            blackhole.consume(message);
        }
    }

    public static class TestInvoker extends AbstractInvoker {
        private TestHandler1 handler1 = getListenerList(TestHandler1.class).fire();
        private TestHandler2 handler2 = getListenerList(TestHandler2.class).fire();

        public TestInvoker(Object primaryService) {
            super(primaryService);
        }

        public void invokeHandler(String message, Blackhole blackhole) {
            handler1.handle1(message, blackhole);
            handler2.handle2(message, blackhole);
        }
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public TestInvoker invoker;
        public TestService1 service1;
        public TestService2 service2;
        public String testMessage = "test message";

        @Setup(Level.Trial)
        public void setup() {
            service1 = new TestService1();
            service2 = new TestService2();
            invoker = new TestInvoker(service1);
            invoker.addService(service1);
            invoker.addService(service2);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void benchmarkInvokerMethodCall(BenchmarkState state, Blackhole blackhole) {
        state.invoker.invokeHandler(state.testMessage, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void benchmarkDirectMethodCall(BenchmarkState state, Blackhole blackhole) {
        state.service1.handle1(state.testMessage, blackhole);
        state.service1.handle2(state.testMessage, blackhole);
        state.service2.handle2(state.testMessage, blackhole);
        state.service2.handle1(state.testMessage, blackhole);
    }

//    public static class Main {
        public static void main(String[] args) throws RunnerException {
            Options opt = new OptionsBuilder()
                    // Specify which benchmarks to run.
                    // You can be more specific if you'd like to run only one benchmark per test.
                    .include(AbstractInvoker1Benchmark.class.getName() + ".*")
                    // Set the following options as needed
                    .warmupTime(TimeValue.seconds(1))
                    .warmupIterations(3)
                    .measurementTime(TimeValue.seconds(3))
                    .measurementIterations(10)
                    .threads(1)
                    .forks(1)
                    .shouldFailOnError(true)
                    .shouldDoGC(true)
                    .addProfiler(JavaFlightRecorderProfiler.class)
                    .build();
            new Runner(opt).run();
        }
//    }
}