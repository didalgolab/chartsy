package one.chartsy.charting.interactors;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.imageio.ImageIO;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.SingleChartRenderer;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.swing.CursorRegistry;

/// Edits one renderer-backed [DisplayPoint] by dragging it inside the chart area.
///
/// The interactor inherits axis-aware picking from [ChartDataInteractor] and then narrows the
/// picker to renderers whose currently exposed dataset view is editable. Once a point is selected,
/// edits are written back through [DisplayPoint#setData(double, double)] so renderer-specific
/// virtual-dataset mappings still participate in the reverse projection.
///
/// When [#isOpaqueEdit()] is `false`, dragging does not mutate the dataset immediately. Instead the
/// interactor paints a ghost square for the tentative point location and connects it to the
/// adjacent display points returned by the owning renderer. Radar charts wrap those connections
/// across the first and last point so the preview matches the closed shape seen on screen.
///
/// [#isXEditAllowed()] and [#isYEditAllowed()] act as independent axis locks. After those locks are
/// applied, subclasses can further constrain the candidate coordinates in
/// [#validate(DoublePoints, DisplayPoint)].
public class ChartEditPointInteractor extends ChartDataInteractor {
    private static final int HANDLE_RADIUS = 3;
    private static final int CURSOR_SIZE_SMALL = 32;
    private static final int CURSOR_SIZE_LARGE = 64;

    private static Cursor editPointCursor;

    static {
        ChartInteractor.register("EditPoint", ChartEditPointInteractor.class);
    }

    private boolean opaqueEdit;
    private boolean xEditAllowed;
    private boolean yEditAllowed;
    private transient DisplayPoint editPoint;
    private transient boolean hoverCursorActive;

    /// Creates an interactor for the primary y-axis slot with primary-button drag gestures.
    ///
    /// The default configuration allows only y-axis changes and uses ghost previews instead of
    /// immediate commits.
    public ChartEditPointInteractor() {
        this(16, false);
    }

    /// Creates an interactor for the primary y-axis slot.
    ///
    /// @param eventMask  legacy mouse modifier mask required to start editing
    /// @param opaqueEdit `true` to commit edits during the drag, `false` to preview them first
    public ChartEditPointInteractor(int eventMask, boolean opaqueEdit) {
        this(0, eventMask, opaqueEdit);
    }

    /// Creates an interactor for one y-axis slot and modifier combination.
    ///
    /// X edits are disabled by default, y edits are enabled by default, and ghost painting uses
    /// normal repainting rather than XOR mode.
    ///
    /// @param yAxisIndex y-axis slot whose renderers should participate in editing
    /// @param eventMask  legacy mouse modifier mask required to start editing
    /// @param opaqueEdit `true` to commit edits during the drag, `false` to preview them first
    public ChartEditPointInteractor(int yAxisIndex, int eventMask, boolean opaqueEdit) {
        super(yAxisIndex, eventMask);
        xEditAllowed = false;
        yEditAllowed = true;
        clearInteractionState();
        this.opaqueEdit = opaqueEdit;
        enableEvents(56L);
        setXORGhost(false);
        initializeEditPointCursor();
    }

    /// Creates a custom cursor when the platform accepts the supplied image and hot spot.
    ///
    /// Successful custom cursors are also registered in [CursorRegistry]. When the platform cannot
    /// create the cursor, the predefined fallback cursor is returned instead.
    ///
    /// @param image              image to use for the custom cursor, or `null` to force fallback
    /// @param hotSpot            cursor hot spot within `image`
    /// @param name               registry name for the cursor
    /// @param fallbackCursorType predefined cursor type to use on failure
    /// @return the custom cursor when creation succeeds, otherwise the fallback cursor
    public static Cursor createCursor(Image image, Point hotSpot, String name, int fallbackCursorType) {
        Cursor cursor = null;
        if (image != null) {
            try {
                cursor = Toolkit.getDefaultToolkit().createCustomCursor(image, hotSpot, name);
            } catch (IndexOutOfBoundsException ignored) {
            }
            if (cursor != null)
                CursorRegistry.registerCustomCursor(name, image, hotSpot);
        }
        return (cursor != null)
                ? cursor
                : Cursor.getPredefinedCursor(fallbackCursorType);
    }

    private void clearHoverCursor() {
        if (hoverCursorActive) {
            setCursor(null);
            hoverCursorActive = false;
        }
    }

    private void clearInteractionState() {
        clearHoverCursor();
        editPoint = null;
    }

    private void constrainEditedPoint(DoublePoints points, DisplayPoint editPoint) {
        if (!xEditAllowed)
            points.setX(0, editPoint.getXData());
        if (!yEditAllowed)
            points.setY(0, editPoint.getYData());
        validate(points, editPoint);
    }

