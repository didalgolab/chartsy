package one.chartsy.charting;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.swing.JComponent;

/// Arranges a [Chart]'s fixed chrome around a single central plot component.
///
/// The layout tracks at most one component per logical slot:
/// - [#CENTER] fills the remaining rectangle.
/// - [#NORTH_TOP] and [#NORTH_BOTTOM] stack from the top down.
/// - [#SOUTH_BOTTOM] and [#SOUTH_TOP] stack from the bottom up.
/// - [#WEST] and [#EAST] consume the remaining vertical strip on either side.
/// - [#NORTH_WEST], [#SOUTH_WEST], [#NORTH_EAST], and [#SOUTH_EAST] reuse the west or east strip
///   but align it to the upper or lower part of the chart so docked legends can avoid the header
///   or footer bands.
///
/// [Chart] installs this layout permanently, adds the plot area at [#CENTER], places the header
/// at [#NORTH_TOP], places the footer at [#SOUTH_BOTTOM], and docks legends into the remaining
/// slots. [Legend#getPosition()] also relies on this layout's internal slot lookup to report the
/// legend's current docking position.
///
/// [#computeBounds(Container, Rectangle)] mirrors [#layoutContainer(Container)] without mutating
/// child components. [Chart] uses that method when it needs deterministic bounds for image or
/// server-side rendering before Swing has performed a live layout pass.
///
/// Unknown constraints are ignored. Reusing a constraint replaces only the slot tracked by this
/// layout manager; callers should remove the previous component from the parent container before
/// assigning a new component to the same slot.
///
/// **Thread-safety:** not independently thread-safe. Swing calls are expected to stay on the EDT;
/// the implementation additionally synchronizes on the container tree lock while it updates slot
/// assignments or computes bounds.
public class ChartLayout implements LayoutManager2, Serializable {

    /// Places a side component on the full left edge between the north and south bands.
    public static final String WEST = "West";

    /// Places a side component on the full right edge between the north and south bands.
    public static final String EAST = "East";

    /// Places a horizontal band above [#NORTH_BOTTOM].
    public static final String NORTH_TOP = "North_Top";

    /// Places a horizontal band directly above the central area.
    public static final String NORTH_BOTTOM = "North_Bottom";

    /// Places a horizontal band directly below the central area.
    public static final String SOUTH_BOTTOM = "South_Bottom";

    /// Places a horizontal band below [#SOUTH_BOTTOM].
    public static final String SOUTH_TOP = "South_Top";

    /// Places the west side component in the upper portion of the chart.
    public static final String NORTH_WEST = "North_West";

    /// Places the east side component in the upper portion of the chart.
    public static final String NORTH_EAST = "North_East";

    /// Places the west side component in the lower portion of the chart.
    public static final String SOUTH_WEST = "South_West";

    /// Places the east side component in the lower portion of the chart.
    public static final String SOUTH_EAST = "South_East";

    /// Marks a floating legend handled by [Chart] outside this layout manager.
    ///
    /// Passing this constant to [#addLayoutComponent(String, Component)] has no effect. [Chart]
    /// interprets it at the API level and adds the legend to a separate palette layer instead.
    public static final String ABSOLUTE = "Absolute";

    /// Places the main chart content in the remaining rectangle after all edge slots are resolved.
    public static final String CENTER = "Center";

    private Component westComponent;
    private Component eastComponent;
    private Component northTopComponent;
    private Component northBottomComponent;
    private Component southTopComponent;
    private Component southBottomComponent;
    private Component centerComponent;
    private String westConstraint;
    private String eastConstraint;
    int horizontalGap;
    int verticalGap;

    /// Returns the currently assigned constraint for a managed component.
    ///
    /// This method is package-private because [Legend#getPosition()] uses it to translate the
    /// attached legend component back into its docking slot.
    ///
    /// @param component the child component to look up
    /// @return the active layout constraint for `component`, or `null` when the component is not
    ///         tracked
    String getConstraint(Component component) {
        if (component == westComponent)
            return westConstraint;
        if (component == eastComponent)
            return eastConstraint;
        if (component == northBottomComponent)
            return NORTH_BOTTOM;
        if (component == northTopComponent)
            return NORTH_TOP;
        if (component == southBottomComponent)
            return SOUTH_BOTTOM;
        if (component == southTopComponent)
            return SOUTH_TOP;
        return component == centerComponent ? CENTER : null;
    }

    /// Registers a child component with an arbitrary constraint object.
    ///
    /// Only `String` constraints are recognized. `null` constraints and non-string constraints are
    /// ignored so higher-level chart code can decide whether the component should be managed by
    /// this layout or placed in another layer.
    ///
    /// @param component the component being added to the parent container
    /// @param constraints the layout constraint, expected to be one of this class's string constants
    @Override
    public void addLayoutComponent(Component component, Object constraints) {
        synchronized (component.getTreeLock()) {
            if (constraints instanceof String constraint)
                addLayoutComponent(constraint, component);
        }
    }

