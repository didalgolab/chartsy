/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking;

import one.chartsy.base.RingBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class RingBufferOfIntAddAndGetBenchmark {

    private RingBuffer.OfInt ringBuffer;
    private int[] preallocatedRandoms;
    private int index;
    private final int getIndex = ThreadLocalRandom.current().nextInt() % RING_CAPACITY;
    private static final int PREALLOC_SIZE = 10_000_000;
    private static final int RING_CAPACITY = 1000;

    @Setup(Level.Trial)
    public void setUp() {
        ringBuffer = new RingBuffer.OfInt(RING_CAPACITY);
        preallocatedRandoms = new int[PREALLOC_SIZE];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < PREALLOC_SIZE; i++) {
            preallocatedRandoms[i] = random.nextInt();
            ringBuffer.add(random.nextInt());
        }
        index = 0;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public int testAdd() {
        ringBuffer.add(preallocatedRandoms[index]);
        if (++index >= PREALLOC_SIZE)
            index = 0;
        //index = (index + 1) % PREALLOC_SIZE; // wrap around to use the preallocated array circularly
        return ringBuffer.stream().sum();
        // return ringBuffer.get(getIndex);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(RingBufferOfIntAddAndGetBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new org.openjdk.jmh.runner.Runner(opt).run();
    }

    public static class RingBuffer0 {
        private final int[] elements;
        private int tail = 0;
        private final int maxSize;

        public RingBuffer0(int maxSize) {
            this.maxSize = maxSize;
            this.elements = new int[maxSize];
        }

        public void add(int element) {
            elements[tail] = element;
            tail = (tail < maxSize - 1) ? tail + 1 : 0;
            //tail = (tail + 1) % maxSize;
            //if (tail == head) {
            //    head = (head + 1) % maxSize; // Overwrite scenario
            //}
        }

        public int get(int index) {
            int idx = tail - index - 1;
            return elements[(idx < 0) ? idx + maxSize : idx];
        }
    }
}