    private DisplayPoint[] getAdjacentDisplayPoints() {
        int index = editPoint.getIndex();
        DataSet dataSet = editPoint.getDataSet();
        ChartRenderer renderer = editPoint.getRenderer();
        int lastIndex = dataSet.size() - 1;
        boolean wrapAround = getChart().getType() == Chart.RADAR;
        boolean hasPrevious = index > 0 || wrapAround;
        boolean hasNext = index < lastIndex || wrapAround;
        DisplayPoint[] adjacent = new DisplayPoint[(hasPrevious ? 1 : 0) + (hasNext ? 1 : 0)];
        int nextSlot = 0;

        if (hasPrevious)
            adjacent[nextSlot++] = renderer.getDisplayPoint(dataSet, (index > 0) ? index - 1 : lastIndex);
        if (hasNext)
            adjacent[nextSlot] = renderer.getDisplayPoint(dataSet, (index < lastIndex) ? index + 1 : 0);
        return adjacent;
    }

    private boolean hasMatchingModifiers(MouseEvent event) {
        return (event.getModifiersEx() & getEventMaskEx()) == getEventMaskEx()
                && (event.getModifiersEx() & ~getEventMaskEx()) == 0;
    }

    private void initializeEditPointCursor() {
        if (editPointCursor != null)
            return;

        try {
            Dimension bestCursorSize = Toolkit.getDefaultToolkit().getBestCursorSize(
                    CURSOR_SIZE_SMALL,
                    CURSOR_SIZE_SMALL);
            int width = bestCursorSize.width;
            int height = bestCursorSize.height;
            if (!((width == CURSOR_SIZE_SMALL && height == CURSOR_SIZE_SMALL)
                    || (width == CURSOR_SIZE_LARGE && height == CURSOR_SIZE_LARGE))) {
                editPointCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                return;
            }

            String resourceName = "movept_" + width + ".gif";
            var resource = ChartEditPointInteractor.class.getResource(resourceName);
            if (resource == null)
                throw new IllegalStateException("Missing cursor resource: " + resourceName);
            Image image = ImageIO.read(resource);
            editPointCursor = createCursor(image, new Point(8, 8), "MoveCursor", Cursor.MOVE_CURSOR);
        } catch (Exception ignored) {
            editPointCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
    }

    private boolean isDefined(DisplayPoint point) {
        if (point == null)
            return false;

        Double undefValue = point.getDataSet().getUndefValue();
        double y = point.getYData();
        return (undefValue == null || Double.compare(y, undefValue.doubleValue()) != 0) && !Double.isNaN(y);
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!isInOperation())
            return;

        DoublePoints points = new DoublePoints(event.getX(), event.getY());
        try {
            editPoint.getRenderer().toData(points);
            constrainEditedPoint(points, editPoint);
            if (opaqueEdit) {
                editPoint.setData(points.getX(0), points.getY(0));
            } else {
                drawGhost();
                editPoint.getRenderer().toDisplay(points);
                editPoint.set(editPoint.getIndex(), points.getX(0), points.getY(0));
                drawGhost();
            }
        } finally {
            points.dispose();
        }

        if (isConsumeEvents())
            event.consume();
    }

    private void handleMousePressed(MouseEvent event) {
        if (!hasMatchingModifiers(event))
            return;

        editPoint = pickData(event);
        if (editPoint == null) {
            setGhostDrawingAllowed(false);
            return;
        }
        if (!editPoint.isEditable())
            return;

        startOperation(event);
        if (!opaqueEdit)
            drawGhost();
        if (isConsumeEvents())
            event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!isInOperation())
            return;
        if ((event.getModifiersEx() & getEventMaskEx()) == getEventMaskEx())
            return;

        DoublePoints points = new DoublePoints(event.getX(), event.getY());
        try {
            editPoint.getRenderer().toData(points);
            constrainEditedPoint(points, editPoint);
            if (!opaqueEdit) {
                drawGhost();
                editPoint.setData(points.getX(0), points.getY(0));
            }
        } finally {
            points.dispose();
        }

