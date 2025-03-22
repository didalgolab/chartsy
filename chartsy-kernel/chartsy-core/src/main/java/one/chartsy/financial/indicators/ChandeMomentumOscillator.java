package one.chartsy.financial.indicators;

import one.chartsy.financial.AbstractDoubleIndicator;
import one.chartsy.data.structures.RingBuffer;

public class ChandeMomentumOscillator extends AbstractDoubleIndicator {

    private final int periods;
    private final RingBuffer.OfDouble changes;
    private double gainSum = 0.0;
    private double lossSum = 0.0;
    private double lastValue = Double.NaN;
    private double lastPrice = Double.NaN;

    public ChandeMomentumOscillator(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("`periods` must be positive");
        this.periods = periods;
        this.changes = new RingBuffer.OfDouble(periods);
    }

    @Override
    public void accept(double price) {
        if (!Double.isNaN(lastPrice)) {
            double change = price - lastPrice;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            gainSum += gain;
            lossSum += loss;

            if (changes.length() == periods) {
                double oldestChange = changes.get(periods - 1);
                gainSum -= Math.max(oldestChange, 0);
                lossSum -= Math.max(-oldestChange, 0);
            }

            changes.add(change);

            if (changes.length() == periods) {
                double denominator = gainSum + lossSum;
                lastValue = (denominator != 0.0)
                        ? 100.0 * (gainSum - lossSum) / denominator
                        : 0.0;
            }
        }
        lastPrice = price;
    }

    @Override
    public double getLast() {
        return lastValue;
    }

    @Override
    public boolean isReady() {
        return changes.length() == periods;
    }
}
