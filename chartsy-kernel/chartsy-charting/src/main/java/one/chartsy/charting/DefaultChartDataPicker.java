package one.chartsy.charting;

/// Default [ChartDataPicker] backed by one fixed pick anchor in display space.
///
/// This implementation keeps every renderer eligible, measures proximity with the Euclidean
/// distance between the anchor and each candidate, and never asks renderers to transform the
/// request into data space. Interactors commonly subclass it when they want the standard
/// mouse-position anchor semantics but need custom renderer filtering or distance weighting.
public class DefaultChartDataPicker implements ChartDataPicker {
    private final int pickX;
    private final int pickY;
    private final int pickDistance;

    /// Creates a picker with a fixed display-space anchor and distance threshold.
    ///
    /// The supplied values are returned unchanged by [#getPickX()], [#getPickY()], and
    /// [#getPickDistance()].
    ///
    /// @param pickX display-space x coordinate of the pick anchor
    /// @param pickY display-space y coordinate of the pick anchor
    /// @param pickDistance maximum accepted distance from the pick anchor
    public DefaultChartDataPicker(int pickX, int pickY, int pickDistance) {
        this.pickX = pickX;
        this.pickY = pickY;
        this.pickDistance = pickDistance;
    }

    /// Returns `true` for every renderer.
    ///
    /// Subclasses override this to narrow the pick request to one chart layer, axis, or renderer
    /// family.
    @Override
    public boolean accept(ChartRenderer renderer) {
        return true;
    }

    /// Computes the Euclidean distance between the pick anchor and one candidate location.
    ///
    /// Because [#useDataSpace()] returns `false`, renderers normally call this with display-space
    /// coordinates.
    @Override
    public double computeDistance(double pickX, double pickY, double candidateX, double candidateY) {
        return Math.hypot(candidateX - pickX, candidateY - pickY);
    }

    /// Returns the fixed distance threshold supplied at construction time.
    @Override
    public int getPickDistance() {
        return pickDistance;
    }

    /// Returns the fixed display-space x coordinate of the pick anchor.
    @Override
    public int getPickX() {
        return pickX;
    }

    /// Returns the fixed display-space y coordinate of the pick anchor.
    @Override
    public int getPickY() {
        return pickY;
    }

    /// Returns `false` so renderers compare candidates in display space by default.
    @Override
    public boolean useDataSpace() {
        return false;
    }
}
