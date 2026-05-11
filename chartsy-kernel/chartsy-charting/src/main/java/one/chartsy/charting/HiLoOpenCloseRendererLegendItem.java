package one.chartsy.charting;

import java.util.StringJoiner;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.HiLoChartRenderer;
import one.chartsy.charting.renderers.SingleHiLoRenderer;

/// [ChartRendererLegendItem] specialization for one logical OHLC series rendered by
/// [HiLoChartRenderer].
///
/// In [HiLoChartRenderer.Mode#CANDLE] and [HiLoChartRenderer.Mode#OPENCLOSE], one logical series is
/// painted by two [SingleHiLoRenderer] children: one for the high/low stroke and one for the
/// open/close or candle-body glyphs. This legend entry keeps both children together so the owning
/// renderer can paint one composite marker and derive one label for the pair.
///
/// An explicit per-series name configured on the renderer takes precedence over dataset names.
/// When no such name is available, the label falls back to the non-empty names exposed by the two
/// child renderers' datasets.
public class HiLoOpenCloseRendererLegendItem extends ChartRendererLegendItem {
    private final SingleHiLoRenderer hiLoChildRenderer;
    private final SingleHiLoRenderer openCloseChildRenderer;

    /// Creates a legend row for one logical OHLC series.
    ///
    /// `hiLoChildRenderer` and `openCloseChildRenderer` are expected to belong to the same logical
    /// series inside `renderer`.
    ///
    /// @param renderer the composite renderer that owns the logical series
    /// @param hiLoChildRenderer the child renderer that paints the high/low stroke
    /// @param openCloseChildRenderer the child renderer that paints the open/close or candle body
    public HiLoOpenCloseRendererLegendItem(
            HiLoChartRenderer renderer,
            SingleHiLoRenderer hiLoChildRenderer,
            SingleHiLoRenderer openCloseChildRenderer) {
        super(renderer);
        this.hiLoChildRenderer = hiLoChildRenderer;
        this.openCloseChildRenderer = openCloseChildRenderer;
    }

    /// Returns the child renderer responsible for the high/low stroke portion of the marker.
    ///
    /// @return the renderer that contributes the wick or stick segment
    public final SingleHiLoRenderer getHiLoChildRenderer() {
        return hiLoChildRenderer;
    }

    /// Returns the legend label for this logical OHLC series.
    ///
    /// An explicit per-series name from the owning [HiLoChartRenderer] wins. If the renderer does
    /// not expose one, this method joins the non-empty [DataSet#getName()] values from the hi/lo
    /// child first and the open/close child second, using `/` as the separator.
    ///
    /// @return the configured series name, or a synthesized dataset-name label that may be empty
    public String getLegendText() {
        var renderer = (HiLoChartRenderer) getRenderer();
        var seriesName = getConfiguredSeriesName(renderer);
        if (seriesName != null)
            return seriesName;

        var legendText = new StringJoiner("/");
        appendDataSetNames(legendText, hiLoChildRenderer);
        appendDataSetNames(legendText, openCloseChildRenderer);
        return legendText.toString();
    }

    /// Returns the child renderer responsible for the open/close portion of the marker.
    ///
    /// @return the renderer that contributes the candle body or open/close ticks
    public final SingleHiLoRenderer getOpenCloseChildRenderer() {
        return openCloseChildRenderer;
    }

    private String getConfiguredSeriesName(HiLoChartRenderer renderer) {
        int logicalSeriesCount = renderer.getChildCount() / 2;
        for (int logicalSeriesIndex = 0; logicalSeriesIndex < logicalSeriesCount; logicalSeriesIndex++) {
            if (matchesLogicalSeries(renderer, logicalSeriesIndex))
                return renderer.getName(logicalSeriesIndex);
        }
        return null;
    }

    private static void appendDataSetNames(StringJoiner legendText, SingleHiLoRenderer childRenderer) {
        var dataSource = childRenderer.getDataSource();
        for (int dataSetIndex = 0; dataSetIndex < dataSource.size(); dataSetIndex++) {
            DataSet dataSet = dataSource.get(dataSetIndex);
            String dataSetName = dataSet.getName();
            if (dataSetName != null && !dataSetName.isEmpty())
                legendText.add(dataSetName);
        }
    }

    private boolean matchesLogicalSeries(HiLoChartRenderer renderer, int logicalSeriesIndex) {
        return renderer.getChild(logicalSeriesIndex * 2) == hiLoChildRenderer
                || renderer.getChild(logicalSeriesIndex * 2 + 1) == openCloseChildRenderer;
    }
}
