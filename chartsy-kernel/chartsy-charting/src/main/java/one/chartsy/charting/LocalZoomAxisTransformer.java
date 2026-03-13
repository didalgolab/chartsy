package one.chartsy.charting;

/// Applies a lens-style zoom to one source-space interval on an [Axis].
///
/// The transformer keeps [#getZoomRange()] in source coordinates and derives
/// [#getTransformedRange()] by expanding that interval around the same midpoint with
/// [#getZoomFactor()]. Values inside the zoom range are remapped linearly into the transformed
/// range. Values outside it are either compressed linearly so the overall mapping stays continuous
/// or collapsed onto the transformed-range edges, depending on [#isContinuous()].
///
/// Current chart interaction code uses this transformer as mutable local-zoom state: zoom gestures
/// adjust the factor, pan gestures move the zoom range, and reshape gestures drag its bounds.
/// Companion UI code listens to the `zoomFactor`, `zoomRange`, and `continuous` property changes
/// to keep overlays and annotations in sync with the current lens configuration.
///
/// [#validateInterval(DataInterval)] enlarges axis intervals to include the current zoom range, so
/// axis normalization does not clip the active lens out of view. Instances are mutable UI-state
/// objects and are not thread-safe.
public class LocalZoomAxisTransformer extends AxisTransformer {
    private final DataInterval zoomRange;
    private double zoomFactor;
    private boolean continuous;
    private final Axis axis;

    /// Creates a local-zoom transformer from an existing interval object.
    ///
    /// The supplied interval is copied immediately, so later mutations of `zoomRange` do not affect
    /// the created transformer.
    ///
    /// @param axis       the owning axis whose visible range clips [#getTransformedRange()], or
    ///                                     `null` for a detached transformer
    /// @param zoomRange  source-space interval that should receive the local zoom treatment
    /// @param zoomFactor multiplicative factor used to derive the transformed-range length
    /// @param continuous `true` to keep the outer segments continuous, `false` to collapse the
    ///                                     transition bands onto the zoom-range edges
    public static LocalZoomAxisTransformer create(Axis axis, DataInterval zoomRange, double zoomFactor,
                                                  boolean continuous) {
        return create(axis, zoomRange.min, zoomRange.max, zoomFactor, continuous);
    }

    /// Creates a local-zoom transformer from explicit source-space bounds.
    ///
    /// @param axis       the owning axis whose visible range clips [#getTransformedRange()], or
    ///                                     `null` for a detached transformer
    /// @param min        lower bound of the source-space zoom range
    /// @param max        upper bound of the source-space zoom range
    /// @param zoomFactor multiplicative factor used to derive the transformed-range length
    /// @param continuous `true` to keep the outer segments continuous, `false` to collapse the
    ///                                     transition bands onto the zoom-range edges
    public static LocalZoomAxisTransformer create(Axis axis, double min, double max, double zoomFactor,
                                                  boolean continuous) {
        return new LocalZoomAxisTransformer(axis, min, max, zoomFactor, continuous);
    }

    private LocalZoomAxisTransformer(Axis axis, double min, double max, double zoomFactor, boolean continuous) {
        zoomRange = new DataInterval(min, max);
        this.zoomFactor = zoomFactor;
        this.continuous = continuous;
        this.axis = axis;
    }

    /// Applies the local-zoom mapping to one source-space value.
    ///
    /// Values inside [#getZoomRange()] are scaled into [#getTransformedRange()]. Outside values are
    /// either compressed linearly or collapsed onto the transformed-range edges, depending on
    /// [#isContinuous()].
    @Override
    public double apply(double value) throws AxisTransformerException {
        if (isIdentityMapping()) {
            return value;
        }

        DataInterval transformedRange = getTransformedRange();
        double stretch = transformedRange.getLength() / zoomRange.getLength();
        if (zoomRange.isInside(value)) {
            return transformedRange.getMin() + stretch * (value - zoomRange.getMin());
        }
        if (!continuous) {
            return applyDiscontinuous(value, transformedRange);
        }
        return applyContinuous(value, transformedRange, axis.getVisibleRange());
    }

