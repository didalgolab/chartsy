package one.chartsy.charting;

import java.applet.Applet;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.Serializable;

import javax.swing.JComponent;

import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartDrawEvent;
import one.chartsy.charting.event.ChartDrawListener;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.util.GraphicUtil;

/// Keeps a chart area's fixed margins aligned with a reference rectangle taken from another Swing
/// component.
///
/// The synchronizer converts that reference rectangle into the owning chart area's coordinate space
/// and rewrites either the horizontal or vertical margins so the chart's draw rectangle tracks the
/// same visual bounds. The default [#synchronize(Chart, Component)] variant aligns left and right
/// margins with the target component's inset-adjusted bounds, while [MultiChartSync] narrows the
/// reference to another chart area's live plot rectangle for plot-area synchronization.
///
/// Instances observe target component movement and resizing. If the source chart is not yet
/// showing, the first synchronization pass is deferred until the chart draws for the first time so
/// layout is not forced against an unrealized Swing hierarchy.
///
/// ### API Note
///
/// This type coordinates live AWT and Swing component geometry. Install and remove it on the UI
/// thread that owns the participating components.
public class ChartAreaSynchronizer implements Serializable {

    static final String CLIENT_PROPERTY_KEY = "_ChartAreaSync";

    /// Synchronizes the chart area's left and right margins.
    public static final int HORIZONTAL = 0;

    /// Synchronizes the chart area's top and bottom margins.
    public static final int VERTICAL = 1;

    private final ComponentListener componentListener;
    private final ChartListener targetChartListener;
    private final ChartDrawListener deferredSyncListener;
    private final int orientation;

    private Chart chart;
    private Component targetComponent;

    /// One-shot draw listener that performs the deferred first synchronization pass.
    ///
    /// [ChartAreaSynchronizer#bind(Chart, Component)] installs this listener only when the bound
    /// chart is not yet showing. Waiting until `afterDraw` avoids forcing margin computation
    /// against an unrealized Swing hierarchy and ensures the chart area already has a current draw
    /// rectangle.
    private class DeferredSyncListener implements ChartDrawListener, Serializable {

        /// Removes this one-shot listener and runs the first deferred margin synchronization.
        @Override
        public void afterDraw(ChartDrawEvent event) {
            event.getChart().removeChartDrawListener(this);
            synchronizeMargins();
        }

        /// Does nothing because the deferred synchronization needs the post-draw geometry.
        @Override
        public void beforeDraw(ChartDrawEvent event) {
        }
    }

    /// Listener used by subclasses that derive the reference rectangle from another chart.
    ///
    /// [MultiChartSync] registers this listener with the target chart so plot-area synchronization
    /// is refreshed after that chart finishes a layout pass and publishes a new plot rectangle.
    /// The event payload is not inspected because [#synchronizeMargins()] always reads the current
    /// geometry directly from the bound target component.
    private class TargetChartListener implements ChartListener, Serializable {

        /// Re-synchronizes margins after the target chart reports a chart-area change.
        @Override
        public void chartAreaChanged(ChartAreaEvent event) {
            synchronizeMargins();
        }
    }

    /// Listener that tracks movement and resizing of the bound reference component.
    ///
    /// The default synchronizer derives its reference rectangle from the component's live bounds,
    /// so any geometry change requires the chart-area margins to be recomputed.
    private class TargetComponentListener extends ComponentAdapter implements Serializable {

        /// Re-synchronizes margins after the bound component moves within its container hierarchy.
        @Override
        public void componentMoved(ComponentEvent event) {
            synchronizeMargins();
        }

        /// Re-synchronizes margins after the bound component changes size.
        @Override
        public void componentResized(ComponentEvent event) {
            synchronizeMargins();
        }
    }

    /// Creates a synchronizer that updates one pair of chart-area margins from a target rectangle.
    ///
    /// Pass [#HORIZONTAL] to keep left and right margins aligned or [#VERTICAL] to keep top and
    /// bottom margins aligned.
    ///
    /// @param orientation the margin direction to synchronize
    public ChartAreaSynchronizer(int orientation) {
        componentListener = new TargetComponentListener();
        targetChartListener = new TargetChartListener();
        deferredSyncListener = new DeferredSyncListener();
        this.orientation = orientation;
    }

