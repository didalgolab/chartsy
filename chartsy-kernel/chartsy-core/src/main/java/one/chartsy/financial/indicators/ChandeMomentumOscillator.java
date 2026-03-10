package one.chartsy.financial.indicators;

import one.chartsy.CandleField;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractDoubleIndicator;
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
        name = "Chande Momentum Oscillator",
        label = "CMO({priceField}, {periods})",
        category = "Momentum",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = -100.0, max = 100.0, steps = {-100.0, -50.0, 0.0, 50.0, 100.0})
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#2962FF", order = 100)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@LinePlotSpec(id = "cmo", label = "CMO", output = "value", colorParameter = "color", strokeParameter = "style", order = 10)
public class ChandeMomentumOscillator extends AbstractDoubleIndicator {

    private final int periods;
    private final RingBuffer.OfDouble changes;
    private double gainSum = 0.0;
    private double lossSum = 0.0;
    private double lastValue = Double.NaN;
    private double lastPrice = Double.NaN;

    @StudyFactory(input = StudyInputKind.PRICE_FIELD, inputParameter = "priceField")
    public static ChandeMomentumOscillator study(
            @StudyParameter(id = "priceField", name = "Price Field", scope = StudyParameterScope.INPUT, type = StudyParameterType.ENUM, enumType = CandleField.class, defaultValue = "CLOSE", order = 10) CandleField priceField,
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "14", order = 20) int periods
    ) {
        return new ChandeMomentumOscillator(periods);
    }

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
    @StudyOutput(id = "value", name = "CMO", order = 10)
    public double getLast() {
        return lastValue;
    }

    @Override
    public boolean isReady() {
        return changes.length() == periods;
    }
}
