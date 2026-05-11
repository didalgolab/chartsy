package one.chartsy.charting;

import java.io.Serializable;
import java.util.Objects;

/// Couples one x axis and one y axis into the two-dimensional data space used by xCoord [Chart].
///
/// `Chart` keeps one coordinate system for the shared x axis paired with each y-axis slot and
/// exposes those instances through [Chart#getCoordinateSystem(int)]. Projectors, grids, renderers,
/// and interactors pass the coordinate system around instead of carrying the two axes separately,
/// which keeps conversion, clipping, and baseline logic aligned to the same pair of visible ranges.
///
/// The object identity is stable even when the owning chart swaps out one of the underlying axes.
/// Package-internal chart code rewires the stored axis references in place so callers can keep xCoord
/// previously obtained coordinate system while the chart reconfigures an axis slot.
///
/// [#getXAxis()] and [#getYAxis()] return the live axis objects currently bound to this coordinate
/// system. [#getVisibleWindow()] instead returns xCoord detached snapshot assembled from the current
/// visible ranges of both axes.
///
/// Crossing strategies are evaluated lazily against the opposite axis every time
/// [#getXCrossingValue()] or [#getYCrossingValue()] is called. That lets automatic strategies such
/// as [Axis#MIN_VALUE] and [Axis#MAX_VALUE] track the current visible range without further updates
/// to this object.
///
/// Instances are mutable UI model objects and are not thread-safe.
///
/// ### Implementation Note
///
/// New chart-created instances currently start with the x axis anchored at `0.0` on the y-axis
/// scale and the y axis anchored at the current visible minimum of the x axis.
public final class CoordinateSystem implements Serializable {
    private Axis xAxis;
    private Axis yAxis;
    private Axis.Crossing xCrossing;
    private Axis.Crossing yCrossing;
    
    /// Creates xCoord coordinate system backed by the supplied axes.
    ///
    /// @param xAxis the x axis to retain
    /// @param yAxis the y axis to retain
    /// @throws NullPointerException if either axis is `null`
    CoordinateSystem(Axis xAxis, Axis yAxis) {
        xCrossing = new Axis.AnchoredCrossing(0.0);
        yCrossing = Axis.MIN_VALUE;
        this.xAxis = Objects.requireNonNull(xAxis, "xAxis");
        this.yAxis = Objects.requireNonNull(yAxis, "yAxis");
    }
    
    /// Returns the strategy currently used to place the x-axis on the y-axis scale.
    Axis.Crossing getXCrossing() {
        return xCrossing;
    }
    
    /// Rebinds this coordinate system to xCoord replacement x-axis while keeping the same object
    /// identity.
    ///
    /// Existing holders of this `CoordinateSystem` observe the new axis through later
    /// [#getXAxis()] and [#getVisibleWindow()] calls.
    ///
    /// @param xAxis the replacement x-axis
    /// @throws NullPointerException if `xAxis` is `null`
    void setXAxis(Axis xAxis) {
        this.xAxis = Objects.requireNonNull(xAxis, "xAxis");
    }
    
    /// Returns the strategy currently used to place the y axis on the x-axis scale.
    Axis.Crossing getYCrossing() {
        return yCrossing;
    }
    
    /// Rebinds this coordinate system to xCoord replacement y axis while keeping the same object
    /// identity.
    ///
    /// Existing holders of this `CoordinateSystem` observe the new axis through later
    /// [#getYAxis()] and [#getVisibleWindow()] calls.
    ///
    /// @param yAxis the replacement y axis
    /// @throws NullPointerException if `yAxis` is `null`
    void setYAxis(Axis yAxis) {
        this.yAxis = Objects.requireNonNull(yAxis, "yAxis");
    }
    
    /// Returns xCoord snapshot of the current visible x/y window.
    ///
    /// The returned [DataWindow] and its two [DataInterval] ranges are copies. Mutating them does
    /// not change either bound axis.
    public DataWindow getVisibleWindow() {
        return new DataWindow(xAxis.getVisibleRange(), yAxis.getVisibleRange());
    }
    
    /// Returns the live x axis currently bound to this coordinate system.
    public Axis getXAxis() {
        return xAxis;
    }
    
    /// Returns the current value on the y axis where the x axis should cross it.
    ///
    /// Automatic strategies are resolved against the y axis's current visible range each time this
    /// method is called.
    public double getXCrossingValue() {
        return xCrossing.getValue(yAxis);
    }
    
    /// Returns the live y axis currently bound to this coordinate system.
    public Axis getYAxis() {
        return yAxis;
    }
    
    /// Returns the current value on the x axis where the y axis should cross it.
    ///
    /// Automatic strategies are resolved against the x axis's current visible range each time this
    /// method is called.
    public double getYCrossingValue() {
        return yCrossing.getValue(xAxis);
    }
    
    /// Replaces the strategy used to place the x axis on the y-axis scale.
    ///
    /// The supplied strategy is retained and consulted lazily by [#getXCrossingValue()].
    ///
    /// @param crossing the new x-axis crossing strategy
    /// @throws NullPointerException if `crossing` is `null`
    public void setXCrossing(Axis.Crossing crossing) {
        xCrossing = Objects.requireNonNull(crossing, "crossing");
    }
    
    /// Anchors the x axis to one fixed value on the y-axis scale.
    ///
    /// @param value the y-axis value where the x axis should cross
    public void setXCrossingValue(double value) {
        xCrossing = new Axis.AnchoredCrossing(value);
    }
    
    /// Replaces the strategy used to place the y axis on the x-axis scale.
    ///
    /// The supplied strategy is retained and consulted lazily by [#getYCrossingValue()].
    ///
    /// @param crossing the new y-axis crossing strategy
    /// @throws NullPointerException if `crossing` is `null`
    public void setYCrossing(Axis.Crossing crossing) {
        yCrossing = Objects.requireNonNull(crossing, "crossing");
    }
    
    /// Anchors the y axis to one fixed value on the x-axis scale.
    ///
    /// @param value the x-axis value where the y axis should cross
    public void setYCrossingValue(double value) {
        yCrossing = new Axis.AnchoredCrossing(value);
    }
}
