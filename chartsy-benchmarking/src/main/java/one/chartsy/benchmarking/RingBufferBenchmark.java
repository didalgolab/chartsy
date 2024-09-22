/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.base.function.IndexedConsumer;
import one.chartsy.data.structures.RingBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@State(Scope.Thread)
public class RingBufferBenchmark implements Consumer<Object>, IndexedConsumer<Object> {

    private RingBuffer<Candle> ringBuffer;
    private Candle[] preallocatedRandoms;
    private int index;
    private final int getIndex = ThreadLocalRandom.current().nextInt(RING_CAPACITY);
    private static final int PREALLOC_SIZE = 100_000_000;
    private static final int RING_CAPACITY = 1000;

    @Setup(Level.Trial)
    public void setUp() {
        ringBuffer = new RingBuffer<>(RING_CAPACITY);
        preallocatedRandoms = new Candle[PREALLOC_SIZE];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long time = System.currentTimeMillis();
        for (int i = 0; i < PREALLOC_SIZE; i++) {
            preallocatedRandoms[i] = Candle.of(time++, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble());
            ringBuffer.add(Candle.of(time++, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()));
        }
        index = 0;
    }

    private int totalHash;

    @Override
    public void accept(Object o) {
        totalHash += o.hashCode();
    }

    @Override
    public void accept(Object o, int index) {
        totalHash += o.hashCode();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void addAndGet(Blackhole blackhole) {
        ringBuffer.add(preallocatedRandoms[index]);
        if (++index >= PREALLOC_SIZE)
            index = 0;
        //index = (index + 1) % PREALLOC_SIZE; // wrap around to use the preallocated array circularly
        //return ringBuffer.get(getIndex);
        ringBuffer.forEach(this); // 168733,830 ± 3145,072  ops/s
        //ringBuffer.forEach2(this); // 174781,318 ± 1579,128  ops/s
        //ringBuffer.forEachIndexed(this); // 175122,853 ± 1216,645  ops/s
    }

    //@Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long newInstance() {
        return new RingBuffer<>().stream().count();
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(RingBufferBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }

    public static class RingBuffer0<T> {
        private final Object[] elements;
        private int head = 0;
        private int tail = 0;
        private final int maxSize;

        public RingBuffer0(int maxSize) {
            this.maxSize = maxSize;
            this.elements = new Object[maxSize];
        }

        public void add(T element) {
            elements[tail] = element;
            tail = (tail + 1) % maxSize;
            if (tail == head) {
                head = (head + 1) % maxSize; // Overwrite scenario
            }
        }

        public T get(int index) {
            return (T) elements[(head + index) % maxSize];
        }
    }
}