package one.chartsy.charting;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.event.EventListenerList;

import one.chartsy.charting.event.ChartInteractionEvent;
import one.chartsy.charting.event.ChartInteractionListener;
import one.chartsy.charting.internal.ChartInteractorManager;
import one.chartsy.charting.util.swing.EventUtil;

/// Base class for chart gestures coordinated by [ChartInteractorManager].
///
/// A `ChartInteractor` packages one family of user input behavior, such as panning, drag-box
/// zooming, point picking, or point editing. The manager attaches interactors to one [Chart],
/// filters incoming AWT event categories through [#enableEvents(long)] and
/// [#disableEvents(long)], and offers mouse input only when [#isHandling(int, int)] accepts the
/// current pointer location or the interactor is already inside an operation.
///
/// ### Lifecycle
///
/// Interactors are mutable chart-side UI objects that belong to at most one chart at a time.
/// Subclasses typically commit to a gesture by calling [#startOperation(MouseEvent)] from a mouse
/// press handler, keep transient drag state while [#isInOperation()] remains `true`, and then
/// terminate that gesture through [#endOperation(MouseEvent)] or [#abort()]. The ownership hook
/// [#chartConnected(Chart, Chart)] runs after [#setChart(Chart)] updates the backing chart field,
/// so overrides may inspect [#getChart()] immediately.
///
/// ### Registry
///
/// Subclasses may expose a stable external name through [#register(String, Class)] so charts,
/// demos, or configuration code can create new instances with [#create(String)]. Registry-backed
/// creation always uses a public no-argument constructor.
///
/// ### Ghost Painting
///
/// The base class supports transient preview painting for drag gestures. Non-XOR ghosts schedule a
/// repaint for the rectangle returned by [#getGhostBounds()]. XOR ghosts draw immediately into the
/// chart area graphics after [#initGhostGraphics(Graphics)] configures the graphics context.
///
/// Instances are not thread-safe and are expected to be used from the Swing event-dispatch thread.
public abstract class ChartInteractor implements Serializable {
    private static final Map<String, Class<? extends ChartInteractor>> registeredInteractorClassesByName =
            new HashMap<>();

    /// Priority reserved for infrastructure interactors that must run before normal tools.
    public static final int INTERNAL_HIGH = 1000;

    /// Default priority used by ordinary user-facing interactors.
    public static final int NORMAL = 0;

    /// Priority reserved for infrastructure interactors that should run after normal tools.
    public static final int INTERNAL_LOW = -1000;

    private EventListenerList chartInteractionListeners;
    private Chart chart;
    private int priority;
    private long awtEventMask;
    private transient boolean aborted;
    private transient boolean inOperation;
    private int yAxisIndex;
    private int eventMask;
    private int eventMaskEx;
    private boolean consumeEvents = true;
    private Color xorColor = Color.white;
    private Color ghostColor = Color.black;
    private boolean ghostDrawingAllowed;
    private boolean xorGhost;

    /// Returns a new interactor instance resolved from `name`.
    ///
    /// This convenience overload suppresses reflective lookup and construction failures. When
    /// creation fails, it returns `null` after delegating error handling to
    /// [#create(String, boolean)] with `failOnError == false`.
    ///
    /// @param name registered short name or fully qualified class name
    /// @return a newly created interactor, or `null` when the name cannot be resolved or
    ///     instantiated
    public static ChartInteractor create(String name) {
        try {
            return create(name, false);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException exception) {
            return null;
        }
    }

    /// Returns a new interactor instance resolved from `name`.
    ///
    /// Resolution first consults the short-name registry populated by [#register(String, Class)].
    /// When no registered name matches, the method falls back to `Class.forName(...)`. The
    /// resolved class must be a [ChartInteractor] subtype with a public no-argument constructor.
    ///
    /// @param name registered short name or fully qualified class name
    /// @param failOnError `true` to propagate reflective lookup failures, `false` to print them and
    ///     return `null`
    /// @return a newly created interactor, or `null` when creation fails and `failOnError` is
    ///     `false`
    /// @throws ClassNotFoundException if `name` cannot be resolved and `failOnError` is `true`
    /// @throws InstantiationException if the resolved class is not instantiable through a public
    ///     no-argument constructor and `failOnError` is `true`
    /// @throws IllegalAccessException if the no-argument constructor is inaccessible and
    ///     `failOnError` is `true`
    public static ChartInteractor create(String name, boolean failOnError)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<? extends ChartInteractor> interactorClass = registeredInteractorClassesByName.get(name);
        if (interactorClass == null) {
            try {
                interactorClass = Class.forName(name).asSubclass(ChartInteractor.class);
            } catch (ClassNotFoundException exception) {
                if (failOnError)
                    throw exception;
                exception.printStackTrace();
                return null;
            } catch (ClassCastException exception) {
                InstantiationException wrapped = new InstantiationException(name);
                wrapped.initCause(exception);
                if (failOnError)
                    throw wrapped;
                wrapped.printStackTrace();
                return null;
            }
        }

