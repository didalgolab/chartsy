package one.chartsy.charting;

/// Strategy object that describes one chart-picking request.
///
/// [Chart], [ChartRenderer], and renderer-specific picking code pass the same picker through the
/// whole lookup so it can decide which renderers participate, where the pick anchor lies, how
/// candidate points are ranked, and whether distance should be measured in display space or data
/// space. [DefaultChartDataPicker] provides the standard mouse-position implementation, while
/// interactors can override individual methods to constrain pickable renderers or customize the
/// distance metric.
public interface ChartDataPicker {

    /// Returns whether `renderer` should participate in this pick request.
    boolean accept(ChartRenderer renderer);

    /// Computes the distance between the pick anchor and one candidate location.
    ///
    /// The coordinates are expressed in display space unless [#useDataSpace()] requests data-space
    /// comparison.
    double computeDistance(double pickX, double pickY, double candidateX, double candidateY);

    /// Returns the maximum distance threshold accepted by this picker.
    ///
    /// Renderer implementations use this value to limit search work and to reject candidates that
    /// are too far from the pick anchor.
    int getPickDistance();

    /// Returns the display-space x coordinate of the pick anchor.
    int getPickX();

    /// Returns the display-space y coordinate of the pick anchor.
    int getPickY();

    /// Returns whether nearest-item calculations should compare candidates in data space.
    ///
    /// When this method returns `true`, renderers may transform the pick anchor and candidate
    /// points back into data coordinates before calling [#computeDistance(double, double, double,
    /// double)].
    boolean useDataSpace();
}