    /// Applies the local-zoom mapping to the first `count` array entries in place.
    ///
    /// This override precomputes the shared range geometry once so batch projection code can reuse
    /// the same lens parameters for every processed value.
    @Override
    public double[] apply(double[] values, int count) throws AxisTransformerException {
        if (isIdentityMapping()) {
            return values;
        }

        DataInterval transformedRange = getTransformedRange();
        double stretch = transformedRange.getLength() / zoomRange.getLength();
        if (!continuous) {
            for (int index = 0; index < count; index++) {
                double value = values[index];
                values[index] = zoomRange.isInside(value)
                        ? transformedRange.getMin() + stretch * (value - zoomRange.getMin())
                        : applyDiscontinuous(value, transformedRange);
            }
            return values;
        }

        DataInterval visibleRange = axis.getVisibleRange();
        double leftScale = (zoomRange.getMin() == visibleRange.getMin())
                ? 0.0
                : (transformedRange.getMin() - visibleRange.getMin()) / (zoomRange.getMin() - visibleRange.getMin());
        double rightScale = (visibleRange.getMax() == zoomRange.getMax())
                ? 0.0
                : (visibleRange.getMax() - transformedRange.getMax()) / (visibleRange.getMax() - zoomRange.getMax());
        for (int index = 0; index < count; index++) {
            double value = values[index];
            if (zoomRange.isInside(value)) {
                values[index] = transformedRange.getMin() + stretch * (value - zoomRange.getMin());
            } else if (zoomRange.isBefore(value)) {
                values[index] = visibleRange.getMin() + (value - visibleRange.getMin()) * leftScale;
            } else if (zoomRange.isAfter(value)) {
                values[index] = transformedRange.getMax() + (value - zoomRange.getMax()) * rightScale;
            }
        }
        return values;
    }

    /// Returns the axis that owns this transformer, or `null` when the transformer is detached.
    public Axis getAxis() {
        return axis;
    }

    /// Returns the current source-space interval that the zoom range expands into.
    ///
    /// The transformed range shares the midpoint of [#getZoomRange()] and has length
    /// `getZoomFactor() * getZoomRange().getLength()` before clipping. When an owning axis is
    /// present, the result is intersected with that axis's current visible range.
    public DataInterval getTransformedRange() {
        double midpoint = zoomRange.getMiddle();
        double halfLength = zoomFactor * zoomRange.getLength() / 2.0;
        DataInterval transformedRange = new DataInterval(midpoint - halfLength, midpoint + halfLength);
        if (axis != null) {
            transformedRange.intersection(axis.getVisibleRange());
        }
        return transformedRange;
    }

    /// Returns the current multiplicative factor used to derive [#getTransformedRange()].
    public final double getZoomFactor() {
        return zoomFactor;
    }

    /// Returns a snapshot of the current source-space zoom interval.
    ///
    /// The returned [DataInterval] is a copy, so mutating it does not update this transformer.
    public final DataInterval getZoomRange() {
        return new DataInterval(zoomRange);
    }

    /// Inverts one transformed-space value back into the source coordinate system.
    ///
    /// In discontinuous mode only the exact transformed-range edges invert back to the
    /// corresponding zoom-range edges; other outside values remain unchanged because the forward
    /// mapping collapsed multiple source values onto those boundaries.
    @Override
    public double inverse(double value) throws AxisTransformerException {
        if (isIdentityMapping()) {
            return value;
        }

        DataInterval transformedRange = getTransformedRange();
        DataInterval visibleRange = axis.getVisibleRange();
        if (transformedRange.isInside(value)) {
            double stretch = transformedRange.getLength() / zoomRange.getLength();
            return zoomRange.getMin() + (value - transformedRange.getMin()) / stretch;
        }
        if (!continuous) {
            if (value == transformedRange.getMin()) {
                return zoomRange.getMin();
            }
            if (value == transformedRange.getMax()) {
                return zoomRange.getMax();
            }
            return value;
        }
        if (transformedRange.isBefore(value)) {
            if (zoomRange.getMin() == visibleRange.getMin()) {
                return visibleRange.getMin();
            }
            return visibleRange.getMin()
                    + (value - visibleRange.getMin()) / (transformedRange.getMin() - visibleRange.getMin())
                    * (zoomRange.getMin() - visibleRange.getMin());
        }
        if (transformedRange.isAfter(value)) {
            if (visibleRange.getMax() == zoomRange.getMax()) {
                return zoomRange.getMax();
            }
            return zoomRange.getMax()
                    + (value - transformedRange.getMax()) / (visibleRange.getMax() - transformedRange.getMax())
                    * (visibleRange.getMax() - zoomRange.getMax());
        }
        return value;
    }

