/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import lombok.Getter;
import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractBandValueIndicator;

import java.util.function.Consumer;

/**
 * Sentiment Bands indicator that dynamically calculates adaptive upper and lower bands based on
 * multiple fractal-adaptive moving averages (FRAMAs). It leverages delayed versions and high-low
 * window statistics of FRAMAs to create bands sensitive to market sentiment changes.
 * <p>
 * This indicator is designed to capture shifts in market sentiment by adapting band boundaries
 * through the interplay of short- and long-term fractal-adaptive moving averages and volatility
 * measurements (ATR). It can be used to signal bullish or bearish market conditions.
 *
 * @author Mariusz Bernacki
 */
public class SentimentBands extends AbstractBandValueIndicator<SentimentBands.Values> implements Consumer<Candle> {

    private final FramaGroup[] framaGroups;
    private final Frama baseFrama;
    private final AverageTrueRange atr;
    private @Getter double lowerBand = Double.NaN;
    private @Getter double upperBand = Double.NaN;
    private @Getter boolean isReady;

    public SentimentBands() {
        framaGroups = new FramaGroup[5];
        for (int i = 0; i < 5; i++) {
            int averagingLength = 21 * 3 + 15 * i;
            framaGroups[i] = new FramaGroup(13, averagingLength);
        }

        baseFrama = new Frama(10, 90, 10.0);
        atr = new AverageTrueRange(15);
    }
    
    /**
     * Processes a new bar and updates internal FRAMAs, bands, and side states accordingly.
     *
     * @param bar the new market bar data
     */
    @Override
    public void accept(Candle bar) {
        double price = bar.close();
        
        // Update all FRAMA groups and their delayed values
        for (FramaGroup group : framaGroups) {
            group.update(price);
        }
        
        // Update base Frama and ATR
        baseFrama.accept(price);
        atr.accept(bar);

        // Check readiness
        isReady = checkReadiness();
        if (isReady) {
            computeBands();
            updateBandSide(price);
        }
    }

    private void computeBands() {
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;

        // Process delayed FRAMAs and their HHVs/LLVs
        for (FramaGroup holder : framaGroups) {
            if (!holder.highestHighsWindow.isEmpty()) {
                double hhv = holder.highestHighsWindow.getMax();
                minValue = Math.min(minValue, hhv);
                maxValue = Math.max(maxValue, hhv);
            }
            if (!holder.lowestLowsWindow.isEmpty()) {
                double llv = holder.lowestLowsWindow.getMin();
                minValue = Math.min(minValue, llv);
                maxValue = Math.max(maxValue, llv);
            }
        }

        // Include special Frama + ATR(15)
        double specialFramaValue = baseFrama.getLast();
        double atrValue = atr.getLast();
        double framaAtrUpper = specialFramaValue + atrValue;

        minValue = Math.min(minValue, specialFramaValue); // Lower band includes only FRAMA itself
        maxValue = Math.max(maxValue, framaAtrUpper);     // Upper band includes FRAMA + ATR only (no minus)

        this.lowerBand = minValue;
        this.upperBand = maxValue;
    }

    private boolean checkReadiness() {
        if (!baseFrama.isReady() || !atr.isReady())
            return false;

        for (FramaGroup holder : framaGroups)
            if (!holder.isReady())
                return false;

        return true;
    }

    /**
     * Retrieves the latest computed sentiment bands as a pair of upper and lower values.
     *
     * @return an instance of {@link Values} containing the upper and lower band values
     */
    @Override
    public Values getLast() {
        return new Values(upperBand, lowerBand);
    }

    /**
     * Container for the upper and lower sentiment band values.
     *
     * @param upperBand the computed upper sentiment band
     * @param lowerBand the computed lower sentiment band
     */
    public record Values(double upperBand, double lowerBand) implements BandValues { }

    /**
     * Holder class for managing individual FRAMAs, delays, and HHVs
     */
    private static class FramaGroup {
        static final int REF_WINDOW = 15;
        static final int HHV_WINDOW = 85;
        static final int LLV_WINDOW = 25;

        final Frama frama;
        final RingBuffer.OfDouble delayedFrama = new RingBuffer.OfDouble(REF_WINDOW);
        final DoubleWindowSummaryStatistics highestHighsWindow = new DoubleWindowSummaryStatistics(HHV_WINDOW);
        final DoubleWindowSummaryStatistics lowestLowsWindow = new DoubleWindowSummaryStatistics(LLV_WINDOW);

        FramaGroup(int framaLength, int averagingLength) {
            this.frama = new Frama(framaLength, averagingLength, 9.0);
        }

        void update(double price) {
            frama.accept(price);
            if (frama.isReady()) {
                delayedFrama.add(frama.getLast());
                if (delayedFrama.isFull()) {
                    var delayedValue = delayedFrama.get(delayedFrama.length() - 1);
                    highestHighsWindow.add(delayedValue);
                    lowestLowsWindow.add(delayedValue);
                }
            }
        }

        boolean isReady() {
            return delayedFrama.isFull();
        }
    }
}