        try {
            return interactorClass.getConstructor().newInstance();
        } catch (NoSuchMethodException exception) {
            InstantiationException wrapped = new InstantiationException(interactorClass.getName());
            wrapped.initCause(exception);
            if (failOnError)
                throw wrapped;
            wrapped.printStackTrace();
        } catch (InvocationTargetException exception) {
            InstantiationException wrapped = new InstantiationException(interactorClass.getName());
            wrapped.initCause(exception.getTargetException());
            if (failOnError)
                throw wrapped;
            wrapped.printStackTrace();
        } catch (InstantiationException exception) {
            if (failOnError)
                throw exception;
            exception.printStackTrace();
        } catch (IllegalAccessException exception) {
            if (failOnError)
                throw exception;
            exception.printStackTrace();
        }
        return null;
    }

    /// Returns the class currently registered under `name`, if any.
    ///
    /// This method consults only the short-name registry. It does not attempt `Class.forName(...)`.
    ///
    /// @param name short name previously supplied to [#register(String, Class)]
    /// @return the registered interactor class, or `null` when the name is unknown
    public static synchronized Class<? extends ChartInteractor> getInteractorClassByName(String name) {
        return registeredInteractorClassesByName.get(name);
    }

    /// Resolves a user-facing display name for `interactorClass`.
    ///
    /// The lookup order is:
    /// 1. a public static `getLocalizedName(Locale)` method declared directly on
    ///    `interactorClass`
    /// 2. the short name registered through [#register(String, Class)]
    /// 3. the simple Java class name
    ///
    /// Failures in the reflective hook are logged and the method continues with the fallback chain.
    ///
    /// @param interactorClass interactor class to describe
    /// @param locale locale requested by the caller
    /// @return a localized display name, the registered short name, or the simple class name
    public static String getLocalizedName(Class<? extends ChartInteractor> interactorClass, Locale locale) {
        Method localizedNameMethod = null;
        try {
            localizedNameMethod = interactorClass.getMethod("getLocalizedName", Locale.class);
        } catch (NoSuchMethodException | SecurityException exception) {
            // Fall through to the short-name and simple-name fallbacks below.
        }

        if (localizedNameMethod != null
                && Modifier.isStatic(localizedNameMethod.getModifiers())
                && localizedNameMethod.getDeclaringClass() == interactorClass) {
            try {
                Object localizedName = localizedNameMethod.invoke(null, locale);
                if (localizedName instanceof String name)
                    return name;
            } catch (IllegalAccessException | IllegalArgumentException exception) {
                exception.printStackTrace();
            } catch (InvocationTargetException exception) {
                exception.getTargetException().printStackTrace();
            }
        }

        String shortName = getShortName(interactorClass);
        if (shortName != null)
            return shortName;

        String className = interactorClass.getName();
        int lastDot = className.lastIndexOf('.');
        return (lastDot >= 0) ? className.substring(lastDot + 1) : className;
    }

    /// Returns a snapshot of all registered short names.
    ///
    /// The returned array reflects the registry state at call time. Its iteration order matches the
    /// current [HashMap] iteration order and is therefore not a stable API guarantee.
    ///
    /// @return snapshot array containing every currently registered short name
    public static synchronized String[] getRegisteredInteractorsByName() {
        return registeredInteractorClassesByName.keySet().toArray(String[]::new);
    }

    /// Returns the registered short name for `interactorClass`, if one exists.
    ///
    /// @param interactorClass class to resolve
    /// @return the registered short name, or `null` when the class is not currently registered
    public static synchronized String getShortName(Class<? extends ChartInteractor> interactorClass) {
        for (Map.Entry<String, Class<? extends ChartInteractor>> entry
                : registeredInteractorClassesByName.entrySet()) {
            if (interactorClass == entry.getValue())
                return entry.getKey();
        }
        return null;
    }

    /// Registers `shortName` so [#create(String)] can resolve it later.
    ///
    /// Subclasses usually call this from a static initializer. Registering the same short name more
    /// than once replaces the previous mapping.
    ///
    /// @param shortName external identifier for the interactor
    /// @param interactorClass concrete interactor class created for `shortName`
    protected static synchronized void register(String shortName,
            Class<? extends ChartInteractor> interactorClass) {
        registeredInteractorClassesByName.put(shortName, interactorClass);
    }

    /// Creates an interactor bound to the primary y-axis slot with no modifier requirement.
    public ChartInteractor() {
        this(0, 0);
    }

    /// Creates an interactor configured for one y-axis slot and one mouse-modifier combination.
    ///
    /// `eventMask` stores the legacy modifier-mask form used throughout this module. The
    /// constructor immediately caches the equivalent extended mask through
    /// [EventUtil#convertModifiersMask(int)] so subclasses can compare it directly with
    /// [MouseEvent#getModifiersEx()].
    ///
    /// @param yAxisIndex y-axis slot this interactor should target
    /// @param eventMask legacy modifier mask that must match before a gesture may start
    public ChartInteractor(int yAxisIndex, int eventMask) {
        priority = NORMAL;
        this.yAxisIndex = yAxisIndex;
        this.eventMask = eventMask;
        eventMaskEx = EventUtil.convertModifiersMask(eventMask);
    }

    /// Aborts the current gesture without completing it normally.
    ///
    /// The base implementation records the aborted state and clears [#isInOperation()]. Subclasses
    /// typically extend this to release cursors, repaint ghost overlays, or restore axis adjusting
    /// flags.
    protected void abort() {
        setAborted(true);
        setInOperation(false);
    }

    /// Registers a listener for semantic interaction events emitted by this interactor.
    ///
    /// Subclasses publish those events through [#fireChartInteractionEvent(ChartInteractionEvent)]
    /// after a gesture produces a meaningful result such as a pick, edit, or highlight change.
    ///
    /// @param listener listener to add
    public final void addChartInteractionListener(ChartInteractionListener listener) {
        if (chartInteractionListeners == null)
            chartInteractionListeners = new EventListenerList();
        chartInteractionListeners.add(ChartInteractionListener.class, listener);
    }

    /// Reacts to a change in the owning chart reference.
    ///
    /// [ChartInteractorManager] invokes this through [#setChart(Chart)] after the backing chart
    /// field has already been updated. The default implementation does nothing.
    ///
    /// @param previousChart previous owner, or `null` when the interactor was not attached
    /// @param chart new owner, or `null` when the interactor has just been detached
    protected void chartConnected(Chart previousChart, Chart chart) {
    }

    /// Removes selected AWT event categories from this interactor's manager-visible interest mask.
    ///
    /// This mask is independent from the mouse-modifier mask configured through
    /// [#setEventMask(int)].
    ///
    /// @param awtEventMask AWT event-category bits to disable
    protected final void disableEvents(long awtEventMask) {
        this.awtEventMask &= ~awtEventMask;
    }

    /// Repaints or redraws the current ghost overlay.
    ///
    /// The method is a no-op when the interactor is detached, ghost drawing is disabled, or
    /// [#getGhostBounds()] returns `null`. Non-XOR ghosts request a repaint from the chart area.
    /// XOR ghosts draw immediately into the current chart-area graphics, if one is available.
    protected final void drawGhost() {
        Chart currentChart = getChart();
        if (currentChart == null || !ghostDrawingAllowed)
            return;

        Rectangle ghostBounds = getGhostBounds();
        if (ghostBounds == null)
            return;

        Rectangle expandedBounds = new Rectangle(ghostBounds);
        expandedBounds.grow(1, 1);
        if (!isXORGhost()) {
            currentChart.getChartArea().repaint(expandedBounds);
            return;
        }

        Graphics g = currentChart.getChartArea().getGraphics();
        if (g == null)
            return;
        try {
            initGhostGraphics(g);
            g.setClip(expandedBounds);
            drawGhost(g);
        } finally {
            g.dispose();
        }
    }

    /// Paints the transient ghost representation of the current gesture.
    ///
    /// The base implementation does nothing. Subclasses overriding this hook may assume that
    /// [#initGhostGraphics(Graphics)] has already prepared the graphics context.
    ///
    /// @param g graphics context clipped to the chart plot region
    protected void drawGhost(Graphics g) {
    }

    /// Adds selected AWT event categories to this interactor's manager-visible interest mask.
    ///
    /// @param awtEventMask AWT event-category bits to enable
    protected final void enableEvents(long awtEventMask) {
        this.awtEventMask |= awtEventMask;
    }

    /// Marks the current gesture as completed normally.
    ///
    /// The base implementation clears the aborted flag and resets [#isInOperation()]. Subclasses
    /// typically extend this to commit edits or release transient UI state.
    ///
    /// @param event event that completed the operation
    protected void endOperation(MouseEvent event) {
        setAborted(false);
        setInOperation(false);
    }

    /// Dispatches one semantic interaction event to this interactor's listeners.
    ///
    /// @param event event to publish
    protected final void fireChartInteractionEvent(ChartInteractionEvent event) {
        if (chartInteractionListeners == null)
            return;

        Object[] listeners = chartInteractionListeners.getListenerList();
        for (int i = listeners.length - 1; i >= 0; i -= 2)
            ((ChartInteractionListener) listeners[i]).interactionPerformed(event);
    }

    /// Returns the enabled AWT event-category mask used by the interactor manager.
    public final long getAWTEventMask() {
        return awtEventMask;
    }

    /// Returns the chart that currently owns this interactor, if any.
    public final Chart getChart() {
        return chart;
    }

    /// Returns the coordinate system selected by [#getYAxisIndex()].
    public final CoordinateSystem getCoordinateSystem() {
        return chart.getCoordinateSystem(yAxisIndex);
    }

    /// Converts one display-space location into chart data coordinates.
    ///
    /// The conversion uses this interactor's current y-axis slot.
    ///
    /// @param x display-space x coordinate
    /// @param y display-space y coordinate
    /// @return converted data-space point
    public final DoublePoint getData(int x, int y) {
        DoublePoints points = new DoublePoints(x, y);
        try {
            getChart().toData(points, getYAxisIndex());
            return new DoublePoint(points.getX(0), points.getY(0));
        } finally {
            points.dispose();
        }
    }

    /// Converts the location carried by `event` into chart data coordinates.
    ///
    /// @param event mouse event providing the display-space location
    /// @return converted data-space point
    public final DoublePoint getData(MouseEvent event) {
        return getData(event.getX(), event.getY());
    }

    /// Returns the configured legacy modifier mask required to start this interactor's gesture.
    public final int getEventMask() {
        return eventMask;
    }

    /// Returns the extended modifier mask derived from [#getEventMask()].
    ///
    /// Subclasses commonly compare this value directly with [MouseEvent#getModifiersEx()].
    public final int getEventMaskEx() {
        return eventMaskEx;
    }

    /// Returns the region occupied by the current ghost overlay.
    ///
    /// Non-XOR ghosts use this rectangle to schedule repaints. The default implementation returns
    /// `null`, which suppresses [#drawGhost()].
    protected Rectangle getGhostBounds() {
        return null;
    }

    /// Returns the configured drawing color for ghost previews.
    ///
    /// The base XOR implementation uses this color as the active paint after switching into XOR
    /// mode. Non-XOR subclasses commonly reuse it as their default preview color as well.
    public Color getGhostColor() {
        return ghostColor;
    }

    /// Returns the priority used by [ChartInteractorManager] when ordering interactors.
    public final int getPriority() {
        return priority;
    }

    /// Returns the chart's shared x axis.
    public final Axis getXAxis() {
        return chart.getXAxis();
    }

    /// Returns the XOR comparison color used when [#isXORGhost()] is enabled.
    public final Color getXORColor() {
        return xorColor;
    }

    /// Returns the y axis currently targeted by this interactor.
    public final Axis getYAxis() {
        return chart.getYAxis(yAxisIndex);
    }

    /// Returns the y-axis slot used for coordinate conversion and axis selection.
    public final int getYAxisIndex() {
        return yAxisIndex;
    }

    /// Repaints the current ghost during chart expose handling.
    ///
    /// [ChartInteractorManager] forwards expose repaints to the interactor that currently owns the
    /// active gesture. The base implementation clips to the live plot rectangle before calling
    /// [#drawGhost(Graphics)].
    ///
    /// @param g chart-area graphics context
    public void handleExpose(Graphics g) {
        if (getChart() == null || !ghostDrawingAllowed)
            return;

        Graphics ghostGraphics = g.create();
        try {
            initGhostGraphics(ghostGraphics);
            Rectangle plotBounds = getChart().getChartArea().getPlotRect();
            ghostGraphics.clipRect(plotBounds.x, plotBounds.y, plotBounds.width, plotBounds.height);
            drawGhost(ghostGraphics);
        } finally {
            ghostGraphics.dispose();
        }
    }

    /// Prepares the graphics context that will be used for ghost painting.
    ///
    /// The default implementation configures XOR mode when [#isXORGhost()] is enabled and leaves
    /// non-XOR graphics unchanged.
    ///
    /// @param g graphics context about to be used for ghost painting
    protected void initGhostGraphics(Graphics g) {
        if (isXORGhost()) {
            g.setXORMode(getXORColor());
            g.setColor(getGhostColor());
        }
    }

    /// Notifies this interactor that a peer interactor has started a gesture.
    ///
    /// [ChartInteractorManager] broadcasts this callback to the other ordinary interactors attached
    /// to the same chart when one interactor enters operation state. The default implementation does
    /// nothing.
    ///
    /// @param interactor peer interactor that just entered an operation
    /// @param event mouse event that started that operation
    public void interactionStarted(ChartInteractor interactor, MouseEvent event) {
    }

    /// Returns whether the most recent gesture ended through [#abort()] rather than completion.
    public final boolean isAborted() {
        return aborted;
    }

    /// Returns whether ghost painting is currently enabled for this interactor.
    protected final boolean isGhostDrawingAllowed() {
        return ghostDrawingAllowed;
    }

    /// Returns whether handled input events should be consumed.
    public boolean isConsumeEvents() {
        return consumeEvents;
    }

    /// Returns whether this interactor is interested in the supplied display-space location.
    ///
    /// The default implementation accepts any point inside the currently projected visible window
    /// for this interactor's coordinate system. Subclasses commonly narrow that region to scales,
    /// handles, overlays, or renderer-specific affordances.
    ///
    /// @param x display-space x coordinate
    /// @param y display-space y coordinate
    /// @return `true` when the location should be offered to this interactor
    public boolean isHandling(int x, int y) {
        return getChart().getProjector()
                .getShape(getCoordinateSystem().getVisibleWindow(), getChart().getProjectorRect(),
                        getCoordinateSystem())
                .contains(x, y);
    }

    /// Returns whether this interactor currently owns an active gesture operation.
    public final boolean isInOperation() {
        return inOperation;
    }

    /// Returns whether ghost painting should use XOR compositing.
    public final boolean isXORGhost() {
        return xorGhost;
    }

    /// Processes a key event routed to this interactor.
    ///
    /// The base implementation does nothing.
    ///
    /// @param event key event to inspect
    public void processKeyEvent(KeyEvent event) {
    }

    /// Processes a mouse-button event routed to this interactor.
    ///
    /// The base implementation does nothing.
    ///
    /// @param event mouse event to inspect
    public void processMouseEvent(MouseEvent event) {
    }

    /// Processes a mouse-motion event routed to this interactor.
    ///
    /// The base implementation does nothing.
    ///
    /// @param event mouse event to inspect
    public void processMouseMotionEvent(MouseEvent event) {
    }

    /// Unregisters a previously added interaction listener.
    ///
    /// @param listener listener to remove
    public final void removeChartInteractionListener(ChartInteractionListener listener) {
        if (chartInteractionListeners == null)
            return;

        chartInteractionListeners.remove(ChartInteractionListener.class, listener);
        if (chartInteractionListeners.getListenerList().length == 0)
            chartInteractionListeners = null;
    }

    /// Updates the aborted flag maintained by the base lifecycle.
    ///
    /// @param aborted new aborted-state value
    protected void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    /// Enables or disables ghost painting support for the current gesture.
    ///
    /// Subclasses usually toggle this in tandem with their own operation lifecycle.
    ///
    /// @param ghostDrawingAllowed `true` to allow [#drawGhost()] and [#handleExpose(Graphics)]
    protected void setGhostDrawingAllowed(boolean ghostDrawingAllowed) {
        this.ghostDrawingAllowed = ghostDrawingAllowed;
    }

    /// Sets the owning chart for this interactor.
    ///
    /// This method is normally managed by [ChartInteractorManager]. Direct callers must preserve
    /// the one-chart-at-a-time ownership rule.
    ///
    /// @param chart new owner, or `null` to detach this interactor
    public final void setChart(Chart chart) {
        Chart previousChart = this.chart;
        if (chart == previousChart)
            return;

        this.chart = chart;
        chartConnected(previousChart, chart);
    }

    /// Controls whether handled events should be consumed.
    ///
    /// @param consumeEvents `true` to call `consume()` on handled events
    public void setConsumeEvents(boolean consumeEvents) {
        this.consumeEvents = consumeEvents;
    }

    /// Pushes or pops this interactor's cursor on the owning chart.
    ///
    /// Passing `null` restores the cursor saved by the manager's cursor stack.
    ///
    /// @param cursor cursor to install, or `null` to restore the previous cursor
    /// @return the cursor that was active before the change
    protected Cursor setCursor(Cursor cursor) {
        return (cursor == null)
                ? ChartInteractorManager.popCursor(chart)
                : ChartInteractorManager.pushCursor(chart, cursor);
    }

    /// Sets the legacy modifier mask required to start this interactor's gesture.
    ///
    /// The corresponding extended mask exposed by [#getEventMaskEx()] is recomputed immediately.
    /// This does not affect the AWT event categories enabled through [#enableEvents(long)].
    ///
    /// @param eventMask required modifier mask
    public void setEventMask(int eventMask) {
        this.eventMask = eventMask;
        eventMaskEx = EventUtil.convertModifiersMask(eventMask);
    }

    /// Sets the preferred drawing color for ghost previews.
    ///
    /// @param ghostColor preferred ghost color
    public void setGhostColor(Color ghostColor) {
        this.ghostColor = ghostColor;
    }

    /// Updates the in-operation flag maintained by the base lifecycle.
    ///
    /// @param inOperation new operation-state value
    protected void setInOperation(boolean inOperation) {
        this.inOperation = inOperation;
    }

    /// Sets the priority used when the manager orders interactors.
    ///
    /// The reserved values [#INTERNAL_HIGH] and [#INTERNAL_LOW] place an interactor in the
    /// manager's internal-priority lanes.
    ///
    /// @param priority new priority value
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /// Sets the XOR comparison color used during XOR ghost painting.
    ///
    /// @param xorColor XOR comparison color
    public void setXORColor(Color xorColor) {
        this.xorColor = xorColor;
    }

    /// Switches ghost painting between normal repaint mode and XOR mode.
    ///
    /// @param xorGhost `true` to use XOR painting, `false` to repaint through the normal paint path
    public void setXORGhost(boolean xorGhost) {
        this.xorGhost = xorGhost;
    }

    /// Selects the y-axis slot this interactor should target.
    ///
    /// @param yAxisIndex y-axis slot index
    public void setYAxisIndex(int yAxisIndex) {
        this.yAxisIndex = yAxisIndex;
    }

    /// Marks the current gesture as active and notifies peer interactors on the same chart.
    ///
    /// Subclasses normally call this once the opening mouse event has committed to the gesture.
    ///
    /// @param event event that started the operation
    protected void startOperation(MouseEvent event) {
        setAborted(false);
        setInOperation(true);
        getChart().getInteractorManager().interactionStarted(this, event);
    }

    /// Converts one data-space point into display coordinates using the selected y axis.
    ///
    /// @param point data-space point to convert
    /// @return converted display-space point
    public final DoublePoint toDisplay(DoublePoint point) {
        DoublePoints points = new DoublePoints(point.x, point.y);
        try {
            getChart().toDisplay(points, getYAxisIndex());
            return new DoublePoint(points.getX(0), points.getY(0));
        } finally {
            points.dispose();
        }
    }
}