    /// Registers a child component in one of the named chart slots.
    ///
    /// The layout stores only a single component reference per slot. Adding another component with
    /// the same constraint replaces the tracked slot occupant without removing the old component
    /// from the parent container.
    ///
    /// Recognized constraints are [#CENTER], [#NORTH_TOP], [#NORTH_BOTTOM], [#SOUTH_TOP],
    /// [#SOUTH_BOTTOM], [#WEST], [#EAST], [#NORTH_WEST], [#SOUTH_WEST], [#NORTH_EAST], and
    /// [#SOUTH_EAST]. Other values, including [#ABSOLUTE], are ignored.
    ///
    /// @param constraint the constraint naming the slot to update
    /// @param component the component assigned to that slot
    @Override
    public void addLayoutComponent(String constraint, Component component) {
        if (constraint == null)
            return;

        synchronized (component.getTreeLock()) {
            switch (constraint) {
                case NORTH_WEST, SOUTH_WEST, WEST -> {
                    westConstraint = constraint;
                    westComponent = component;
                }
                case NORTH_EAST, SOUTH_EAST, EAST -> {
                    eastConstraint = constraint;
                    eastComponent = component;
                }
                case NORTH_TOP -> northTopComponent = component;
                case NORTH_BOTTOM -> northBottomComponent = component;
                case SOUTH_TOP -> southTopComponent = component;
                case SOUTH_BOTTOM -> southBottomComponent = component;
                case CENTER -> centerComponent = component;
                default -> {
                }
            }
        }
    }

    /// Computes the rectangles that [#layoutContainer(Container)] would assign inside a target
    /// bounds rectangle.
    ///
    /// The returned map contains only components currently tracked by this layout. Bounds are
    /// derived from each child's preferred size and from the container's insets, using the same
    /// two-pass algorithm as the live layout method. The returned rectangles are expressed in the
    /// same coordinate system as `bounds`.
    ///
    /// Side components docked as [#NORTH_WEST], [#SOUTH_WEST], [#NORTH_EAST], or [#SOUTH_EAST]
    /// also shrink the corresponding north or south bands so the final rectangles match interactive
    /// legend docking.
    ///
    /// The container must be a [JComponent]. This method stores `bounds` under
    /// [ServerSideLayout#BOUNDS_PROPERTY] on that component before performing the calculation.
    ///
    /// @param parent the container whose children are being measured
    /// @param bounds the available drawing rectangle, in the container's coordinate system
    /// @return a newly allocated map from managed components to their computed bounds
    public Map<Component, Rectangle> computeBounds(Container parent, Rectangle bounds) {
        synchronized (parent.getTreeLock()) {
            ((JComponent) parent).putClientProperty(ServerSideLayout.BOUNDS_PROPERTY, bounds);
            return computeLayout(parent, bounds, false);
        }
    }

