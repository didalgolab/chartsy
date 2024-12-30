/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.ValueIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FramaTrendWhispers implements ValueIndicator, Consumer<Candle> {
    private final List<Path> paths = new ArrayList<>();
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    private static final class Path implements ValueIndicator.OfDouble, Consumer<Candle> {
        private final FramaZero frama;
        private final DoubleWindowSummaryStatistics queue;
        private double last = Double.NaN;

        private Path(FramaZero frama, DoubleWindowSummaryStatistics queue) {
            this.frama = frama;
            this.queue = queue;
        }

        @Override
        public void accept(Candle bar) {
            frama.accept(bar);
            if (frama.isReady()) {
                queue.accept(frama.getLast());
                if (queue.isFull()) {
                    last = queue.getAverage();
                }
            }
        }

        @Override
        public double getLast() {
            return last;
        }

        @Override
        public boolean isReady() {
            return !Double.isNaN(getLast());
        }
    }

    public FramaTrendWhispers() {
        this(new Options());
    }

    public FramaTrendWhispers(Options options) {
        for (int i = 1; i <= options.numberOfCurrents; i++) {
            paths.add(new Path(
                    new FramaZero(options.framaPeriod + 1 - i),
                    new DoubleWindowSummaryStatistics(i * options.framaSlowdownPeriod)
            ));
        }
    }

    @Override
    public void accept(Candle bar) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean ready = true;
        for (var path : paths) {
            path.accept(bar);

            if (ready && (ready = path.isReady())) {
                min = Math.min(min, path.getLast());
                max = Math.max(max, path.getLast());
            }
        }

        if (ready) {
            this.min = min;
            this.max = max;
        }
    }

    public ValueIndicator.OfDouble getPath(int index) {
        return paths.get(index);
    }

    public final double getMax() {
        return max;
    }

    public final double getMin() {
        return min;
    }

    public final double getRange() {
        return max - min;
    }

    @Override
    public boolean isReady() {
        return max > min;
    }

    public static class Options {
        public int numberOfCurrents;
        public int framaPeriod;
        public int framaSlowdownPeriod;

        public Options() {
            this(8, 45, 16);
        }

        public Options(int numberOfCurrents, int framaPeriod, int framaSlowdownPeriod) {
            this.numberOfCurrents = numberOfCurrents;
            this.framaPeriod = framaPeriod;
            this.framaSlowdownPeriod = framaSlowdownPeriod;
        }
    }
}
