package one.chartsy.charting.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Window;

/// Swing coordinate helpers used by chart components when dragging or placing overlays.
///
/// The current charting code uses [#convertPoint(Component, Point, Component)] to translate mouse
/// locations between nested chart widgets, floating legends, and layered panes.
///
/// The helper deliberately works below `SwingUtilities.convertPoint(...)` so it can also convert
/// to or from the owning window itself by passing `null` for one endpoint. That is the behavior
/// the charting module needs when it stores drag positions relative to a chart window rather than
/// to a specific child component.
public final class SwingUtil {
    
    /// Returns the top-level window that currently owns `component`.
    ///
    /// Non-container components start from their parent because they cannot own children and are
    /// never themselves windows.
    private static Window getOwningWindow(Component component) {
        Component current = (component instanceof Container) ? component : component.getParent();
        while (current != null) {
            if (current instanceof Window window)
                return window;
            current = current.getParent();
        }
        return null;
    }

    /// Returns the owning window for `component` or throws when the component is detached.
    private static Window requireOwningWindow(Component component, String role) {
        Window owningWindow = getOwningWindow(component);
        if (owningWindow == null)
            throw new IllegalArgumentException(role + " component not connected to component tree hierarchy");
        return owningWindow;
    }

    /// Accumulates the location of `component` relative to the supplied owning `window`.
    private static Point offsetToWindow(Component component, Window window) {
        int x = 0;
        int y = 0;
        for (Component current = component; current != window; current = current.getParent()) {
            x += current.getX();
            y += current.getY();
        }
        return new Point(x, y);
    }
    
    /// Converts `point` from `source` coordinates into `destination` coordinates.
    ///
    /// Both non-null components must already belong to hierarchies rooted at a [Window]. When
    /// `source` and `destination` belong to different windows, the conversion reconciles the two
    /// window coordinate systems through `Window.getLocationOnScreen()`.
    ///
    /// When one endpoint is `null`, the point is interpreted relative to the non-null endpoint's
    /// owning window:
    /// - `source == null`: `point` is already in window coordinates and is converted into
    ///   `destination` coordinates
    /// - `destination == null`: the returned point is expressed in the source window's coordinates
    ///
    /// @throws IllegalArgumentException if `source` or `destination` is non-null but detached from
    ///     a window hierarchy
    public static Point convertPoint(Component source, Point point, Component destination) {
        if (source == null && destination == null)
            return point;

        Window sourceWindow = source != null ? requireOwningWindow(source, "Source") : null;
        Window destinationWindow = destination != null ? requireOwningWindow(destination, "Destination") : null;
        if (source == null)
            sourceWindow = destinationWindow;
        if (destination == null)
            destinationWindow = sourceWindow;

        Component sourceReference = source != null ? source : sourceWindow;
        Component destinationReference = destination != null ? destination : destinationWindow;

        Point sourceOffset = offsetToWindow(sourceReference, sourceWindow);
        Point destinationOffset = offsetToWindow(destinationReference, destinationWindow);
        int x = point.x + sourceOffset.x - destinationOffset.x;
        int y = point.y + sourceOffset.y - destinationOffset.y;

        if (sourceWindow != destinationWindow) {
            Point sourceWindowOnScreen = sourceWindow.getLocationOnScreen();
            Point destinationWindowOnScreen = destinationWindow.getLocationOnScreen();
            x += sourceWindowOnScreen.x - destinationWindowOnScreen.x;
            y += sourceWindowOnScreen.y - destinationWindowOnScreen.y;
        }
        return new Point(x, y);
    }
}