    /// Returns whether outer visible-range segments are compressed continuously.
    ///
    /// When this flag is `false`, the gap between [#getZoomRange()] and [#getTransformedRange()]
    /// collapses onto the transformed-range edges instead.
    public final boolean isContinuous() {
        return continuous;
    }

    /// Enables or disables continuous remapping of the outer visible-range segments.
    ///
    /// Successful changes publish the `continuous` property.
    ///
    /// @param continuous `true` to keep the full mapping continuous
    public void setContinuous(boolean continuous) {
        boolean oldValue = this.continuous;
        if (continuous != oldValue) {
            this.continuous = continuous;
            firePropertyChange("continuous", oldValue, continuous);
        }
    }

    /// Replaces the current zoom factor.
    ///
    /// Factors below `1.0` are rejected. Detached transformers simply store the new value. When an
    /// owning axis is present, successful updates also publish the `zoomFactor` property so the axis
    /// can invalidate dependent UI.
    ///
    /// @param zoomFactor the new local-zoom factor
    /// @return `true` when the factor was accepted
    public boolean setZoomFactor(double zoomFactor) {
        if (zoomFactor < 1.0) {
            return false;
        }
        if (axis == null) {
            this.zoomFactor = zoomFactor;
            return true;
        }

        double oldValue = this.zoomFactor;
        this.zoomFactor = zoomFactor;
        if (axis.getVisibleRange().contains(getTransformedRange())) {
            firePropertyChange("zoomFactor", oldValue, zoomFactor);
            return true;
        }

        this.zoomFactor = oldValue;
        return false;
    }

    /// Replaces the current source-space zoom interval.
    ///
    /// When an owning axis is present, the new interval must stay inside that axis's visible range.
    /// Successful updates publish the `zoomRange` property with a defensive-copy payload.
    ///
    /// @param min new lower zoom bound
    /// @param max new upper zoom bound
    /// @return `true` when the interval was accepted
    public boolean setZoomRange(double min, double max) {
        if (min > max) {
            return false;
        }
        if (axis == null) {
            zoomRange.set(min, max);
            firePropertyChange("zoomRange", null, getZoomRange());
            return true;
        }

        double oldMin = zoomRange.getMin();
        double oldMax = zoomRange.getMax();
        zoomRange.set(min, max);
        if (!axis.getVisibleRange().contains(zoomRange)) {
            zoomRange.set(oldMin, oldMax);
            return false;
        }

        firePropertyChange("zoomRange", null, getZoomRange());
        return true;
    }

    /// Expands `interval` just enough to keep the current zoom range representable.
    ///
    /// [Axis] calls this during range normalization so data and visible ranges continue to cover
    /// the local-zoom lens.
    @Override
    public boolean validateInterval(DataInterval interval) {
        if (interval.contains(zoomRange)) {
            return false;
        }
        interval.add(zoomRange);
        return true;
    }

    private double applyContinuous(double value, DataInterval transformedRange, DataInterval visibleRange) {
        if (zoomRange.isBefore(value)) {
            if (zoomRange.getMin() == visibleRange.getMin()) {
                return visibleRange.getMin();
            }
            return visibleRange.getMin()
                    + (value - visibleRange.getMin()) * (transformedRange.getMin() - visibleRange.getMin())
                    / (zoomRange.getMin() - visibleRange.getMin());
        }
        if (zoomRange.isAfter(value)) {
            if (visibleRange.getMax() == zoomRange.getMax()) {
                return visibleRange.getMax();
            }
            return transformedRange.getMax()
                    + (value - zoomRange.getMax()) * (visibleRange.getMax() - transformedRange.getMax())
                    / (visibleRange.getMax() - zoomRange.getMax());
        }
        return value;
    }

    private double applyDiscontinuous(double value, DataInterval transformedRange) {
        if (value >= transformedRange.getMin() && zoomRange.isBefore(value)) {
            return transformedRange.getMin();
        }
        if (zoomRange.isAfter(value) && value <= transformedRange.getMax()) {
            return transformedRange.getMax();
        }
        return value;
    }

    private boolean isIdentityMapping() {
        return zoomFactor == 1.0 || zoomRange.getLength() == 0.0;
    }
}
