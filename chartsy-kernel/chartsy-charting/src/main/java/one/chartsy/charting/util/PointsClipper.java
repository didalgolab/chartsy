package one.chartsy.charting.util;

import one.chartsy.charting.DoublePoints;

/// In-place clipping helpers for monotonic point batches used by polyline renderers.
///
/// The methods in this utility do not remove points or allocate replacement batches. Instead they
/// move the leading and trailing runs of an existing [DoublePoints] instance onto the clip
/// boundary, interpolating the boundary y-value from the first segment that crosses into the kept
/// x-range.
///
/// This logic assumes the points are ordered by x. If the batch is not x-monotonic, the first and
/// last crossings selected by the scan may not correspond to the visible polyline segment.
public final class PointsClipper {

    /// Clips an x-ordered point batch to the supplied closed x-range in place.
    ///
    /// When the batch enters the requested interval, every point before the first kept point is
    /// moved onto `minimumX`, and every point after the last kept point is moved onto `maximumX`.
    /// The y-value written to those boundary runs is linearly interpolated from the segment that
    /// crosses the boundary. If the whole batch lies completely on one side of the interval, this
    /// method leaves it unchanged.
    ///
    /// @param points point batch to mutate
    /// @param minimumX inclusive lower x boundary
    /// @param maximumX inclusive upper x boundary
    /// @return the same `points` instance after clipping
    public static DoublePoints clipX(DoublePoints points, double minimumX, double maximumX) {
        int size = points.size();
        int firstInside = 0;
        while (firstInside < size && points.getX(firstInside) < minimumX) {
            firstInside++;
        }

        if (firstInside >= size) {
            return points;
        }

        if (firstInside > 0) {
            double minimumY = GraphicUtil.computeYSeg(
                    points.getX(firstInside - 1),
                    points.getY(firstInside - 1),
                    points.getX(firstInside),
                    points.getY(firstInside),
                    minimumX);
            for (int i = firstInside - 1; i >= 0; i--) {
                points.set(i, minimumX, minimumY);
            }
        }

        int lastInside = size - 1;
        while (lastInside >= 0 && points.getX(lastInside) > maximumX) {
            lastInside--;
        }

        if (lastInside < 0) {
            return points;
        }

        if (lastInside < size - 1) {
            double maximumY = GraphicUtil.computeYSeg(
                    points.getX(lastInside),
                    points.getY(lastInside),
                    points.getX(lastInside + 1),
                    points.getY(lastInside + 1),
                    maximumX);
            for (int i = lastInside + 1; i < size; i++) {
                points.set(i, maximumX, maximumY);
            }
        }

        return points;
    }

    private PointsClipper() {
    }
}