    /// Returns the synchronizer currently installed on `chart`, if any.
    ///
    /// The binding is stored as a chart-area client property so layout code can consult it without
    /// keeping a separate registry.
    static ChartAreaSynchronizer getInstalledSynchronizer(Chart chart) {
        return (ChartAreaSynchronizer) chart.getChartArea().getClientProperty(CLIENT_PROPERTY_KEY);
    }

    @SuppressWarnings("removal")
    private static Container findCoordinateRoot(Component component) {
        Container root = (component instanceof Container container) ? container : component.getParent();
        while (root != null) {
            if (root instanceof Window || root instanceof Applet)
                return root;
            root = root.getParent();
        }
        return null;
    }

    private static Rectangle convertRectangle(
            Component sourceComponent,
            Rectangle rectangle,
            Component destinationComponent
    ) {
        if (sourceComponent == null && destinationComponent == null)
            return rectangle;

        Container sourceRoot = null;
        if (sourceComponent != null) {
            sourceRoot = findCoordinateRoot(sourceComponent);
            if (sourceRoot == null)
                throw new IllegalArgumentException(
                        "Source component not connected to component tree hierarchy");
        }

        Container destinationRoot = null;
        if (destinationComponent != null) {
            destinationRoot = findCoordinateRoot(destinationComponent);
            if (destinationRoot == null)
                throw new IllegalArgumentException(
                        "Destination component not connected to component tree hierarchy");
        }

        if (sourceComponent == null) {
            sourceComponent = destinationRoot;
            sourceRoot = destinationRoot;
        }
        if (destinationComponent == null) {
            destinationComponent = sourceRoot;
            destinationRoot = sourceRoot;
        }

        int sourceX = 0;
        int sourceY = 0;
        Component current = sourceComponent;
        while (current != sourceRoot) {
            sourceX += current.getX();
            sourceY += current.getY();
            current = current.getParent();
        }

        int destinationX = 0;
        int destinationY = 0;
        current = destinationComponent;
        while (current != destinationRoot) {
            destinationX += current.getX();
            destinationY += current.getY();
            current = current.getParent();
        }

        if (sourceRoot == destinationRoot) {
            return new Rectangle(
                    rectangle.x + sourceX - destinationX,
                    rectangle.y + sourceY - destinationY,
                    rectangle.width,
                    rectangle.height);
        }

        Point sourceScreenLocation = sourceRoot.getLocationOnScreen();
        Point destinationScreenLocation = destinationRoot.getLocationOnScreen();
        return new Rectangle(
                rectangle.x + sourceX + sourceScreenLocation.x - destinationScreenLocation.x - destinationX,
                rectangle.y + sourceY + sourceScreenLocation.y - destinationScreenLocation.y - destinationY,
                rectangle.width,
                rectangle.height);
    }

    /// Installs a horizontal synchronizer for `chart`.
    ///
    /// This is equivalent to passing `new ChartAreaSynchronizer(HORIZONTAL)` to
    /// [#synchronize(Chart, Component, ChartAreaSynchronizer)].
    ///
    /// @param chart the chart whose margins should track the target component
    /// @param targetComponent the component providing the reference rectangle
    public static void synchronize(Chart chart, Component targetComponent) {
        synchronize(chart, targetComponent, new ChartAreaSynchronizer(HORIZONTAL));
    }

    /// Replaces any previously installed synchronizer on `chart`.
    ///
    /// The new synchronizer is bound immediately and remembered on the chart area until
    /// [#unSynchronize(Chart)] removes it.
    ///
    /// @param chart the chart whose margins should be synchronized
    /// @param targetComponent the component providing the reference rectangle
    /// @param synchronizer the synchronizer strategy to install
    public static void synchronize(
            Chart chart,
            Component targetComponent,
            ChartAreaSynchronizer synchronizer
    ) {
        unSynchronize(chart);
        synchronizer.bind(chart, targetComponent);
        chart.getChartArea().putClientProperty(CLIENT_PROPERTY_KEY, synchronizer);
    }

    /// Removes the synchronizer currently installed on `chart`.
    ///
    /// @param chart the chart whose synchronizer should be detached
    /// @return the detached synchronizer, or `null` if `chart` was not synchronized
    public static ChartAreaSynchronizer unSynchronize(Chart chart) {
        ChartAreaSynchronizer synchronizer = getInstalledSynchronizer(chart);
        if (synchronizer != null) {
            synchronizer.unbind();
            chart.getChartArea().putClientProperty(CLIENT_PROPERTY_KEY, null);
        }
        return synchronizer;
    }

