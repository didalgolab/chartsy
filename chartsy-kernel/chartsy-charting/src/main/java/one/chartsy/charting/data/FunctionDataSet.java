package one.chartsy.charting.data;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.event.DataSetContentsEvent;

/// Abstract `DataSet` that samples a mathematical function on an evenly spaced x grid.
///
/// Unlike `DefaultDataSet`, this type does not store y-values. Each call to [#getYData(int)]
/// evaluates [#callFunction(double)] for the sampled x position, so rendering and range
/// computation may reevaluate the function many times. Implementations should therefore keep the
/// function cheap and side-effect free.
///
/// The sampling definition is the triple `(minimumX, maximumX, sampleCount)`. Logical indices are
/// mapped linearly onto the closed interval `[minimumX, maximumX]`, and this class reports its x
/// values as sorted. Callers should therefore supply an ascending interval and a sample count
/// greater than `1`; the current implementation does not validate either precondition.
public abstract class FunctionDataSet extends AbstractDataSet {
    private double minimumX;
    private double maximumX;
    private transient double xSpan;
    private int sampleCount;

    /// Creates a function dataset with the supplied sampling definition.
    public FunctionDataSet(double minimumX, double maximumX, int sampleCount) {
        setDefinition(minimumX, maximumX, sampleCount);
    }

    /// Evaluates the function at `x`.
    ///
    /// Returning `Double.NaN` or this dataset's undefined-value sentinel causes the sample to be
    /// excluded from y-range computation, and optionally from x-range computation when
    /// [#isXRangeIncludingUndefinedPoints()] is `false`.
    public abstract double callFunction(double x);

    /// Recomputes x/y limits by evaluating the function across the full sampled grid.
    ///
    /// Undefined samples are filtered using both `Double.NaN` and `getUndefValue()`, matching the
    /// conventions used by `AbstractDataSet`.
    @Override
    protected void computeLimits(DataInterval xRange, DataInterval yRange) {
        int firstDefinedIndex = -1;
        int lastDefinedIndex = -1;
        yRange.empty();

        Double undefValue = getUndefValue();
        for (int index = 0; index < sampleCount; index++) {
            double y = getYData(index);
            if (!isDefined(y, undefValue)) {
                continue;
            }

            if (firstDefinedIndex < 0) {
                firstDefinedIndex = index;
            }
            lastDefinedIndex = index;
            yRange.add(y);
        }

        if (isXRangeIncludingUndefinedPoints()) {
            xRange.set(minimumX, maximumX);
        } else {
            xRange.empty();
            if (firstDefinedIndex >= 0) {
                xRange.set(getXData(firstDefinedIndex), getXData(lastDefinedIndex));
            }
        }
    }

    /// Returns the uniform spacing between adjacent sampled x positions.
    @Override
    public double getMinimumXDifference() {
        return sampleStep();
    }

    /// Returns the sampled x coordinate at `index`.
    ///
    /// The first sample is `minimumX`, the last sample is `maximumX`, and intermediate samples are
    /// distributed uniformly between them.
    @Override
    public double getXData(int index) {
        return minimumX + sampleStep() * index;
    }

    /// Returns the function value at the sampled x position for `index`.
    @Override
    public double getYData(int index) {
        return callFunction(getXData(index));
    }

    /// Returns `true`.
    ///
    /// The implementation assumes the sampling grid is monotonic in x.
    @Override
    public boolean isXValuesSorted() {
        return true;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        xSpan = maximumX - minimumX;
    }

    /// Replaces the sampling definition.
    ///
    /// When any part of the definition changes, cached limits are invalidated and a full dataset
    /// contents event is fired. Reapplying the same definition is a no-op.
    public void setDefinition(double minimumX, double maximumX, int sampleCount) {
        if (minimumX == this.minimumX
                && maximumX == this.maximumX
                && sampleCount == this.sampleCount) {
            return;
        }

        this.minimumX = minimumX;
        this.maximumX = maximumX;
        this.sampleCount = sampleCount;
        xSpan = maximumX - minimumX;
        super.invalidateLimits();
        super.fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    @Override
    public int size() {
        return sampleCount;
    }

    private static boolean isDefined(double y, Double undefValue) {
        return !Double.isNaN(y) && (undefValue == null || y != undefValue.doubleValue());
    }

    private double sampleStep() {
        return xSpan / (sampleCount - 1);
    }
}
