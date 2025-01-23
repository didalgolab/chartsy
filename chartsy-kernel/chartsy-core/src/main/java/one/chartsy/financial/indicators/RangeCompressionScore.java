package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.base.Dataset;
import one.chartsy.data.Series;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;

/**
 * An indicator version of RangeCompressionScore that:
 *   - Extends AbstractCandleIndicator
 *   - Uses the existing static compute(...) / computeWithBands(...) methods
 *   - Maintains an internal RingBuffer for lookback
 */
public class RangeCompressionScore extends AbstractCandleIndicator {

    /** 
     * We need up to 415 bars in order to handle the largest rangeLen (80) 
     * plus up to 256 lookback blocks of length 80 each.
     */
    private static final int REQUIRED_CAPACITY = 415;
    
    private final RingBuffer<Candle> ringBuffer;
    private double lastValue;
    private boolean ready;

    /**
     * Creates a RangeCompressionScore indicator with a default ring buffer capacity
     * sufficient for the maximum needed lookback (415 bars).
     */
    public RangeCompressionScore() {
        this.ringBuffer = new RingBuffer<>(REQUIRED_CAPACITY);
    }

    /**
     * Processes a new Candle, appending it to the ring buffer and then 
     * updating the compression score (if there is enough data).
     *
     * @param bar the new Candle to process
     */
    @Override
    public void accept(Candle bar) {
        ringBuffer.add(bar);

        if (!ringBuffer.isEmpty()) {
            // Compute the score for the most recent candle (offset=0).
            lastValue = compute(ringBuffer, 0);
            ready = true;
        }
    }

    /**
     * Returns the last computed RangeCompressionScore value.
     */
    @Override
    public double getLast() {
        return lastValue;
    }

    /**
     * Indicates if the indicator has enough data to produce the "full" score
     * (i.e. at least 415 bars).
     */
    @Override
    public boolean isReady() {
        return ready;
    }

    /**
     * The existing (unchanged) static method that calculates 
     * the Range Compression Score (without bands) for any series at a given offset.
     */
    public static double compute(Dataset<Candle> series, int offset) {
        final int MAX_LOOKBACK = 256;
        final int MAX_RANGE_LENGTH = 80;

        if (offset >= series.length() || offset < 0) {
            return 0.0;
        }

        double totalScore = 0.0;

        for (int rangeLen = 1; rangeLen <= MAX_RANGE_LENGTH; rangeLen++) {
            if (offset + rangeLen - 1 >= series.length())
                break;

            double minLow  = Double.POSITIVE_INFINITY;
            double maxHigh = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < rangeLen; i++) {
                Candle c = series.get(offset + i);
                minLow  = Math.min(minLow,  c.low());
                maxHigh = Math.max(maxHigh, c.high());
            }
            double currentRange = maxHigh - minLow;

            int startIndex = offset + rangeLen;
            int endIndex   = Math.min(offset + rangeLen + MAX_LOOKBACK - 1,
                                      series.length() - rangeLen);

            if (startIndex > endIndex) {
                continue;
            }

            int blockCount = 0;

            for (int histStart = startIndex; histStart <= endIndex; histStart++) {
                double histMinLow  = Double.POSITIVE_INFINITY;
                double histMaxHigh = Double.NEGATIVE_INFINITY;

                for (int j = 0; j < rangeLen; j++) {
                    Candle c = series.get(histStart + j);
                    histMinLow  = Math.min(histMinLow,  c.low());
                    histMaxHigh = Math.max(histMaxHigh, c.high());
                }

                double histRange = histMaxHigh - histMinLow;
                if (histRange <= currentRange) {
                    break;
                }
                blockCount++;
            }

            if (blockCount > 0) {
                totalScore += Math.sqrt(blockCount);
            }
        }

        return totalScore;
    }

    /**
     * The extended static method that returns the total score plus
     * approximate universal breakout bands, unchanged.
     */
    public static RangeCompressionResult computeWithBands(Series<Candle> series, int offset) {
        final int MAX_LOOKBACK = 256;
        final int MAX_RANGE_LENGTH = 80;

        if (offset >= series.length() || offset < 0)
            return new RangeCompressionResult(0, 0, 0);

        double totalScore = 0.0;
        double weightedLowSum  = 0.0;
        double weightedHighSum = 0.0;
        double totalWeight     = 0.0;

        for (int rangeLen = 1; rangeLen <= MAX_RANGE_LENGTH; rangeLen++) {
            if (offset + rangeLen - 1 >= series.length())
                break;

            double minLow  = Double.POSITIVE_INFINITY;
            double maxHigh = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < rangeLen; i++) {
                Candle c = series.get(offset + i);
                minLow  = Math.min(minLow,  c.low());
                maxHigh = Math.max(maxHigh, c.high());
            }
            double currentRange = maxHigh - minLow;

            int startIndex = offset + rangeLen;
            int endIndex   = Math.min(offset + rangeLen + MAX_LOOKBACK - 1,
                                      series.length() - rangeLen);

            if (startIndex > endIndex)
                continue;

            int blockCount = 0;

            for (int histStart = startIndex; histStart <= endIndex; histStart++) {
                double histMinLow  = Double.POSITIVE_INFINITY;
                double histMaxHigh = Double.NEGATIVE_INFINITY;

                for (int j = 0; j < rangeLen; j++) {
                    Candle c = series.get(histStart + j);
                    histMinLow  = Math.min(histMinLow,  c.low());
                    histMaxHigh = Math.max(histMaxHigh, c.high());
                }

                double histRange = histMaxHigh - histMinLow;
                if (histRange <= currentRange) {
                    break;
                }
                blockCount++;
            }

            if (blockCount > 0) {
                double partialScore = Math.sqrt(blockCount);
                double weight = blockCount * blockCount;
                totalScore += partialScore;
                totalWeight += weight;

                weightedLowSum  += weight * minLow;
                weightedHighSum += weight * maxHigh;
            }
        }

        if (totalScore == 0.0) {
            return new RangeCompressionResult(0, 0, 0);
        }

        double lowerBand = weightedLowSum  / totalWeight;
        double upperBand = weightedHighSum / totalWeight;

        return new RangeCompressionResult(totalScore, lowerBand, upperBand);
    }

    /**
     * A small record container for returning both the compression score and breakout range.
     */
    public record RangeCompressionResult(double totalScore, double lowerBand, double upperBand) { }
}
