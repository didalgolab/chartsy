package one.chartsy.ui.chart.indicators;

import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.RangeCompressionScore;
import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

/**
 * A presentation layer for the RangeCompressionScore indicator.
 */
@ServiceProvider(service = Indicator.class)
public class RangeCompressionScorePresentation extends AbstractIndicator {

    @Parameter(name = "Line Color")
    public Color lineColor = Color.RED;

    @Parameter(name = "Line Style")
    public Stroke lineStyle = BasicStrokes.DEFAULT;

    /**
     * Constructs the visual indicator with a default name.
     */
    public RangeCompressionScorePresentation() {
        super("Range Compression Score");
    }

    /**
     * Optionally, you can make the axis range logarithmic, etc.
     * For example, if you wanted it log-scaled, you might override
     * {@link #getRange(ChartContext)} or {@link #paint(Graphics2D, ChartContext, Rectangle)}.
     * Below shows the pattern if you choose to do so:
     */
    @Override
    public VisualRange getRange(ChartContext cf) {
        // Example of toggling to a logarithmic range
        return super.getRange(cf).asLogarithmic();
    }

    @Override
    public void calculate() {
        var quotes = getDataset();
        if (quotes != null) {
            // We calculate the RangeCompressionScore values for each bar in the dataset.
            // 'ValueIndicatorSupport.calculate' steps through each candle and calls 'RangeCompressionScore.accept()',
            // then collects the 'getLast()' values into a DoubleSeries.
            DoubleSeries rcsSeries = ValueIndicatorSupport.calculate(quotes, new RangeCompressionScore(), RangeCompressionScore::getLast);

            // Add a simple line plot of the values
            addPlot("Range Compression Score", new LinePlot(rcsSeries, lineColor, lineStyle));
        }
    }
}
