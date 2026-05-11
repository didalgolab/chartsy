package one.chartsy.charting;

import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.Map;

/// Defines a layout manager that can predict component bounds for off-screen chart rendering.
///
/// [Chart] and [Legend] use this extension when they need layout results during image generation
/// or other server-side paint passes, where mutating live component bounds would either be too
/// late or would interfere with the interactive Swing tree. Implementations are expected to mirror
/// [#layoutContainer(Container)] closely enough that callers can paint children from the returned
/// rectangles instead of from their current on-screen bounds.
///
/// Callers in this module query the returned map by component and do not rely on iteration order
/// or on an exact key set beyond the components they ask for. Implementations may publish the
/// requested bounds under [#BOUNDS_PROPERTY] when nested server-side sublayouts need access to the
/// same available rectangle.
public interface ServerSideLayout extends LayoutManager {

    /// Client-property key used to expose the active server-side bounds rectangle to nested
    /// layouts.
    ///
    /// [ChartLayout] stores the rectangle passed to
    /// [#computeBounds(Container, Rectangle)] under this key before delegating to nested layout
    /// logic, and [LegendLayout] reads the same property when its server-side variants need the
    /// parent chart or legend bounds to decide how many rows or columns fit.
    String BOUNDS_PROPERTY = "ServerSideLayout.Bounds";

    /// Computes the component rectangles that a server-side paint pass should use for the current
    /// container state.
    ///
    /// Unlike [#layoutContainer(Container)], this method should not reposition child components
    /// directly. Callers use the returned rectangles while painting charts and legends into image
    /// buffers or other off-screen graphics targets.
    ///
    /// @param container the container whose children and insets define the layout input
    /// @param bounds the available drawing rectangle, in `container` coordinates
    /// @return component bounds keyed by the components the caller may need to render or inspect
    Map<Component, Rectangle> computeBounds(Container container, Rectangle bounds);
}
