package one.chartsy.charting;

import java.util.HashMap;
import java.util.Map;

import one.chartsy.charting.internal.PolarProjector;
import one.chartsy.charting.renderers.PieChartRenderer;

/// Adapts a chart to pie-chart rendering semantics.
///
/// While attached, this configuration removes the chart area's shared plot style, replaces every
/// non-[PieChartRenderer] with a temporary pie renderer, and configures a polar projector whose
/// radius is always non-symmetric. Those replacements are tracked so switching the chart back to a
/// different type restores the original renderer chain instead of leaving pie-specific renderers in
/// place.
///
/// Pie charts do not install default scales or grids. Slice layout is driven entirely by the
/// projector's angle/radius transform and by the active pie renderers.
class PieConfig extends ChartConfig {
    private static final int TYPE = 3;

    private transient boolean restrictToPieRenderers;
    private transient Map<ChartRenderer, ChartRenderer> originalRenderersByReplacement;
    private transient PlotStyle savedPlotStyle;

    /// Creates a configuration that accepts only pie renderers while attached.
    PieConfig() {
        restrictToPieRenderers = true;
    }

    /// Returns the lazily created mapping from temporary pie renderers back to the renderers they
    /// replaced.
    private Map<ChartRenderer, ChartRenderer> getOriginalRenderersByReplacement() {
        if (originalRenderersByReplacement == null)
            originalRenderersByReplacement = new HashMap<>();
        return originalRenderersByReplacement;
    }

    /// Installs pie-specific renderer swaps before attachment and restores the original chart state
    /// before detachment.
    ///
    /// When attaching, the chart area's plot style is cleared so slice fill and stroke come from
    /// the pie renderers themselves. When detaching, the saved plot style and any replaced
    /// renderers are restored before the base implementation clears this configuration's chart
    /// reference.
    ///
    /// ### API Note
    ///
    /// Renderer compatibility is relaxed only while restoring the saved renderer chain. That allows
    /// non-pie renderers to be reattached without tripping [#supportsRenderer(ChartRenderer)].
    @Override
    void applyToChart(Chart chart, boolean installDefaultScaleAndGrid) {
        if (chart == null)
            restoreChartState();
        else
            prepareChartForPieRendering(chart);

        super.applyToChart(chart, installDefaultScaleAndGrid);
    }

    /// Prepares `chart` for pie rendering by disabling shared plot painting and swapping in pie
    /// renderers where needed.
    private void prepareChartForPieRendering(Chart chart) {
        savedPlotStyle = chart.getChartArea().getPlotStyle();
        chart.getChartArea().setPlotStyle(null);
        replaceNonPieRenderers(chart);
        chart.getXAxis().setAutoVisibleRange(true);
        chart.getYAxis(0).setAutoVisibleRange(true);
    }

    /// Replaces each non-pie renderer with a temporary [PieChartRenderer] and remembers the
    /// original renderer for later restoration.
    private void replaceNonPieRenderers(Chart chart) {
        Map<ChartRenderer, ChartRenderer> originalRenderers = getOriginalRenderersByReplacement();
        originalRenderers.clear();

        for (int rendererIndex = 0; rendererIndex < chart.getRendererCount(); rendererIndex++) {
            ChartRenderer renderer = chart.getRenderer(rendererIndex);
            if (renderer instanceof PieChartRenderer)
                continue;

            PieChartRenderer replacement = new PieChartRenderer();
            originalRenderers.put(replacement, renderer);
            chart.setRenderer(rendererIndex, replacement);
        }
    }

    /// Restores the plot style and any non-pie renderers that were hidden while this configuration
    /// was attached.
    private void restoreChartState() {
        Chart chart = super.chart;
        if (chart == null) {
            restrictToPieRenderers = true;
            return;
        }

        restrictToPieRenderers = false;
        chart.getChartArea().setPlotStyle(savedPlotStyle);
        restoreOriginalRenderers(chart);
        savedPlotStyle = null;
        restrictToPieRenderers = true;
    }

    /// Reinstalls every original renderer that was replaced during attachment.
    private void restoreOriginalRenderers(Chart chart) {
        Map<ChartRenderer, ChartRenderer> originalRenderers = getOriginalRenderersByReplacement();
        for (int rendererIndex = 0; rendererIndex < chart.getRendererCount(); rendererIndex++) {
            ChartRenderer replacement = chart.getRenderer(rendererIndex);
            ChartRenderer originalRenderer = originalRenderers.get(replacement);
            if (originalRenderer != null)
                chart.setRenderer(rendererIndex, originalRenderer);
        }
        originalRenderers.clear();
    }

    /// Returns whether `renderer` may be connected while pie rendering is active.
    ///
    /// Only [PieChartRenderer] instances are accepted during normal use. The restriction is
    /// temporarily disabled while detaching so the original renderer chain can be restored.
    @Override
    boolean supportsRenderer(ChartRenderer renderer) {
        if (restrictToPieRenderers)
            return renderer instanceof PieChartRenderer;
        return true;
    }

    /// Suppresses default grid installation for pie charts.
    @Override
    protected Grid createGrid(int axisIndex) {
        return null;
    }

    /// Creates the polar projector used to interpret x values as angle and y values as radial span.
    ///
    /// The projector reuses the chart's stored starting angle and angular range, disables symmetric
    /// radial mapping, and keeps the radius visible so pie renderers can use the full radial
    /// interval.
    @Override
    protected ChartProjector createProjector(Chart chart) {
        PolarProjector projector = new PolarProjector();
        projector.setStartingAngle(chart.getStoredStartingAngleOrZero());
        projector.setRange(chart.getStoredAngleRange());
        projector.setSymmetric(false);
        projector.setShowingRadius(true);
        return projector;
    }

    /// Suppresses default scale installation for pie charts.
    @Override
    protected Scale createScale(int axisIndex) {
        return null;
    }

    /// Returns the chart type id used by [ChartConfig#forType(int)] for pie charts.
    @Override
    public int getType() {
        return TYPE;
    }
}
