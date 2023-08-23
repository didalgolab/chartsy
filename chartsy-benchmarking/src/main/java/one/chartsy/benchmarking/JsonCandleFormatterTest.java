/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.benchmarking;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import one.chartsy.Candle;
import one.chartsy.random.RandomWalk;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JsonCandleFormatterTest {

    @State(Scope.Thread)
    public static class BenchmarkState {
        List<Candle> candles;
        Gson gson = new Gson();
        ObjectMapper mapper = new ObjectMapper();

        @Setup(Level.Trial)
        public void initialize() {
            mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
            candles = RandomWalk.candles(Duration.ofMinutes(15), LocalDateTime.of(1900, 1, 1, 0, 0))
                    .limit(1000)
                    .collect(Collectors.toList());
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public Object candleToJsonUsingJackson(BenchmarkState state) throws JsonProcessingException {
        List<String> jsons = new LinkedList<>();
        for (Candle c : state.candles)
            jsons.add(state.mapper.writeValueAsString(c));

        return jsons;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public Object candleToJsonUsingGson(BenchmarkState state) throws JsonProcessingException {
        List<String> jsons = new LinkedList<>();
        for (Candle c : state.candles)
            jsons.add(state.gson.toJson(c));

        return jsons;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public Object candleToString(BenchmarkState state) throws JsonProcessingException {
        List<String> jsons = new LinkedList<>();
        for (Candle c : state.candles)
            jsons.add(c.toString());

        return jsons;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(JsonCandleFormatterTest.class.getName() + ".*")
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
