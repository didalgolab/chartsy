package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;

/// Reports one structural or presentation change in a [ChartRenderer] attached to a [Chart].
///
/// [Chart] publishes this event whenever a renderer branch is attached, detached, or asks the
/// chart to react to one of the renderer-change categories defined here. Listeners typically use
/// [#getType()] to decide whether they must refresh legend content, update renderer-specific UI
/// state, or ignore changes that only affect geometry.
///
/// ### API Note
///
/// Charts can bracket bursts of these events with
/// [ChartRendererListener2#startRendererChanges()] and
/// [ChartRendererListener2#endRendererChanges()] so listeners can coalesce expensive work.
public class ChartRendererEvent extends EventObject {
    /// Change type published when a renderer becomes attached to a chart.
    public static final int RENDERER_ADDED = 1;

    /// Change type published when a renderer is detached from a chart.
    public static final int RENDERER_REMOVED = 2;

    /// Change type published when a renderer enters or leaves the chart's viewable set.
    public static final int VISIBILITY_CHANGED = 3;

    /// Change type published when renderer styling changes.
    public static final int STYLE_CHANGED = 4;

    /// Change type published when renderer output can affect ranges or coordinate transforms.
    public static final int DATARANGE_CHANGED = 5;

    /// Change type published when legend-visible metadata changes.
    public static final int LEGENDITEM_CHANGED = 6;

    /// Change type published when the renderer changes data sources or source structure.
    public static final int DATASOURCE_CHANGED = 7;

    /// Change type published when renderer annotations change.
    public static final int ANNOTATION_CHANGED = 8;

    private final int type;
    private final ChartRenderer renderer;

    /// Creates a renderer-change event for `renderer`.
    ///
    /// @param renderer the renderer whose state changed
    /// @param type     one of the public change-type constants defined on this class
    public ChartRendererEvent(Chart chart, ChartRenderer renderer, int type) {
        super(chart);
        this.renderer = renderer;
        this.type = type;
    }

    /// Returns the chart that published this event.
    public final Chart getChart() {
        return (Chart) super.getSource();
    }

    /// Returns the renderer whose state change triggered this event.
    public final ChartRenderer getRenderer() {
        return renderer;
    }

    /// Returns the renderer-change category represented by this event.
    ///
    /// The value is one of the public `..._CHANGED`, `RENDERER_ADDED`, or `RENDERER_REMOVED`
    /// constants declared on this class.
    public final int getType() {
        return type;
    }
}
