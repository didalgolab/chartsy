package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.base.Dataset;
import one.chartsy.data.Series;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

@ChartStudy(
        name = "Range Compression Score",
        label = "Range Compression Score",
        category = "Volatility",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(logarithmic = true)
@StudyParameter(id = "lineColor", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#FF0000", order = 100)
@StudyParameter(id = "lineStyle", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "SOLID", order = 110)
@LinePlotSpec(id = "score", label = "Range Compression Score", output = "value", colorParameter = "lineColor", strokeParameter = "lineStyle", order = 10)
public class RangeCompressionScore extends AbstractCandleIndicator {

    private static final int REQUIRED_CAPACITY = 415;

    private final RingBuffer<Candle> ringBuffer;
    private double lastValue;
    private boolean ready;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static RangeCompressionScore study() {
        return new RangeCompressionScore();
    }

    public RangeCompressionScore() {
        this.ringBuffer = new RingBuffer<>(REQUIRED_CAPACITY);
    }

    @Override
    public void accept(Candle bar) {
        ringBuffer.add(bar);

        if (!ringBuffer.isEmpty()) {
            lastValue = compute(ringBuffer, 0);
            ready = true;
        }
    }

    @Override
    @StudyOutput(id = "value", name = "Range Compression Score", order = 10)
    public double getLast() {
        return lastValue;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

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

    public record RangeCompressionResult(double totalScore, double lowerBand, double upperBand) { }
}
