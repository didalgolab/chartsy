package one.chartsy.charting.graphic;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.Serializable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/// Adapts a [JComponent] to the [Icon] contract for off-hierarchy painting.
///
/// The icon delegates its dimensions and rendered appearance to the wrapped component's current
/// preferred size and visual state. It is useful when an existing Swing component already knows
/// how to render a small preview, but the surrounding API accepts only an [Icon].
///
/// [#paintIcon(Component, Graphics, int, int)] does not install the wrapped component in the
/// caller's component hierarchy. Instead, it resizes the component to its preferred size and paints
/// it through [SwingUtilities#paintComponent(Graphics, Component, Container, int, int, int, int)]
/// using a shared temporary parent container. Changes made to the wrapped component after
/// construction are therefore reflected in later icon measurements and paint calls.
///
/// **Thread-safety:** this wrapper follows the usual Swing single-threaded access model of the
/// wrapped component.
public class JComponentIcon implements Icon, Serializable {

    private static final Container PAINT_HOST = new Container();

    private final JComponent component;
    
    /// Creates an icon view of the supplied component.
    ///
    /// The component reference is retained directly. Mutating the component or changing its
    /// preferred size later affects subsequent [#getIconWidth()], [#getIconHeight()], and
    /// [#paintIcon(Component, Graphics, int, int)] calls.
    ///
    /// @param component component exposed through the [Icon] interface
    public JComponentIcon(JComponent component) {
        this.component = component;
    }
    
    /// Returns the wrapped component's current preferred height.
    ///
    /// The value is queried on each call so icon layout tracks later preferred-size changes.
    @Override
    public int getIconHeight() {
        return component.getPreferredSize().height;
    }
    
    /// Returns the wrapped component's current preferred width.
    ///
    /// The value is queried on each call so icon layout tracks later preferred-size changes.
    @Override
    public int getIconWidth() {
        return component.getPreferredSize().width;
    }
    
    /// Paints the wrapped component at the requested icon origin.
    ///
    /// The caller-supplied host component from the [Icon] contract is ignored. This wrapper sizes
    /// the wrapped component to its current preferred size before delegating to
    /// [SwingUtilities#paintComponent(Graphics, Component, Container, int, int, int, int)].
    ///
    /// @param ignored ignored caller-owned host component
    /// @param g graphics context that receives the component rendering
    @Override
    public void paintIcon(Component ignored, Graphics g, int x, int y) {
        Dimension preferredSize = component.getPreferredSize();
        component.setSize(preferredSize);
        SwingUtilities.paintComponent(g, component, PAINT_HOST, x, y, preferredSize.width, preferredSize.height);
    }
}
