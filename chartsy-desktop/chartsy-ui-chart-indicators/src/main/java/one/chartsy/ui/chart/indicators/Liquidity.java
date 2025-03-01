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
 * Liquidity indicator computes the liquidity of a financial instrument based on
 * the number of unique price levels relative to the total number of price points
 * over a rolling window of past bars.
 *
 * <p>This indicator is price-based only and does not depend on trading volume.
 * Liquidity is calculated as the ratio of unique price levels (open, high, low, close)
 * to the total number of these price points within a given look-back period.</p>
 *
 * <p><b>Calculation details:</b></p>
 * <ul>
 *     <li>Each bar contributes between 3 and 4 price points (high, low, close are always included).</li>
 *     <li>The open price of a bar is only included if it differs from the previous bar's close.</li>
 *     <li>The indicator maintains a rolling count of price levels using an efficient {@code RingBuffer} structure.</li>
 * </ul>
 *
 * <p>This implementation is stateful and is <b>not thread-safe</b>.</p>
 *
 * <p><b>Usage example:</b></p>
 * <pre>{@code
 * LiquidityIndicator liquidity = new LiquidityIndicator(100);
 * candles.forEach(liquidity::accept);
 * double liquidityValue = liquidity.getLast();
 * }</pre>
 *
 * @author Mariusz Bernacki
 * @see one.chartsy.financial.AbstractCandleIndicator
 * @see one.chartsy.financial.ValueIndicator.OfDouble
 */
@ServiceProvider(service = Indicator.class)
public class Liquidity extends AbstractIndicator {

    @Parameter(name = "Periods")
    public int periods = 100;

    @Parameter(name = "Line Color")
    public Color color = new Color(0x4285F4);

    @Parameter(name = "Line Style")
    public Stroke style = BasicStrokes.THIN_SOLID;

    public Liquidity() {
        super("Liquidity");
    }

    @Override
    public String getLabel() {
        return "Liquidity (" + periods + ")";
    }

    @Override
    public VisualRange getRange(ChartContext cf) {
        return new VisualRange(Range.of(0, 1), false);
    }

    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles != null) {
            DoubleSeries liquidityValues = ValueIndicatorSupport.calculate(candles, new one.chartsy.financial.indicators.Liquidity(periods));
            addPlot("Liquidity", new LinePlot(liquidityValues, color, style));
        }
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] {0, 0.2, 0.4, 0.6, 0.8, 1};
    }
}
