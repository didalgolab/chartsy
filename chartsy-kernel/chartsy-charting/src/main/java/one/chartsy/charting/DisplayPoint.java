package one.chartsy.charting;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;

/// Couples one logical `DataSet` entry with the display-space coordinates produced by a specific
/// [ChartRenderer].
///
/// Renderers use this type as the shared transport object for hit testing, annotations, and
/// interaction callbacks. Instances are mutable and are often reused in place while renderers scan
/// projected data. Code that needs a stable snapshot typically clones the handle before storing or
/// publishing it.
///
/// The inherited [#dataSet] and [#index] identify the logical source point. [#getXCoord()] and
/// [#getYCoord()] track the current display-space location for that source point, which may differ
/// from the raw dataset values after projection or virtual-dataset mapping.
public class DisplayPoint extends DataSetPoint {

    double xCoord;
    double yCoord;
    private ChartRenderer renderer;

    /// Creates a detached display handle for later reuse.
    public DisplayPoint() {
    }

    /// Creates a reusable display handle bound to `renderer` and `dataSet`.
    ///
    /// The logical index and display coordinates are initialized to `0` and are commonly filled in
    /// later through [#set(int, double, double)].
    ///
    /// @param renderer the renderer that will own this handle's display-space view and edits
    /// @param dataSet the logical source dataset currently addressed by this handle
    public DisplayPoint(ChartRenderer renderer, DataSet dataSet) {
        this(renderer, dataSet, 0, 0.0, 0.0);
    }

    /// Creates a handle for one renderer-visible dataset point.
    ///
    /// @param renderer the renderer that produced this display-space view and will receive edits
    /// @param dataSet the logical source dataset currently addressed by this handle
    /// @param index the logical point index within `dataSet`
    /// @param xCoord the current display-space x coordinate
    /// @param yCoord the current display-space y coordinate
    public DisplayPoint(ChartRenderer renderer, DataSet dataSet, int index, double xCoord,
            double yCoord) {
        super(dataSet, index);
        this.renderer = renderer;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
    }

    /// Returns a shallow snapshot of this display handle.
    ///
    /// The clone retains the same dataset and renderer references while copying the current logical
    /// index and display coordinates.
    @Override
    public DisplayPoint clone() {
        return (DisplayPoint) super.clone();
    }

    /// Returns the renderer that produced this display-space view.
    ///
    /// Edit operations delegate back through this renderer so virtual datasets can translate the
    /// handle to the correct source dataset point.
    ///
    /// @return the renderer currently associated with this handle
    public final ChartRenderer getRenderer() {
        return renderer;
    }

    /// Returns the current display-space x coordinate for this point.
    ///
    /// @return the point's x position in display coordinates
    public final double getXCoord() {
        return xCoord;
    }

    /// Returns the current display-space y coordinate for this point.
    ///
    /// @return the point's y position in display coordinates
    public final double getYCoord() {
        return yCoord;
    }

    /// Returns whether edits can be written back through this handle.
    ///
    /// A point is editable only when both the addressed dataset and the renderer's current
    /// virtual-dataset view accept mutation. That allows derived datasets to reject edits even when
    /// the backing source dataset itself is editable.
    ///
    /// @return `true` when edits can be translated back to the current source dataset point
    public boolean isEditable() {
        if (!super.getDataSet().isEditable()) {
            return false;
        }

        DataSet virtualDataSet = getRenderer().getVirtualDataSet(super.getDataSet());
        if (virtualDataSet == super.getDataSet()) {
            return true;
        }
        return virtualDataSet.isEditable();
    }

    /// Repositions this handle without mutating the underlying dataset.
    ///
    /// Renderers use this to recycle one `DisplayPoint` instance while iterating over projected
    /// data. The dataset and renderer references stay unchanged.
    ///
    /// @param index the logical point index now addressed by this handle
    /// @param xCoord the new display-space x coordinate
    /// @param yCoord the new display-space y coordinate
    public void set(int index, double xCoord, double yCoord) {
        super.index = index;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
    }

    /// Writes new display-space coordinates back through the owning renderer.
    ///
    /// Non-editable points are ignored. The renderer receives display coordinates so it can reverse
    /// projector and virtual-dataset mappings before the source dataset is updated.
    ///
    /// @param xCoord the requested display-space x coordinate
    /// @param yCoord the requested display-space y coordinate
    public void setCoords(int xCoord, int yCoord) {
        if (!isEditable()) {
            return;
        }

        renderer.setDisplayPoint(super.dataSet, super.index, xCoord, yCoord);
        this.xCoord = xCoord;
        this.yCoord = yCoord;
    }

    /// Writes new data-space coordinates back through the owning renderer.
    ///
    /// This override avoids mutating [#dataSet] directly so renderers backed by virtual datasets can
    /// unmap the edit target before the source dataset receives the change.
    @Override
    public void setData(double x, double y) {
        renderer.setDataPoint(super.dataSet, super.index, x, y);
    }

    /// Returns whether `obj` refers to the same dataset point, renderer, and display coordinates.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof DisplayPoint point
                && super.equals(point)
                && point.renderer == renderer
                && point.xCoord == xCoord
                && point.yCoord == yCoord;
    }
}
