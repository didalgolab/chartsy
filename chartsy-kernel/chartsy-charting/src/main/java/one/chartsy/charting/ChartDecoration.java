package one.chartsy.charting;

import java.awt.ComponentOrientation;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/// Base class for chart-owned overlay layers that decorate the plot independently of dataset
/// renderers.
///
/// Decorations are attached through [Chart#addDecoration(ChartDecoration)] or
/// [Chart#setDecorations(java.util.List)] and then participate in the chart's drawable pipeline.
/// Their [#getDrawOrder()] controls where they sit relative to other drawables, while
/// [#beforeDraw(Graphics)] and [#afterDraw(Graphics)] let a decoration bracket the grouped paint
/// pass for every drawable sharing that same draw order.
///
/// The default bounds implementation covers the current plot rectangle, which is appropriate for
/// full-plot overlays such as backgrounds, highlights, or indicator adornments. Subclasses that
/// paint only a smaller region should override [#getBounds(Rectangle2D)] and typically refresh any
/// cached geometry from [#updateBoundsCache()] when visibility, attachment, orientation, or base
/// text direction changes.
///
/// Instances are mutable UI objects tied to a single owning [Chart] at a time and are not
/// thread-safe.
public abstract class ChartDecoration extends ChartOwnedDrawable implements Serializable {
    private Chart chart;
    private boolean visible;
    private int drawOrder;

    /// Creates a visible decoration with the default draw order of `-1`.
    ///
    /// The negative default places decorations before dataset renderers unless a subclass or caller
    /// explicitly assigns another order.
    protected ChartDecoration() {
        visible = true;
        drawOrder = -1;
    }


    /// Updates the owning chart reference managed by [Chart].
    ///
    /// This package-private hook centralizes the attachment lifecycle so subclasses can react from
    /// [#chartConnected(Chart, Chart)] without exposing a public mutator.
    ///
    /// @param chart the new owner, or `null` when the decoration is being detached
    void setChartInternal(Chart chart) {
        Chart previousChart = this.chart;
        if (chart != previousChart) {
            this.chart = chart;
            chartConnected(previousChart, chart);
        }
    }

    /// Runs after [Chart] finishes drawing the current draw-order group.
    ///
    /// The hook is called only when the decoration is visible and its bounds intersect the current
    /// clip. The default implementation does nothing.
    ///
    /// @param g the active chart graphics context
    public void afterDraw(Graphics g) {
    }

    /// Reacts to a change in the chart's effective base text direction.
    ///
    /// Decorations that cache shaped text or direction-sensitive geometry can override this hook to
    /// invalidate that state. The default implementation does nothing.
    protected void baseTextDirectionChanged() {
    }

    /// Runs before [Chart] draws the current draw-order group.
    ///
    /// Typical overrides install temporary clip, paint, or compositing state that the decoration's
    /// own [#draw(Graphics)] method and its sibling drawables depend on. The default implementation
    /// does nothing.
    ///
    /// @param g the active chart graphics context
    public void beforeDraw(Graphics g) {
    }

    /// Refreshes cached bounds when the decoration becomes attached to a visible chart.
    ///
    /// Subclasses overriding this hook should usually delegate to `super` so the default cache
    /// refresh still happens on attach.
    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        if (chart != null)
            if (isVisible())
                updateBoundsCache();
    }

    /// Reacts to a left-to-right or right-to-left component-orientation change on the owning chart.
    ///
    /// The chart invokes this after its own orientation state has changed. Decorations that depend
    /// on horizontal alignment, icon mirroring, or geometry derived from orientation can override
    /// this hook. The default implementation does nothing.
    ///
    /// @param oldOrientation the orientation active before the change
    /// @param newOrientation the orientation now installed on the chart
    protected void componentOrientationChanged(ComponentOrientation oldOrientation,
            ComponentOrientation newOrientation) {
    }

    /// Returns the plot rectangle while the decoration is attached to a chart.
    ///
    /// Full-plot overlays can inherit this behavior unchanged. Detached decorations report an empty
    /// rectangle so the chart does not attempt to paint or invalidate stale screen regions.
    @Override
    public Rectangle2D getBounds(Rectangle2D bounds) {
        Rectangle2D result = bounds;
        Chart chart = getChart();
        if (chart != null) {
            Rectangle plotRect = chart.getChartArea().getPlotRect();
            if (result != null)
                result.setRect(plotRect);
            else
                result = (Rectangle) plotRect.clone();
        } else if (result == null)
            result = new Rectangle2D.Double();
        else
            result.setRect(0.0, 0.0, 0.0, 0.0);
        return result;
    }

    /// Returns the chart that currently owns this decoration, if any.
    @Override
    public Chart getChart() {
        return chart;
    }

    /// Returns the draw-order group used by the owning chart's drawable pipeline.
    @Override
    public final int getDrawOrder() {
        return drawOrder;
    }

    /// Returns whether this decoration currently participates in painting.
    @Override
    public boolean isVisible() {
        return visible;
    }

    /// Requests repaint of this decoration's current bounds in the owning chart area.
    ///
    /// The request is ignored while detached or hidden.
    public void repaint() {
        if (chart != null)
            if (isVisible())
                chart.getChartArea().repaint2D(getBounds(null));
    }

    /// Changes the draw-order group used when this decoration is inserted into the chart pipeline.
    ///
    /// If the decoration is already attached, the owning chart re-sorts the drawable list and
    /// repaints the affected region.
    ///
    /// @param drawOrder the new ordering key
    public void setDrawOrder(int drawOrder) {
        if (this.drawOrder == drawOrder)
            return;
        int previousDrawOrder = this.drawOrder;
        this.drawOrder = drawOrder;
        if (getChart() != null) {
            getChart().handleDrawableDrawOrderChanged(this, previousDrawOrder, drawOrder);
            repaint();
        }
    }

    /// Shows or hides this decoration.
    ///
    /// Becoming visible refreshes the bounds cache before the repaint request so subclasses can
    /// invalidate the correct region.
    ///
    /// @param visible `true` to paint the decoration, `false` to suppress it
    public void setVisible(boolean visible) {
        if (visible == this.visible)
            return;
        this.visible = visible;
        if (chart != null) {
            if (visible)
                updateBoundsCache();
            chart.getChartArea().repaint2D(getBounds(null));
        }
    }

    /// Refreshes any cached bounds or geometry derived from the current chart state.
    ///
    /// The base implementation does nothing. Subclasses that override [#getBounds(Rectangle2D)]
    /// from cached data should recompute that cache here.
    protected void updateBoundsCache() {
    }
}
