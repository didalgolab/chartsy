package one.chartsy.ui.chart;

import java.awt.geom.Point2D;
import java.util.List;

@FunctionalInterface
public interface PolyPointsAware {
    
    /**
     * Returns the list of anchor points defining the graphic object's geometry.
     * 
     * @return the anchor points
     */
    List<ChartPoint> getAnchorPoints();
    
    /**
     * Returns the number of points exposed by the geometry of the graphic
     * object.
     * 
     * @return the number of points
     */
    default int getPointCount() {
        return getAnchorPoints().size();
    }
    
    /**
     * Returns the location of the point at the specified {@code index}. The {@code index} will
     * be provided in the range from {@code 0} to {@link #getPointCount()}-1
     * (inclusive).
     * 
     * @param index
     *            the index
     * @param coords
     *            the coordinate system used to display the object
     * @return the point at {@code index}
     */
    default Point2D getPoint(int index, CoordinateSystem coords) {
        return coords.transform(getAnchorPoints().get(index));
    }
    
    /**
     * Moves the point at the given {@code index} to the specified location. The
     * {@code index} will be provided in the range from {@code 0} to
     * {@link #getPointCount()}-1
     * 
     * @param index
     *            the point index
     * @param point
     *            the new point location
     * @param coords
     *            the coordinate system used to display the graphic object
     */
    default void movePoint(int index, Point2D point, CoordinateSystem coords) {
        getAnchorPoints().set(index, coords.inverseTransform(point));
    }
    
    default void setPoints(ChartPoint... points) {
        List<ChartPoint> anchorPoints = getAnchorPoints();
        
        int i = 0;
        for (; i < anchorPoints.size(); i++)
            anchorPoints.set(i, points[i]);
        for (; i < points.length; i++)
            anchorPoints.add(i, points[i]);
    }
}