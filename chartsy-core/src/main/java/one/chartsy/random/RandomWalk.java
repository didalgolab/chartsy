package one.chartsy.random;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public class RandomWalk {

    public static Candle candle(long time) {
        return candle(time, 0.0, RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Candle candle(long time, double referencePrice, RandomCandleSpecification spec, RandomGenerator rnd) {
        double open = referencePrice + rnd.nextGaussian(spec.drift(), spec.stddev()) * spec.gappiness();
        double curr = open, min = curr, max = curr;
        for (int i = 0; i < 4; i++) {
            curr += rnd.nextGaussian(spec.drift(), spec.stddev());
            min = Math.min(min, curr);
            max = Math.max(max, curr);
        }
        return Candle.of(time, open, max, min, curr);
    }

    public static Stream<Candle> candles() {
        return candles(LocalDate.ofEpochDay(0).atStartOfDay(), 0.0, Duration.ofDays(1), RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Stream<Candle> candles(Duration granularity, LocalDateTime startTime) {
        return candles(startTime, 0.0, granularity, RandomCandleSpecification.BASIC, ThreadLocalRandom.current());
    }

    public static Stream<Candle> candles(LocalDateTime startTime, double referencePrice, Duration granularity, RandomCandleSpecification spec, RandomGenerator rnd) {
        var timeStepMicros = Chronological.toMicros(granularity);
        var seedEndTime = Chronological.toEpochMicros(startTime) + timeStepMicros;
        var seed = candle(seedEndTime, referencePrice, spec.withGappiness(0.0), rnd);
        return Stream.iterate(seed, c -> candle(c.getTime() + timeStepMicros, c.close(), spec, rnd));
    }


    private RandomWalk() {}
}