    /// Installs the listeners needed to keep the chart margins in sync.
    ///
    /// Subclasses that observe additional state should call `super` before adding their own
    /// listeners.
    void installListeners() {
        targetComponent.addComponentListener(componentListener);
    }

    /// Computes the fixed margins needed for `drawRect` to match `referenceRect`.
    ///
    /// Only the margin pair selected by this synchronizer's orientation is populated.
    ///
    /// @param drawRect the current chart-area draw rectangle
    /// @param referenceRect the target rectangle converted into chart-area coordinates
    /// @return the margins required to align the chart area with the reference rectangle
    Insets computeMargins(Rectangle drawRect, Rectangle referenceRect) {
        Insets margins = new Insets(0, 0, 0, 0);
        if (orientation != HORIZONTAL) {
            margins.top = referenceRect.y - drawRect.y;
            margins.bottom = drawRect.height - referenceRect.height - margins.top;
        } else {
            margins.left = referenceRect.x - drawRect.x;
            margins.right = drawRect.width - referenceRect.width - margins.left;
        }
        return margins;
    }

    /// Removes the listeners installed by [#installListeners()].
    ///
    /// Subclasses overriding this method should release their own listeners after calling `super`.
    void removeListeners() {
        targetComponent.removeComponentListener(componentListener);
    }

    /// Recomputes the owning chart area's fixed margins from the current reference rectangle.
    ///
    /// Failures caused by components not yet being fully attached to a realized hierarchy are
    /// ignored so a later move, resize, or draw callback can retry the synchronization.
    void synchronizeMargins() {
        try {
            Rectangle referenceRect = getReferenceRect();
            Rectangle chartAreaReference = convertRectangle(
                    targetComponent.getParent(), referenceRect, chart.getChartArea());
            Rectangle drawRect = chart.getChartArea().getDrawRect();
            Insets margins = computeMargins(drawRect, chartAreaReference);
            if (orientation != HORIZONTAL)
                chart.getChartArea().setVerticalMargins(margins.top, margins.bottom);
            else
                chart.getChartArea().setHorizontalMargins(margins.left, margins.right);
        } catch (Exception ignored) {
        }
    }

    /// Returns the chart listener used by subclasses that also track a target chart.
    final ChartListener getTargetChartListener() {
        return targetChartListener;
    }

    /// Returns the chart currently bound to this synchronizer.
    ///
    /// @return the synchronized chart, or `null` before [#bind(Chart, Component)]
    public final Chart getChart() {
        return chart;
    }

    /// Returns the component supplying the reference rectangle.
    ///
    /// @return the bound target component, or `null` before [#bind(Chart, Component)]
    public final Component getTargetComponent() {
        return targetComponent;
    }

    /// Returns the target-side rectangle that should align with the chart area's draw rectangle.
    ///
    /// The default implementation uses the target component's bounds minus any [JComponent]
    /// insets.
    Rectangle getReferenceRect() {
        Rectangle referenceRect = targetComponent.getBounds();
        if (targetComponent instanceof JComponent component)
            referenceRect = GraphicUtil.applyInsets(referenceRect, component.getInsets());
        return referenceRect;
    }

    /// Binds this synchronizer to `chart` and `targetComponent`.
    ///
    /// Callers normally go through [#synchronize(Chart, Component, ChartAreaSynchronizer)] so the
    /// binding is also registered on the chart area. If the chart is already showing, margins are
    /// synchronized immediately; otherwise the first update is deferred until the next draw pass.
    ///
    /// @param chart the chart whose margins should be updated
    /// @param targetComponent the component providing the reference rectangle
    public void bind(Chart chart, Component targetComponent) {
        this.chart = chart;
        this.targetComponent = targetComponent;
        installListeners();
        if (chart.isShowing())
            synchronizeMargins();
        else
            chart.addChartDrawListener(deferredSyncListener);
    }

    /// Removes listeners installed by [#bind(Chart, Component)].
    ///
    /// After this call the instance no longer updates margins until it is bound again.
    public void unbind() {
        removeListeners();
        chart.removeChartDrawListener(deferredSyncListener);
    }
}