        endOperation(event);
        if (isConsumeEvents())
            event.consume();
    }

    private PlotStyle resolveGhostStyle() {
        PlotStyle style = (!isXORGhost() && editPoint != null)
                ? editPoint.getRenderer().getStyle(0)
                : null;
        return (style != null)
                ? style
                : new PlotStyle(getGhostColor(), getGhostColor());
    }

    /// Creates the picker used while locating a point to edit.
    ///
    /// The picker keeps the base class's y-axis filtering and fixed pick radius, then further
    /// rejects single renderers whose visible dataset view is not editable.
    @Override
    protected ChartDataPicker createDataPicker(MouseEvent event) {
        return new DataPicker(event.getX(), event.getY(), DEFAULT_PICK_DISTANCE) {
            @Override
            public boolean accept(ChartRenderer renderer) {
                if (!super.accept(renderer))
                    return false;
                if (!(renderer instanceof SingleChartRenderer))
                    return true;

                DataSet dataSet = renderer.getDataSource().get(0);
                return renderer.getVirtualDataSet(dataSet).isEditable();
            }
        };
    }

    @Override
    protected void abort() {
        super.abort();
        setGhostDrawingAllowed(false);
        setCursor(null);
        editPoint = null;
        clearHoverCursor();
    }

    @Override
    protected void drawGhost(Graphics g) {
        assert isGhostDrawingAllowed();
        assert editPoint != null;

        PlotStyle style = resolveGhostStyle();
        for (DisplayPoint adjacent : getAdjacentDisplayPoints()) {
            if (isDefined(adjacent))
                style.drawLine(g, adjacent.getXCoord(), adjacent.getYCoord(), editPoint.getXCoord(), editPoint.getYCoord());
        }
        style.plotSquare(g, GraphicUtil.toInt(editPoint.getXCoord()), GraphicUtil.toInt(editPoint.getYCoord()), HANDLE_RADIUS);
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        setGhostDrawingAllowed(false);
        setCursor(null);
        editPoint = null;
        clearHoverCursor();
    }

    /// Returns the cursor shown while a point is editable or actively being dragged.
    protected Cursor getCursor() {
        return editPointCursor;
    }

    /// Returns the point currently being edited, if any.
    ///
    /// The returned handle is a clone of the current in-memory edit state. In ghost-preview mode
    /// that state may differ from the dataset until the drag is committed.
    ///
    /// @return cloned edit-point state, or `null` when no edit is active
    public DisplayPoint getEditPoint() {
        return (editPoint == null) ? null : editPoint.clone();
    }

    @Override
    protected Rectangle getGhostBounds() {
        Rectangle bounds = new Rectangle(
                GraphicUtil.toInt(editPoint.getXCoord()) - HANDLE_RADIUS,
                GraphicUtil.toInt(editPoint.getYCoord()) - HANDLE_RADIUS,
                HANDLE_RADIUS * 2 + 1,
                HANDLE_RADIUS * 2 + 1);
        for (DisplayPoint adjacent : getAdjacentDisplayPoints()) {
            if (isDefined(adjacent))
                GraphicUtil.addToRect(bounds, adjacent.getXCoord(), adjacent.getYCoord());
        }
        resolveGhostStyle().expand(true, bounds);
        return bounds;
    }

    @Override
    public void interactionStarted(ChartInteractor interactor, MouseEvent event) {
        clearHoverCursor();
    }

    @Override
    public boolean isHandling(int x, int y) {
        return true;
    }

    /// Returns whether dragged coordinates are committed during the drag itself.
    ///
    /// A `false` result means the interactor keeps the change in ghost-preview state until mouse
    /// release.
    public final boolean isOpaqueEdit() {
        return opaqueEdit;
    }

    /// Returns whether dragging may change the point's x value.
    public final boolean isXEditAllowed() {
        return xEditAllowed;
    }

    /// Returns whether dragging may change the point's y value.
    public final boolean isYEditAllowed() {
        return yEditAllowed;
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (!opaqueEdit && editPoint != null)
                drawGhost();
            abort();
            if (isConsumeEvents())
                event.consume();
        }
    }

    @Override
    public void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED -> handleMousePressed(event);
            case MouseEvent.MOUSE_RELEASED -> handleMouseReleased(event);
            case MouseEvent.MOUSE_ENTERED -> {
                if (event.getButton() == MouseEvent.NOBUTTON)
                    processMouseMovedEvent(event);
            }
            default -> {
            }
        }
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_MOVED -> processMouseMovedEvent(event);
            case MouseEvent.MOUSE_DRAGGED -> handleMouseDragged(event);
            default -> {
            }
        }
    }

    /// Updates the hover cursor state for the current mouse location.
    ///
    /// The cursor is shown only when the current pick resolves to an editable point.
    ///
    /// @param event mouse event providing the current hover location
    public void processMouseMovedEvent(MouseEvent event) {
        DisplayPoint point = pickData(event);
        boolean shouldShowCursor = point != null && point.isEditable();
        if (shouldShowCursor) {
            if (!hoverCursorActive) {
                setCursor(getCursor());
                hoverCursorActive = true;
            }
        } else {
            clearHoverCursor();
        }
    }

    /// Switches between immediate commits and ghost-preview editing.
    ///
    /// @param opaqueEdit `true` to commit during drag, `false` to wait until release
    public void setOpaqueEdit(boolean opaqueEdit) {
        this.opaqueEdit = opaqueEdit;
    }

    /// Enables or disables x-axis movement while dragging.
    ///
    /// @param xEditAllowed `true` to allow x changes, `false` to keep the original x value
    public void setXEditAllowed(boolean xEditAllowed) {
        this.xEditAllowed = xEditAllowed;
    }

    /// Enables or disables y-axis movement while dragging.
    ///
    /// @param yEditAllowed `true` to allow y changes, `false` to keep the original y value
    public void setYEditAllowed(boolean yEditAllowed) {
        this.yEditAllowed = yEditAllowed;
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        assert editPoint != null;
        if (!opaqueEdit)
            setGhostDrawingAllowed(true);
        setCursor(getCursor());
    }

    /// Validates or clamps the candidate data-space coordinates before they are previewed or
    /// committed.
    ///
    /// The default implementation accepts the coordinates unchanged after the x/y axis locks have
    /// already been applied.
    ///
    /// @param points    mutable one-point buffer containing the candidate coordinates
    /// @param editPoint display point currently being edited
    protected void validate(DoublePoints points, DisplayPoint editPoint) {
    }
}
