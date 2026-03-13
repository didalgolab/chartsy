package one.chartsy.charting.interactors;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.util.swing.SwingUtil;
import one.chartsy.charting.util.text.BidiUtil;

/// Shows a tooltip-like popup for the point currently highlighted by [ChartHighlightInteractor].
///
/// The popup is rendered with a normal [JToolTip] so it follows the active look and feel, but it
/// is hosted directly in the chart root pane's popup layer instead of going through Swing's global
/// tooltip manager. That keeps the overlay synchronized with chart picking rather than with
/// component hover timing.
///
/// Returning `null` from [#buildInfoText(DisplayPoint)] suppresses the popup and the highlight
/// event for that picked point. Subclasses can use that hook to ignore picks that should not
/// surface as info views.
public class ChartInfoViewInteractor extends ChartHighlightInteractor {

    /// Popup host for the tooltip content managed by [ChartInfoViewInteractor].
    ///
    /// The wrapper lets the interactor position a standard [JToolTip] in the root pane's popup
    /// layer, switch borders for HTML content, and repaint only the region vacated when the popup
    /// disappears.
    static class InfoViewWindow extends JPanel {
        private final JToolTip toolTip;
        private final Border defaultBorder;
        private final Border htmlBorder;

        /// Creates an empty popup window for `interactor`.
        ///
        /// @param interactor the interactor that owns the tooltip component
        public InfoViewWindow(ChartInfoViewInteractor interactor) {
            this(interactor, null);
        }

        /// Creates a popup window for `interactor` with optional initial text.
        ///
        /// @param interactor the interactor that owns the tooltip component
        /// @param text   initial tooltip text, or `null` to create an empty window
        public InfoViewWindow(ChartInfoViewInteractor interactor, String text) {
            setLayout(new BorderLayout());

            toolTip = interactor.createToolTip();
            toolTip.setComponent(interactor.getChart());
            defaultBorder = toolTip.getBorder();
            htmlBorder = new CompoundBorder(defaultBorder, new EmptyBorder(0, 3, 0, 3));
            updateText(text);

            setDoubleBuffered(true);
            setOpaque(true);
            add(toolTip, BorderLayout.CENTER);
            setSize(toolTip.getPreferredSize());
        }

        private void updateText(String text) {
            toolTip.setTipText(text);
            toolTip.setBorder(text != null && text.startsWith("<html>") ? htmlBorder : defaultBorder);
        }

        /// Removes this popup from its parent and repaints the exposed area.
        public void hideTip() {
            Container parent = getParent();
            if (parent == null) {
                return;
            }

            Rectangle bounds = getBounds();
            parent.remove(this);
            parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        /// Replaces the tooltip text and resizes the popup if needed.
        ///
        /// @param text the new tooltip text, or `null` to clear it
        public void setText(String text) {
            if (Objects.equals(text, toolTip.getTipText())) {
                return;
            }

            updateText(text);
            setSize(toolTip.getPreferredSize());
        }

        /// Shows this popup in `layeredPane` at the given layered-pane coordinates.
        ///
        /// @param layeredPane the popup host
        /// @param x           popup x coordinate inside `layeredPane`
        /// @param y           popup y coordinate inside `layeredPane`
        public void showTip(JLayeredPane layeredPane, int x, int y) {
            setLocation(x, y);
            if (getParent() != layeredPane) {
                layeredPane.add(this, JLayeredPane.POPUP_LAYER, 0);
            }
        }

        /// Paints directly without clearing the background first.
        ///
        /// Swing tooltip popups are frequently moved while the pointer travels, so bypassing the
        /// normal `update(...)` clear step avoids visible flicker.
        @Override
        public void update(Graphics g) {
            paint(g);
        }
    }

    static {
        ChartInteractor.register("InfoView", ChartInfoViewInteractor.class);
    }

    private boolean followingMouse;
    private transient InfoViewWindow infoViewWindow;

    /// Creates an info-view interactor for the primary y axis.
    public ChartInfoViewInteractor() {
        this(0);
    }