    /// Returns the horizontal alignment hint expected by [LayoutManager2].
    ///
    /// @param parent the parent container
    /// @return `0.5f` to indicate centered alignment semantics
    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    /// Returns the vertical alignment hint expected by [LayoutManager2].
    ///
    /// @param parent the parent container
    /// @return `0.5f` to indicate centered alignment semantics
    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.5f;
    }

    /// Invalidates cached layout data.
    ///
    /// This implementation keeps no derived cache, so the method intentionally does nothing.
    ///
    /// @param parent the container whose layout became invalid
    @Override
    public void invalidateLayout(Container parent) {
    }

    /// Lays out the container's managed components in place.
    ///
    /// The algorithm matches [#computeBounds(Container, Rectangle)] and uses each component's
    /// preferred size when allocating the fixed north, south, west, and east slots. The center
    /// component receives the remaining rectangle.
    ///
    /// @param parent the container whose children should be resized and repositioned
    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            computeLayout(parent, new Rectangle(parent.getSize()), true);
        }
    }

    /// Returns the largest size this layout is willing to accept.
    ///
    /// @param parent the parent container
    /// @return an effectively unbounded size
    @Override
    public Dimension maximumLayoutSize(Container parent) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /// Returns the minimum size required by the currently managed components.
    ///
    /// The calculation mirrors the slot model used during layout:
    /// - north and south bands contribute cumulative height and the widest required width
    /// - west and east bands contribute cumulative width and the tallest required height
    /// - container insets are added last
    ///
    /// @param parent the parent container
    /// @return the minimum size that can host all currently tracked components
    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return aggregateLayoutSize(parent, Component::getMinimumSize);
    }

    /// Returns the preferred size required by the currently managed components.
    ///
    /// The computation is identical to [#minimumLayoutSize(Container)] except that it queries
    /// preferred sizes instead of minimum sizes.
    ///
    /// @param parent the parent container
    /// @return the preferred size that can host all currently tracked components
    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return aggregateLayoutSize(parent, Component::getPreferredSize);
    }

    /// Removes a managed component from whichever slot currently references it.
    ///
    /// Slot-orientation markers for the west and east positions are left unchanged; they become
    /// relevant again only after another component is assigned to the corresponding side slot.
    ///
    /// @param component the component to detach from the layout
    @Override
    public void removeLayoutComponent(Component component) {
        synchronized (component.getTreeLock()) {
            var constraint = getConstraint(component);
            if (constraint == null)
                return;

            switch (constraint) {
                case WEST, NORTH_WEST, SOUTH_WEST -> westComponent = null;
                case EAST, NORTH_EAST, SOUTH_EAST -> eastComponent = null;
                case NORTH_TOP -> northTopComponent = null;
                case NORTH_BOTTOM -> northBottomComponent = null;
                case SOUTH_TOP -> southTopComponent = null;
                case SOUTH_BOTTOM -> southBottomComponent = null;
                case CENTER -> centerComponent = null;
                default -> {
                }
            }
        }
    }

    private Dimension aggregateLayoutSize(Container parent, Function<Component, Dimension> sizeProvider) {
        synchronized (parent.getTreeLock()) {
            var size = new Dimension();

            addBandSize(size, northTopComponent, sizeProvider);
            addBandSize(size, northBottomComponent, sizeProvider);
            addBandSize(size, southBottomComponent, sizeProvider);
            addBandSize(size, southTopComponent, sizeProvider);
            addCenterSize(size, centerComponent, sizeProvider);
            addSideSize(size, westComponent, sizeProvider);
            addSideSize(size, eastComponent, sizeProvider);

            var insets = parent.getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }
    }

    private void addBandSize(
            Dimension aggregate,
            Component component,
            Function<Component, Dimension> sizeProvider) {

        if (component == null)
            return;

        var size = sizeProvider.apply(component);
        aggregate.width = Math.max(aggregate.width, size.width);
        aggregate.height += size.height + verticalGap;
    }

    private void addCenterSize(
            Dimension aggregate,
            Component component,
            Function<Component, Dimension> sizeProvider) {

        if (component == null)
            return;

        var size = sizeProvider.apply(component);
        aggregate.width = Math.max(aggregate.width, size.width);
        aggregate.height += size.height;
    }

    private void addSideSize(
            Dimension aggregate,
            Component component,
            Function<Component, Dimension> sizeProvider) {

        if (component == null)
            return;

        var size = sizeProvider.apply(component);
        aggregate.width += size.width + horizontalGap;
        aggregate.height = Math.max(aggregate.height, size.height);
    }

    private Map<Component, Rectangle> computeLayout(Container parent, Rectangle bounds, boolean apply) {
        var computed = new HashMap<Component, Rectangle>();
        var insets = parent.getInsets();

        var area = LayoutArea.of(bounds, insets);
        placeNorthBand(northTopComponent, area, computed, apply);
        placeNorthBand(northBottomComponent, area, computed, apply);
        placeSouthBand(southBottomComponent, area, computed, apply);
        placeSouthBand(southTopComponent, area, computed, apply);
        placeEastSide(eastComponent, area, computed, apply);
        placeWestSide(westComponent, area, computed, apply);
        place(centerComponent, area.toRectangle(), computed, apply);

        adjustWestDock(bounds, insets, computed, apply);
        adjustEastDock(bounds, insets, computed, apply);
        return computed;
    }

    private void placeNorthBand(
            Component component,
            LayoutArea area,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (component == null)
            return;

        var preferred = preferredForHorizontalBand(component, area.width(), apply);
        place(component, new Rectangle(area.left, area.top, area.width(), preferred.height), computed, apply);
        area.top += preferred.height + verticalGap;
    }

    private void placeSouthBand(
            Component component,
            LayoutArea area,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (component == null)
            return;

        var preferred = preferredForHorizontalBand(component, area.width(), apply);
        place(component, new Rectangle(area.left, area.bottom - preferred.height, area.width(), preferred.height), computed, apply);
        area.bottom -= preferred.height + verticalGap;
    }

    private void placeEastSide(
            Component component,
            LayoutArea area,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (component == null)
            return;

        var preferred = preferredForVerticalBand(component, area.height(), apply);
        place(component, new Rectangle(area.right - preferred.width, area.top, preferred.width, area.height()), computed, apply);
        area.right -= preferred.width + horizontalGap;
    }

    private void placeWestSide(
            Component component,
            LayoutArea area,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (component == null)
            return;

        var preferred = preferredForVerticalBand(component, area.height(), apply);
        place(component, new Rectangle(area.left, area.top, preferred.width, area.height()), computed, apply);
        area.left += preferred.width + horizontalGap;
    }

    private Dimension preferredForHorizontalBand(Component component, int width, boolean apply) {
        if (apply)
            component.setSize(width, component.getSize().height);
        return component.getPreferredSize();
    }

    private Dimension preferredForVerticalBand(Component component, int height, boolean apply) {
        if (apply)
            component.setSize(component.getSize().width, height);
        return component.getPreferredSize();
    }

    private void adjustWestDock(
            Rectangle bounds,
            Insets insets,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (westComponent == null)
            return;

        var preferred = westComponent.getPreferredSize();
        var trim = preferred.width + horizontalGap;
        var area = LayoutArea.of(bounds, insets);

        switch (westConstraint) {
            case NORTH_WEST -> {
                var bottom = topOfSouthArea(area.bottom, computed);
                place(westComponent, new Rectangle(area.left, area.top, preferred.width, bottom - area.top), computed, apply);
                trimFromLeft(northTopComponent, trim, computed, apply);
                trimFromLeft(northBottomComponent, trim, computed, apply);
            }
            case SOUTH_WEST -> {
                var top = bottomOfNorthArea(area.top, computed);
                place(westComponent, new Rectangle(area.left, top, preferred.width, area.bottom - top), computed, apply);
                trimFromLeft(southTopComponent, trim, computed, apply);
                trimFromLeft(southBottomComponent, trim, computed, apply);
            }
            default -> {
            }
        }
    }

    private void adjustEastDock(
            Rectangle bounds,
            Insets insets,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (eastComponent == null)
            return;

        var preferred = eastComponent.getPreferredSize();
        var trim = preferred.width + horizontalGap;
        var area = LayoutArea.of(bounds, insets);

        switch (eastConstraint) {
            case NORTH_EAST -> {
                var bottom = topOfSouthArea(area.bottom, computed);
                place(eastComponent, new Rectangle(area.right - preferred.width, area.top, preferred.width, bottom - area.top), computed, apply);
                trimFromRight(northTopComponent, trim, computed, apply);
                trimFromRight(northBottomComponent, trim, computed, apply);
            }
            case SOUTH_EAST -> {
                var top = bottomOfNorthArea(area.top, computed);
                place(eastComponent, new Rectangle(area.right - preferred.width, top, preferred.width, area.bottom - top), computed, apply);
                trimFromRight(southBottomComponent, trim, computed, apply);
                trimFromRight(southTopComponent, trim, computed, apply);
            }
            default -> {
            }
        }
    }

    private int topOfSouthArea(int fallback, Map<Component, Rectangle> computed) {
        var limit = fallback;
        limit = Math.min(limit, gapAbove(computed.get(southBottomComponent)));
        limit = Math.min(limit, gapAbove(computed.get(southTopComponent)));
        return limit;
    }

    private int bottomOfNorthArea(int fallback, Map<Component, Rectangle> computed) {
        var limit = fallback;
        limit = Math.max(limit, gapBelow(computed.get(northTopComponent)));
        limit = Math.max(limit, gapBelow(computed.get(northBottomComponent)));
        return limit;
    }

    private int gapAbove(Rectangle bounds) {
        return bounds == null ? Integer.MAX_VALUE : bounds.y - verticalGap;
    }

    private int gapBelow(Rectangle bounds) {
        return bounds == null ? Integer.MIN_VALUE : bounds.y + bounds.height + verticalGap;
    }

    private void trimFromLeft(
            Component component,
            int amount,
            Map<Component, Rectangle> computed,
            boolean apply) {

        var bounds = computed.get(component);
        if (bounds != null)
            place(component, new Rectangle(bounds.x + amount, bounds.y, bounds.width - amount, bounds.height), computed, apply);
    }

    private void trimFromRight(
            Component component,
            int amount,
            Map<Component, Rectangle> computed,
            boolean apply) {

        var bounds = computed.get(component);
        if (bounds != null)
            place(component, new Rectangle(bounds.x, bounds.y, bounds.width - amount, bounds.height), computed, apply);
    }

    private void place(
            Component component,
            Rectangle bounds,
            Map<Component, Rectangle> computed,
            boolean apply) {

        if (component == null)
            return;

        var copy = new Rectangle(bounds);
        computed.put(component, copy);
        if (apply)
            component.setBounds(copy);
    }

    private static final class LayoutArea {
        private int top;
        private int bottom;
        private int left;
        private int right;

        private LayoutArea(int top, int bottom, int left, int right) {
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
        }

        static LayoutArea of(Rectangle bounds, Insets insets) {
            return new LayoutArea(
                    bounds.y + insets.top,
                    bounds.y + bounds.height - insets.bottom,
                    bounds.x + insets.left,
                    bounds.x + bounds.width - insets.right);
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        Rectangle toRectangle() {
            return new Rectangle(left, top, width(), height());
        }
    }
}
