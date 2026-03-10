/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
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
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.util.FastMath;

/**
 * Computes the rolling Pearson correlation between logarithmic price returns
 * and logarithmic volume ratios.
 */
@ChartStudy(
        name = "Return/Volume Correlation",
        label = "Ret/Vol Corr ({periods})",
        category = "Momentum",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = -1.0, max = 1.0, steps = {-1.0, -0.5, 0.0, 0.5, 1.0})
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#DB4437", order = 100)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@LinePlotSpec(id = "correlation", label = "Correlation", output = "value", colorParameter = "color", strokeParameter = "style", order = 10)
public class ReturnVolumeCorrelation extends AbstractCandleIndicator {

    public static final int DEFAULT_PERIODS = 30;

    private final int periods;
    private final RingBuffer.OfDouble returns;
    private final RingBuffer.OfDouble volumeRatios;
    private final PearsonsCorrelation pearsons = new PearsonsCorrelation();
    private Candle previous;
    private double last = Double.NaN;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static ReturnVolumeCorrelation study(
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "30", order = 10) int periods
    ) {
        return new ReturnVolumeCorrelation(periods);
    }

    public ReturnVolumeCorrelation() {
        this(DEFAULT_PERIODS);
    }

    public ReturnVolumeCorrelation(int periods) {
        if (periods <= 1)
            throw new IllegalArgumentException("Periods must be greater than 1");
        this.periods = periods;
        this.returns = new RingBuffer.OfDouble(periods);
        this.volumeRatios = new RingBuffer.OfDouble(periods);
    }

    @Override
    public void accept(Candle candle) {
        if (previous != null) {
            double prevClose = previous.close();
            double prevVolume = previous.volume();
            double close = candle.close();
            double volume = candle.volume();
            if (prevClose > 0 && close > 0 && prevVolume > 0 && volume > 0) {
                double r = FastMath.log(close / prevClose);
                double v = FastMath.log(volume / prevVolume);
                returns.add(r);
                volumeRatios.add(v);
                last = isReady() ? pearsons.correlation(returns.toPrimitiveArray(), volumeRatios.toPrimitiveArray()) : Double.NaN;
            }
        }
        previous = candle;
    }

    @Override
    @StudyOutput(id = "value", name = "Correlation", order = 10)
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return returns.length() == periods;
    }
}