    /// Creates an info-view interactor for one y-axis slot.
    ///
    /// The popup stays anchored to the point that triggered the highlight until
    /// [#setFollowingMouse(boolean)] is enabled.
    ///
    /// @param yAxisIndex the y-axis slot whose renderers participate in info picking
    public ChartInfoViewInteractor(int yAxisIndex) {
        super(yAxisIndex);
        initTransientState();
    }

    private void initTransientState() {
        infoViewWindow = null;
    }

    private void showInfoViewAt(int x, int y) {
        Dimension popupSize = infoViewWindow.getSize();
        Point popupLocation = computePosition(popupSize, x, y);
        JLayeredPane layeredPane = getChart().getRootPane().getLayeredPane();
        popupLocation = SwingUtil.convertPoint(getChart().getChartArea(), popupLocation, layeredPane);
        popupLocation = adjustPosition(popupLocation, popupSize, layeredPane.getBounds());
        infoViewWindow.showTip(layeredPane, popupLocation.x, popupLocation.y);
    }

    /// Clamps one popup location into the available layered-pane bounds.
    ///
    /// @param location preferred popup location inside the layered pane
    /// @param size     current popup size
    /// @param bounds   available layered-pane bounds
    /// @return the adjusted popup location
    protected Point adjustPosition(Point location, Dimension size, Rectangle bounds) {
        int adjustedX = location.x;
        int adjustedY = location.y;

        if (size.width <= bounds.width) {
            if (adjustedX < bounds.x) {
                adjustedX = bounds.x;
            } else if (adjustedX + size.width > bounds.x + bounds.width) {
                adjustedX = bounds.x + bounds.width - size.width;
            }
        }
        if (size.height <= bounds.height) {
            if (adjustedY < bounds.y) {
                adjustedY = bounds.y;
            } else if (adjustedY + size.height > bounds.y + bounds.height) {
                adjustedY = bounds.y + bounds.height - size.height;
            }
        }
        return new Point(adjustedX, adjustedY);
    }

    /// Computes the preferred popup anchor before layered-pane clamping is applied.
    ///
    /// The default placement centers the popup horizontally over the pointer and keeps a five-pixel
    /// gap above it.
    ///
    /// @param size current popup size
    /// @param x    pointer x coordinate in chart-area coordinates
    /// @param y    pointer y coordinate in chart-area coordinates
    /// @return the preferred popup location in chart-area coordinates
    protected Point computePosition(Dimension size, int x, int y) {
        return new Point(x - size.width / 2, y - size.height - 5);
    }

    /// Creates the tooltip component wrapped by the popup window.
    ///
    /// Subclasses can override this to install a customized tooltip subclass while keeping the same
    /// popup-management logic.
    protected JToolTip createToolTip() {
        return new JToolTip();
    }

    /// Shows or hides the popup in sync with the highlight lifecycle.
    ///
    /// When [#buildInfoText(DisplayPoint)] returns `null`, the new highlight is suppressed and no
    /// interaction event is published for it.
    @Override
    protected void publishHighlightChange(DisplayPoint displayPoint, boolean highlighted,
                                          MouseEvent event) {
        if (highlighted) {
            if (infoViewWindow == null) {
                infoViewWindow = new InfoViewWindow(this);
            }

            String infoText = buildInfoText(displayPoint);
            if (infoText == null) {
                return;
            }

            infoViewWindow.setText(infoText);
            showInfoViewAt(event.getX(), event.getY());
        } else if (infoViewWindow != null) {
            infoViewWindow.hideTip();
            infoViewWindow.setText(null);
        }
        super.publishHighlightChange(displayPoint, highlighted, event);
    }

