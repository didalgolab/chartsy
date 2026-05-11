package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.data.DataSet;

/// Composite pie/donut renderer that assigns one [SinglePieRenderer] child to each dataset.
///
/// The composite interprets the radial dimension as a shared `0..100` interval. [#getHoleSize()]
/// reserves the inner part of that interval for the donut hole, and each visible child receives an
/// equal slice of the remaining radial span the next time child layout is recomputed.
///
/// The renderer forces its y-range to start at `0.0` so the center remains part of the projected
/// radial space even when every visible child starts at a positive inner radius.
public class PieChartRenderer extends SimpleCompositeChartRenderer {
    static {
        ChartRenderer.register("Pie", PieChartRenderer.class);
    }

    private int holeSize;
    private boolean strokeOn;

    /// Creates a pie renderer with no hole and child slice outlines enabled.
    public PieChartRenderer() {
        holeSize = 0;
        strokeOn = true;
    }

    /// Creates the single-dataset pie child for `dataSet`.
    ///
    /// New children inherit the composite stroke flag.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SinglePieRenderer child = new SinglePieRenderer();
        child.setStrokeOn(strokeOn);
        return child;
    }

    /// Returns the percentage of the full radial span reserved for the donut hole.
    ///
    /// `0` produces a full pie. `100` leaves no radial thickness for child rings.
    public final int getHoleSize() {
        return holeSize;
    }

    /// Returns the child renderer currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public SinglePieRenderer getPie(DataSet dataSet) {
        return (SinglePieRenderer) super.getChild(dataSet);
    }

    /// Returns the composite radial range while forcing the lower bound to the center of the pie.
    @Override
    public DataInterval getYRange(DataInterval range) {
        DataInterval result = super.getYRange(range);
        result.setMin(0.0);
        return result;
    }

    /// Redistributes the remaining radial span between visible child rings.
    ///
    /// The shared `0..100` radial interval is trimmed by [#getHoleSize()] and then split evenly
    /// between visible children. Each participating child receives a translated copy of the
    /// resulting radial subrange.
    private void updateChildRadialRanges() {
        DataInterval childRange = new DataInterval(0.0, 100.0);
        childRange.setMin(childRange.getMin() + childRange.getLength() * holeSize / 100.0);

        int visibleChildCount = 0;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            ChartRenderer child = super.getChild(childIndex);
            if (child.isVisible())
                visibleChildCount++;
        }

        if (visibleChildCount > 1)
            childRange.setMax(childRange.getMin() + childRange.getLength() / visibleChildCount);

        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            ChartRenderer child = super.getChild(childIndex);
            if (child.isVisible() && child instanceof SinglePieRenderer pieChild) {
                pieChild.setRadialRange(childRange);
                childRange.translate(childRange.getLength());
            }
        }
    }

    /// Returns whether child pie slices currently draw their outline strokes.
    public final boolean isStrokeOn() {
        return strokeOn;
    }

    /// Updates the donut-hole percentage and recomputes child radial ranges.
    ///
    /// @param holeSize percentage of the full radial interval reserved for the center hole
    /// @throws IllegalArgumentException if `holeSize` is outside the inclusive range `0..100`
    public void setHoleSize(int holeSize) {
        if (holeSize >= 0 && holeSize <= 100) {
            if (holeSize != this.holeSize) {
                this.holeSize = holeSize;
                updateChildRadialRanges();
                super.triggerChange(4);
            }
            return;
        }
        throw new IllegalArgumentException("Hole size must be between 0 and 100");
    }

    /// Propagates the slice-stroke flag to every current and future child pie renderer.
    public void setStrokeOn(boolean strokeOn) {
        if (strokeOn != this.strokeOn) {
            this.strokeOn = strokeOn;
            for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
                SinglePieRenderer child = (SinglePieRenderer) super.getChild(childIndex);
                child.setStrokeOn(strokeOn);
            }
        }
    }

    /// Reapplies concentric ring allocation after the child set changes.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        updateChildRadialRanges();
    }
}
