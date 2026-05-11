package one.chartsy.charting.graphic;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;

import javax.swing.SwingConstants;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartListener;

/// Base class for chart decorations whose bounds are anchored to the live plot rectangle.
///
/// The anchor uses the compass-style constants from [SwingConstants], including
/// [SwingConstants#CENTER], [SwingConstants#NORTH], [SwingConstants#NORTH_EAST],
/// [SwingConstants#EAST], [SwingConstants#SOUTH_EAST], [SwingConstants#SOUTH],
/// [SwingConstants#SOUTH_WEST], [SwingConstants#WEST], and [SwingConstants#NORTH_WEST].
/// [#computeLocation()] combines that anchor with the current decoration bounds and the configured
/// pixel offset, then stores the resulting top-left paint location for subclasses in
/// [#getLocation()].
///
/// Subclasses are free to report bounds whose origin is not `(0, 0)`. The anchor calculation
/// compensates for that origin before applying the offset so text or image bounds with negative
/// ascent-style coordinates still align predictably against the plot rectangle.
///
/// While attached to a chart, instances keep their cached location synchronized with
/// [ChartAreaEvent] updates from the owning chart area.
public abstract class PositionableDecoration extends ChartDecoration implements SwingConstants {

    /// Refreshes the cached anchored location after the chart installs a new plot rectangle.
    ///
    /// The chart area's own repaint already covers the visual update, so the listener only
    /// recomputes geometry and refreshes the enclosing decoration's bounds cache.
    private final class AreaListener implements ChartListener, Serializable {
        /// Recomputes the enclosing decoration geometry after a chart-area layout change.
        @Override
        public void chartAreaChanged(ChartAreaEvent event) {
            refreshGeometry();
        }
    }

    private int anchor;
    private final Point offset;
    private transient Point location;
    private final ChartListener areaListener;

    /// Creates a decoration anchored to one plot-rectangle position.
    ///
    /// @param anchor one of the compass-style [SwingConstants] positions supported by this class
    /// @throws IllegalArgumentException if `anchor` is not one of the supported constants
    protected PositionableDecoration(int anchor) {
        validateAnchor(anchor);
        this.anchor = anchor;
        offset = new Point();
        areaListener = new AreaListener();
        initializeTransientState();
    }

    /// Reattaches the chart-area listener and recomputes the cached anchored location.
    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        if (previousChart != null)
            previousChart.removeChartListener(areaListener);
        if (chart != null)
            chart.addChartListener(areaListener);

        computeLocation();
        super.chartConnected(previousChart, chart);
    }

    /// Recomputes the cached top-left paint location from the current anchor, offset, and bounds.
    ///
    /// Subclasses typically call this after changing any state that affects [#getBounds(Rectangle2D)].
    protected void computeLocation() {
        if (getChart() == null) {
            location.setLocation(0, 0);
            return;
        }

        location.setLocation(0, 0);
        Rectangle decorationBounds = getBounds(new Rectangle()).getBounds();
        Rectangle plotRect = getChart().getChartArea().getPlotRect();
        int width = decorationBounds.width;
        int height = decorationBounds.height;

        switch (anchor) {
        case NORTH:
            location.setLocation(plotRect.x + (plotRect.width - width) / 2, plotRect.y);
            break;
        case NORTH_EAST:
            location.setLocation(plotRect.x + plotRect.width - width, plotRect.y);
            break;
        case EAST:
            location.setLocation(plotRect.x + plotRect.width - width, plotRect.y + (plotRect.height - height) / 2);
            break;
        case SOUTH_EAST:
            location.setLocation(plotRect.x + plotRect.width - width, plotRect.y + plotRect.height - height);
            break;
        case SOUTH:
            location.setLocation(plotRect.x + (plotRect.width - width) / 2, plotRect.y + plotRect.height - height);
            break;
        case SOUTH_WEST:
            location.setLocation(plotRect.x, plotRect.y + plotRect.height - height);
            break;
        case WEST:
            location.setLocation(plotRect.x, plotRect.y + (plotRect.height - height) / 2);
            break;
        case NORTH_WEST:
            location.setLocation(plotRect.x, plotRect.y);
            break;
        case CENTER:
        default:
            location.setLocation(plotRect.x + (plotRect.width - width) / 2, plotRect.y + (plotRect.height - height) / 2);
            break;
        }

        location.translate(offset.x - decorationBounds.x, offset.y - decorationBounds.y);
    }

    /// Returns the active plot-rectangle anchor.
    ///
    /// @return one of the supported compass-style [SwingConstants] anchor constants
    public int getAnchor() {
        return anchor;
    }

    /// Returns the device-space bounds currently occupied by this decoration.
    ///
    /// Subclasses usually derive those bounds from [#getLocation()] and any decoration-specific
    /// geometry such as text layout or image size.
    @Override
    public abstract Rectangle2D getBounds(Rectangle2D bounds);

    /// Returns the cached top-left paint location computed from the current anchor and offset.
    ///
    /// The returned [Point] is the live internal cache. Subclasses should treat it as read-only.
    ///
    /// @return live cached top-left paint location
    protected final Point getLocation() {
        return location;
    }

    /// Returns the retained pixel offset relative to the anchored bounds origin.
    ///
    /// @return a defensive copy of the current offset
    public Point getOffset() {
        return (Point) offset.clone();
    }

    /// Changes the active plot-rectangle anchor.
    ///
    /// If this decoration is currently attached and visible, the owning chart repaints the union of
    /// the old and new bounds so stale pixels are cleared.
    ///
    /// @param anchor one of the compass-style [SwingConstants] positions supported by this class
    /// @throws IllegalArgumentException if `anchor` is not one of the supported constants
    public void setAnchor(int anchor) {
        if (this.anchor == anchor)
            return;

        validateAnchor(anchor);
        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        this.anchor = anchor;
        computeLocation();
        repaintGeometryChange(previousBounds);
    }

    /// Changes the retained pixel offset relative to the anchored bounds origin.
    ///
    /// The supplied point is copied. Later mutations of `offset` are not observed.
    ///
    /// @param offset new pixel offset relative to the anchored bounds origin
    public void setOffset(Point offset) {
        if (this.offset.equals(offset))
            return;

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        this.offset.setLocation(offset);
        computeLocation();
        repaintGeometryChange(previousBounds);
    }

    private Rectangle2D captureBoundsIfAttachedAndVisible() {
        return isAttachedAndVisible() ? getBounds(null) : null;
    }

    private void initializeTransientState() {
        location = new Point();
    }

    private boolean isAttachedAndVisible() {
        return getChart() != null && isVisible();
    }

    private void refreshGeometry() {
        computeLocation();
        if (isAttachedAndVisible())
            updateBoundsCache();
    }

    private void repaintGeometryChange(Rectangle2D previousBounds) {
        if (!isAttachedAndVisible())
            return;

        updateBoundsCache();
        Rectangle2D repaintBounds = getBounds(null);
        if (previousBounds != null)
            repaintBounds.add(previousBounds);
        getChart().getChartArea().repaint2D(repaintBounds);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        in.registerValidation(() -> {
            initializeTransientState();
            computeLocation();
            if (isAttachedAndVisible())
                updateBoundsCache();
        }, 0);
    }

    private static void validateAnchor(int anchor) {
        switch (anchor) {
        case CENTER:
        case NORTH:
        case NORTH_EAST:
        case EAST:
        case SOUTH_EAST:
        case SOUTH:
        case SOUTH_WEST:
        case WEST:
        case NORTH_WEST:
            return;
        default:
            throw new IllegalArgumentException("Unknown anchor: " + anchor);
        }
    }
}