    /// Builds the HTML text shown for one highlighted point.
    ///
    /// The default implementation combines [#getInfoTextDescriptionPart(DisplayPoint)] and
    /// [#getInfoTextValuePart(DisplayPoint)] into a centered HTML tooltip and wraps each part with
    /// bidi embedding marks according to the current chart text direction.
    ///
    /// @param displayPoint the point being highlighted
    /// @return HTML tooltip text, or `null` to suppress the popup for `displayPoint`
    protected synchronized String buildInfoText(DisplayPoint displayPoint) {
        String description = getInfoTextDescriptionPart(displayPoint);
        String value = getInfoTextValuePart(displayPoint);
        if (description == null && value == null) {
            return null;
        }

        Chart chart = getChart();
        int baseTextDirection = chart.getResolvedBaseTextDirection();
        var orientation = chart.getComponentOrientation();
        description = BidiUtil.getEmbeddableCombinedString(description, baseTextDirection, orientation, false);
        value = BidiUtil.getEmbeddableCombinedString(value, baseTextDirection, orientation, false);

        StringBuilder html = new StringBuilder(96);
        Font toolTipFont = UIManager.getFont("ToolTip.font");
        if (toolTipFont == null) {
            html.append("<html>");
        } else {
            html.append("<html><font face=\"")
                    .append(toolTipFont.getName())
                    .append("\" size=\"-1\">");
        }
        if (description != null) {
            html.append("<p align=\"center\">").append(description);
        }
        if (value != null) {
            html.append("<p align=\"center\">").append(value);
        }
        html.append("</html>");
        return html.toString();
    }

    /// Returns the descriptive line shown before the formatted coordinates.
    ///
    /// The default text is the current dataset name.
    ///
    /// @param displayPoint the point being highlighted
    /// @return the descriptive tooltip line, or `null` to omit it
    protected String getInfoTextDescriptionPart(DisplayPoint displayPoint) {
        return displayPoint.getDataSet().getName();
    }

    /// Returns the formatted coordinate line shown for one highlighted point.
    ///
    /// The default text uses the chart's axis formatters and produces a tuple-like `(x;y)` string.
    ///
    /// @param displayPoint the point being highlighted
    /// @return the formatted value line, or `null` to omit it
    protected String getInfoTextValuePart(DisplayPoint displayPoint) {
        String formattedX = getChart().formatXValue(displayPoint.getXData());
        String formattedY = getChart().formatYValue(getYAxisIndex(), displayPoint.getYData());
        return "(" + formattedX + ";" + formattedY + ")";
    }

    /// Returns the live tooltip component currently hosted by the popup window.
    ///
    /// @return the active tooltip component, or `null` until the popup has been created
    protected JToolTip getInfoToolTip() {
        return (infoViewWindow != null) ? infoViewWindow.toolTip : null;
    }

    /// Hides the popup when another interactor starts a gesture on the same chart.
    @Override
    public void interactionStarted(ChartInteractor interactor, MouseEvent event) {
        DisplayPoint highlightedPoint = getHighlightedPoint();
        if (highlightedPoint != null) {
            publishHighlightChange(highlightedPoint, false, event);
            setHighlightedPoint(null);
        }
    }

    /// Returns whether the popup should keep following the pointer after the initial highlight.
    ///
    /// @return `true` when mouse movement should reposition the popup while the same point remains
    ///     highlighted
    public boolean isFollowingMouse() {
        return followingMouse;
    }

    /// Resolves the highlighted point for the current chart type.
    ///
    /// Pie charts use item picking so the popup follows slice-level hits. Other chart types reuse
    /// the point-based picking behavior from [ChartHighlightInteractor].
    @Override
    protected DisplayPoint pickData(ChartDataPicker picker) {
        if (getChart().getType() == Chart.PIE) {
            return getChart().getDisplayItem(picker);
        }
        return super.pickData(picker);
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        super.processMouseMotionEvent(event);
        if (followingMouse
                && !event.isConsumed()
                && event.getID() == MouseEvent.MOUSE_MOVED
                && getHighlightedPoint() != null
                && infoViewWindow != null) {
            showInfoViewAt(event.getX(), event.getY());
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientState();
    }

    /// Controls whether the popup should move with the pointer while the highlight stays active.
    ///
    /// @param followingMouse `true` to reposition the popup on mouse moves, `false` to keep it at
    ///                           the original anchor point
    public void setFollowingMouse(boolean followingMouse) {
        this.followingMouse = followingMouse;
    }
}
