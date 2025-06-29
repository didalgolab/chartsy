package one.chartsy.ui.chart.indicators;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;

/**
 * Range-Inversion Sigma indicator
 *
 * <p>Shows, for each bar, the maximum absolute sigma multiplier
 * (z-score) obtained when scanning all look-back windows
 * from 7 up to {@code periods} bars of candle ranges
 * (high - low).</p>
 *
 * <p>The underlying computation is performed by
 * {@link one.chartsy.financial.indicators.RangeInversionSigma}.</p>
 *
 * <p>This class is <b>stateful</b> and <b>not thread-safe</b>.</p>
 *
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = Indicator.class)
public class RangeInversionSigmaUI extends AbstractIndicator {

    /* ======== Parameters exposed in the Chartsy property pane ======== */
    @Parameter(name = "Periods")
    public int periods = 100;                  // maximal window length

    @Parameter(name = "Line Color")
    public Color color = new Color(0xD32F2F);  // red tone

    @Parameter(name = "Line Style")
    public Stroke style = BasicStrokes.THIN_SOLID;

    public RangeInversionSigmaUI() {
        super("Range Inversion Sigma");
    }

    /* --------------------------------------------------------------- */
    @Override
    public String getLabel() {
        return "RangeInvSigma (" + periods + ")";
    }

    /**
     * Runs the numerical indicator over the whole dataset and adds
     * a simple line plot.
     */
    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles == null)
            return;

        DoubleSeries sigmaValues = ValueIndicatorSupport.calculate(
                candles,
                new one.chartsy.financial.indicators.RangeInversionSigma(periods));

        addPlot("Sigma", new LinePlot(sigmaValues, color, style));
    }

    /**
     * Symmetric grid lines every two sigmas.
     */
    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] { -10, -8, -6, -4, -2, 0, 2, 4, 6, 8, 10 };
    }
}
