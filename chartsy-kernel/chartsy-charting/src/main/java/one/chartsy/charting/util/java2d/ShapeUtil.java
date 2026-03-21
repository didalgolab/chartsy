package one.chartsy.charting.util.java2d;

import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/// Geometry helpers used by the chart renderers for hit testing, bounds expansion, and lightweight
/// shape translation.
///
/// The utility favors exact or analytically tightened results over the conservative answers
/// returned by plain `Shape#getBounds2D()`. In particular,
/// [#distanceTo(Shape, double, double, Point2D)] follows line, quadratic, and cubic path segments
/// directly, while [#getTightBounds2D(Shape)] evaluates interior extrema of Bezier segments
/// instead of relying only on control points.
///
/// ### API Notes
///
/// - Closed shapes are treated as filled regions for distance checks, so points already inside the
///   shape are at distance `0`.
/// - [#getTranslatedShape(Shape, double, double)] preserves common concrete shape types when it can
///   and falls back to [TranslatedShape] only for shapes that do not have a cheaper specialized
///   representation here.
public final class ShapeUtil {
    /// Shared implementation behind [#distanceTo(Shape, double, double, Point2D)] once the caller
    /// has decided whether interior points count as immediate hits.
    ///
    /// Path distances are evaluated analytically for line, quadratic, and cubic segments. When
    /// `countInteriorAsHit` is `true`, interior points of closed shapes short-circuit to distance
    /// `0` before segment analysis starts.
    private static double distanceToShapeGeometry(Shape shape, boolean countInteriorAsHit, double x, double y, Point2D closestPoint) {
        if (shape instanceof Line2D line)
            return distanceTo(line, x, y, closestPoint);

        if (countInteriorAsHit) {
            if (shape instanceof Rectangle2D rectangle)
                return distanceTo(rectangle, x, y, closestPoint);

            if (shape.contains(x, y)) {
                if (closestPoint != null)
                    closestPoint.setLocation(x, y);
                return 0.0;
            }
        }

        if (closestPoint != null)
            closestPoint.setLocation(Double.NaN, Double.NaN);

        double bestDistance = Double.POSITIVE_INFINITY;
        double[] segmentCoordinates = new double[6];
        double[] solutions = new double[6];
        Point2D.Double segmentClosestPoint = (closestPoint == null) ? null : new Point2D.Double();
        double subpathStartX = 0.0;
        double subpathStartY = 0.0;
        double currentX = 0.0;
        double currentY = 0.0;

        for (PathIterator iterator = shape.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            int segmentType = iterator.currentSegment(segmentCoordinates);
            switch (segmentType) {
            case PathIterator.SEG_MOVETO -> {
                currentX = subpathStartX = segmentCoordinates[0];
                currentY = subpathStartY = segmentCoordinates[1];
                bestDistance = updateBestDistance(bestDistance, currentX, currentY, x, y, closestPoint);
            }
            case PathIterator.SEG_LINETO -> {
                double endX = segmentCoordinates[0];
                double endY = segmentCoordinates[1];
                double candidateDistance = distanceTo(new Line2D.Double(currentX, currentY, endX, endY), x, y, segmentClosestPoint);
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    if (closestPoint != null)
                        closestPoint.setLocation(segmentClosestPoint);
                }
                currentX = endX;
                currentY = endY;
            }
            case PathIterator.SEG_QUADTO -> {
                double controlX = segmentCoordinates[0];
                double controlY = segmentCoordinates[1];
                double endX = segmentCoordinates[2];
                double endY = segmentCoordinates[3];
                bestDistance = updateQuadraticDistance(bestDistance, currentX, currentY, controlX, controlY, endX, endY, x, y,
                        closestPoint, solutions);
                currentX = endX;
                currentY = endY;
            }
            case PathIterator.SEG_CUBICTO -> {
                double control1X = segmentCoordinates[0];
                double control1Y = segmentCoordinates[1];
                double control2X = segmentCoordinates[2];
                double control2Y = segmentCoordinates[3];
                double endX = segmentCoordinates[4];
                double endY = segmentCoordinates[5];
                bestDistance = updateCubicDistance(bestDistance, currentX, currentY, control1X, control1Y, control2X, control2Y,
                        endX, endY, x, y, closestPoint, solutions);
                currentX = endX;
                currentY = endY;
            }
            case PathIterator.SEG_CLOSE -> {
                double candidateDistance = distanceTo(new Line2D.Double(currentX, currentY, subpathStartX, subpathStartY), x, y,
                        segmentClosestPoint);
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    if (closestPoint != null)
                        closestPoint.setLocation(segmentClosestPoint);
                }
                currentX = subpathStartX;
                currentY = subpathStartY;
            }
            default -> throw new Error("Invalid seg type: " + segmentType);
            }
        }
        return bestDistance;
    }

    private static double updateBestDistance(double bestDistance, double candidateX, double candidateY, double x, double y,
            Point2D closestPoint) {
        double deltaX = candidateX - x;
        double deltaY = candidateY - y;
        double candidateDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (candidateDistance < bestDistance) {
            if (closestPoint != null)
                closestPoint.setLocation(candidateX, candidateY);
            return candidateDistance;
        }
        return bestDistance;
    }

    private static double updateQuadraticDistance(double bestDistance, double startX, double startY, double controlX, double controlY,
            double endX, double endY, double x, double y, Point2D closestPoint, double[] solutions) {
        double quadraticX = startX - 2.0 * controlX + endX;
        double linearX = 2.0 * (controlX - startX);
        double constantX = startX - x;
        double quadraticY = startY - 2.0 * controlY + endY;
        double linearY = 2.0 * (controlY - startY);
        double constantY = startY - y;

        double[] coefficients = {
                linearX * constantX + linearY * constantY,
                linearX * linearX + 2.0 * quadraticX * constantX + linearY * linearY + 2.0 * quadraticY * constantY,
                3.0 * (quadraticX * linearX + quadraticY * linearY),
                2.0 * (quadraticX * quadraticX + quadraticY * quadraticY)
        };
        int solutionCount = PathSegmentDistanceAlgorithms.solvePolynomial(3, coefficients, 0.0, 1.0, solutions);
        for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
            double t = solutions[solutionIndex];
            double curveX = quadraticCoordinate(startX, controlX, endX, t);
            double curveY = quadraticCoordinate(startY, controlY, endY, t);
            bestDistance = updateBestDistance(bestDistance, curveX, curveY, x, y, closestPoint);
        }
        return updateBestDistance(bestDistance, endX, endY, x, y, closestPoint);
    }

    private static double updateCubicDistance(double bestDistance, double startX, double startY, double control1X, double control1Y,
            double control2X, double control2Y, double endX, double endY, double x, double y, Point2D closestPoint,
            double[] solutions) {
        double cubicX = 3.0 * (control1X - control2X) + endX - startX;
        double quadraticX = 3.0 * (startX - 2.0 * control1X + control2X);
        double linearX = 3.0 * (control1X - startX);
        double constantX = startX - x;
        double cubicY = 3.0 * (control1Y - control2Y) + endY - startY;
        double quadraticY = 3.0 * (startY - 2.0 * control1Y + control2Y);
        double linearY = 3.0 * (control1Y - startY);
        double constantY = startY - y;

        double[] coefficients = {
                linearX * constantX + linearY * constantY,
                2.0 * quadraticX * constantX + linearX * linearX + 2.0 * quadraticY * constantY + linearY * linearY,
                3.0 * (cubicX * constantX + quadraticX * linearX + cubicY * constantY + quadraticY * linearY),
                4.0 * cubicX * linearX + 2.0 * quadraticX * quadraticX + 4.0 * cubicY * linearY + 2.0 * quadraticY * quadraticY,
                5.0 * (cubicX * quadraticX + cubicY * quadraticY),
                3.0 * (cubicX * cubicX + cubicY * cubicY)
        };
        int solutionCount = PathSegmentDistanceAlgorithms.solvePolynomial(5, coefficients, 0.0, 1.0, solutions);
        for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
            double t = solutions[solutionIndex];
            double curveX = cubicCoordinate(startX, control1X, control2X, endX, t);
            double curveY = cubicCoordinate(startY, control1Y, control2Y, endY, t);
            bestDistance = updateBestDistance(bestDistance, curveX, curveY, x, y, closestPoint);
        }
        return updateBestDistance(bestDistance, endX, endY, x, y, closestPoint);
    }

    private static double quadraticCoordinate(double start, double control, double end, double t) {
        double oneMinusT = 1.0 - t;
        return oneMinusT * oneMinusT * start
                + 2.0 * t * oneMinusT * control
                + t * t * end;
    }

    private static double cubicCoordinate(double start, double control1, double control2, double end, double t) {
        double oneMinusT = 1.0 - t;
        return oneMinusT * oneMinusT * oneMinusT * start
                + 3.0 * t * oneMinusT * oneMinusT * control1
                + 3.0 * t * t * oneMinusT * control2
                + t * t * t * end;
    }

    /// Returns the shortest Euclidean distance from a point to a line segment.
    ///
    /// Degenerate segments are treated as a single endpoint. If `closestPoint` is not `null`, this
    /// method stores the nearest point on the segment into it.
    ///
    /// @param closestPoint receives the nearest point on `line` when not `null`
    /// @return distance from `(x, y)` to `line`
    public static double distanceTo(Line2D line, double x, double y, Point2D closestPoint) {
        double startX = line.getX1();
        double startY = line.getY1();
        double endX = line.getX2();
        double endY = line.getY2();
        double lengthSq = (endX - startX) * (endX - startX) + (endY - startY) * (endY - startY);
        if (lengthSq == 0.0) {
            if (closestPoint != null)
                closestPoint.setLocation(startX, startY);
            return Math.sqrt((x - startX) * (x - startX) + (y - startY) * (y - startY));
        }

        double alongSegment = (x - startX) * (endX - startX) + (y - startY) * (endY - startY);
        double crossProduct = (x - startX) * (endY - startY) - (y - startY) * (endX - startX);
        if (alongSegment < 0.0) {
            if (closestPoint != null)
                closestPoint.setLocation(startX, startY);
            alongSegment = -alongSegment;
        } else if (alongSegment > lengthSq) {
            if (closestPoint != null)
                closestPoint.setLocation(endX, endY);
            alongSegment -= lengthSq;
        } else {
            if (closestPoint != null)
                closestPoint.setLocation(startX + alongSegment / lengthSq * (endX - startX),
                        startY + alongSegment / lengthSq * (endY - startY));
            alongSegment = 0.0;
        }
        return Math.sqrt((alongSegment * alongSegment + crossProduct * crossProduct) / lengthSq);
    }

    /// Returns the shortest Euclidean distance from a point to a rectangle.
    ///
    /// Points inside the rectangle, including points on the border, are at distance `0`. If
    /// `closestPoint` is not `null`, the method stores the clamped point on or in the rectangle.
    ///
    /// @param closestPoint receives the nearest point on or in `rectangle` when not `null`
    /// @return distance from `(x, y)` to `rectangle`
    public static double distanceTo(Rectangle2D rectangle, double x, double y, Point2D closestPoint) {
        double minX = rectangle.getMinX();
        double maxX = rectangle.getMaxX();
        double minY = rectangle.getMinY();
        double maxY = rectangle.getMaxY();

        double closestX;
        double deltaX;
        if (x < minX) {
            closestX = minX;
            deltaX = minX - x;
        } else if (x <= maxX) {
            closestX = x;
            deltaX = 0.0;
        } else {
            closestX = maxX;
            deltaX = x - maxX;
        }

        double closestY;
        double deltaY;
        if (y < minY) {
            closestY = minY;
            deltaY = minY - y;
        } else if (y <= maxY) {
            closestY = y;
            deltaY = 0.0;
        } else {
            closestY = maxY;
            deltaY = y - maxY;
        }

        if (closestPoint != null)
            closestPoint.setLocation(closestX, closestY);
        if (deltaX == 0.0)
            return deltaY;
        if (deltaY == 0.0)
            return deltaX;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    /// Returns the shortest distance from a point to a shape's drawn geometry.
    ///
    /// Closed shapes behave like filled regions, so points already inside the shape are immediate
    /// hits. Open shapes such as lines are measured against the path segments themselves. If
    /// `closestPoint` is not `null`, the nearest point on or in the shape is stored into it.
    ///
    /// @param closestPoint receives the nearest point on or in `shape` when not `null`
    /// @return distance from `(x, y)` to `shape`
    public static double distanceTo(Shape shape, double x, double y, Point2D closestPoint) {
        return distanceToShapeGeometry(shape, true, x, y, closestPoint);
    }

    /// Returns the smallest axis-aligned bounds this utility can derive for a shape.
    ///
    /// For common concrete shape types this delegates to `shape.getBounds2D()`. For general paths
    /// it also evaluates quadratic and cubic extrema, which makes the result tighter than the
    /// control-point bounds returned by many generic implementations. Empty paths produce an empty
    /// rectangle.
    public static Rectangle2D getTightBounds2D(Shape shape) {
        if (shape instanceof Line2D
                || shape instanceof Rectangle2D
                || shape instanceof Polygon
                || shape instanceof Polygon2D
                || shape instanceof RoundRectangle2D
                || shape instanceof Ellipse2D
                || shape instanceof Arc2D
                || shape instanceof Area)
            return shape.getBounds2D();

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double[] segmentCoordinates = new double[6];
        double currentX = 0.0;
        double currentY = 0.0;

        for (PathIterator iterator = shape.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            int segmentType = iterator.currentSegment(segmentCoordinates);
            switch (segmentType) {
            case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> {
                currentX = segmentCoordinates[0];
                currentY = segmentCoordinates[1];
                minX = Math.min(minX, currentX);
                maxX = Math.max(maxX, currentX);
                minY = Math.min(minY, currentY);
                maxY = Math.max(maxY, currentY);
            }
            case PathIterator.SEG_QUADTO -> {
                double controlX = segmentCoordinates[0];
                double controlY = segmentCoordinates[1];
                double endX = segmentCoordinates[2];
                double endY = segmentCoordinates[3];
                minX = Math.min(minX, endX);
                maxX = Math.max(maxX, endX);
                minY = Math.min(minY, endY);
                maxY = Math.max(maxY, endY);

                double quadraticX = currentX - 2.0 * controlX + endX;
                if (quadraticX != 0.0) {
                    double t = (currentX - controlX) / quadraticX;
                    if (t > 0.0 && t < 1.0) {
                        double extremeX = quadraticCoordinate(currentX, controlX, endX, t);
                        minX = Math.min(minX, extremeX);
                        maxX = Math.max(maxX, extremeX);
                    }
                }

                double quadraticY = currentY - 2.0 * controlY + endY;
                if (quadraticY != 0.0) {
                    double t = (currentY - controlY) / quadraticY;
                    if (t > 0.0 && t < 1.0) {
                        double extremeY = quadraticCoordinate(currentY, controlY, endY, t);
                        minY = Math.min(minY, extremeY);
                        maxY = Math.max(maxY, extremeY);
                    }
                }
                currentX = endX;
                currentY = endY;
            }
            case PathIterator.SEG_CUBICTO -> {
                double control1X = segmentCoordinates[0];
                double control1Y = segmentCoordinates[1];
                double control2X = segmentCoordinates[2];
                double control2Y = segmentCoordinates[3];
                double endX = segmentCoordinates[4];
                double endY = segmentCoordinates[5];
                minX = Math.min(minX, endX);
                maxX = Math.max(maxX, endX);
                minY = Math.min(minY, endY);
                maxY = Math.max(maxY, endY);

                double cubicX = -currentX + 3.0 * control1X - 3.0 * control2X + endX;
                double quadraticX = 2.0 * currentX - 4.0 * control1X + 2.0 * control2X;
                double linearX = -currentX + control1X;
                if (cubicX != 0.0) {
                    double discriminant = quadraticX * quadraticX - 4.0 * cubicX * linearX;
                    if (discriminant >= 0.0) {
                        double root = Math.sqrt(discriminant);
                        double firstT = (root - quadraticX) / (2.0 * cubicX);
                        if (firstT > 0.0 && firstT < 1.0) {
                            double extremeX = cubicCoordinate(currentX, control1X, control2X, endX, firstT);
                            minX = Math.min(minX, extremeX);
                            maxX = Math.max(maxX, extremeX);
                        }
                        double secondT = (-root - quadraticX) / (2.0 * cubicX);
                        if (secondT > 0.0 && secondT < 1.0) {
                            double extremeX = cubicCoordinate(currentX, control1X, control2X, endX, secondT);
                            minX = Math.min(minX, extremeX);
                            maxX = Math.max(maxX, extremeX);
                        }
                    }
                } else if (quadraticX != 0.0) {
                    double t = -linearX / quadraticX;
                    if (t > 0.0 && t < 1.0) {
                        double extremeX = cubicCoordinate(currentX, control1X, control2X, endX, t);
                        minX = Math.min(minX, extremeX);
                        maxX = Math.max(maxX, extremeX);
                    }
                }

                double cubicY = -currentY + 3.0 * control1Y - 3.0 * control2Y + endY;
                double quadraticY = 2.0 * currentY - 4.0 * control1Y + 2.0 * control2Y;
                double linearY = -currentY + control1Y;
                if (cubicY != 0.0) {
                    double discriminant = quadraticY * quadraticY - 4.0 * cubicY * linearY;
                    if (discriminant >= 0.0) {
                        double root = Math.sqrt(discriminant);
                        double firstT = (root - quadraticY) / (2.0 * cubicY);
                        if (firstT > 0.0 && firstT < 1.0) {
                            double extremeY = cubicCoordinate(currentY, control1Y, control2Y, endY, firstT);
                            minY = Math.min(minY, extremeY);
                            maxY = Math.max(maxY, extremeY);
                        }
                        double secondT = (-root - quadraticY) / (2.0 * cubicY);
                        if (secondT > 0.0 && secondT < 1.0) {
                            double extremeY = cubicCoordinate(currentY, control1Y, control2Y, endY, secondT);
                            minY = Math.min(minY, extremeY);
                            maxY = Math.max(maxY, extremeY);
                        }
                    }
                } else if (quadraticY != 0.0) {
                    double t = -linearY / quadraticY;
                    if (t > 0.0 && t < 1.0) {
                        double extremeY = cubicCoordinate(currentY, control1Y, control2Y, endY, t);
                        minY = Math.min(minY, extremeY);
                        maxY = Math.max(maxY, extremeY);
                    }
                }
                currentX = endX;
                currentY = endY;
            }
            case PathIterator.SEG_CLOSE -> {
            }
            default -> throw new Error("Invalid seg type: " + segmentType);
            }
        }

        if (Double.isInfinite(minX) || Double.isInfinite(maxX) || Double.isInfinite(minY) || Double.isInfinite(maxY))
            return new Rectangle2D.Double();
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /// Returns `shape` translated by `(dx, dy)`.
    ///
    /// Zero translations return the original shape unchanged. Nested [TranslatedShape] wrappers are
    /// flattened, common concrete shape types are recreated directly, and unsupported shapes fall
    /// back to a new [TranslatedShape] view.
    ///
    /// Integer translations preserve `Polygon`. Non-integral translations of `Polygon` or
    /// [Polygon2D] are materialized as [Polygon2D.Double].
    public static Shape getTranslatedShape(Shape shape, double dx, double dy) {
        Shape originalShape = shape;
        double offsetX = dx;
        double offsetY = dy;
        if (offsetX == 0.0 && offsetY == 0.0)
            return originalShape;

        if (originalShape instanceof TranslatedShape translatedShape) {
            offsetX += translatedShape.getOffsetX();
            offsetY += translatedShape.getOffsetY();
            originalShape = translatedShape.getOriginalShape();
            if (offsetX == 0.0 && offsetY == 0.0)
                return originalShape;
        }

        if (originalShape instanceof Line2D line)
            return new Line2D.Double(line.getX1() + offsetX, line.getY1() + offsetY, line.getX2() + offsetX, line.getY2() + offsetY);
        if (originalShape instanceof Rectangle2D rectangle)
            return new Rectangle2D.Double(rectangle.getX() + offsetX, rectangle.getY() + offsetY, rectangle.getWidth(), rectangle.getHeight());
        if (originalShape instanceof RoundRectangle2D rectangle)
            return new RoundRectangle2D.Double(rectangle.getX() + offsetX, rectangle.getY() + offsetY, rectangle.getWidth(),
                    rectangle.getHeight(), rectangle.getArcWidth(), rectangle.getArcHeight());
        if (originalShape instanceof Ellipse2D ellipse)
            return new Ellipse2D.Double(ellipse.getX() + offsetX, ellipse.getY() + offsetY, ellipse.getWidth(), ellipse.getHeight());
        if (originalShape instanceof Polygon polygon)
            return translatePolygon(polygon, offsetX, offsetY);
        if (originalShape instanceof Polygon2D polygon)
            return translatePolygon(polygon, offsetX, offsetY);
        if (originalShape instanceof GeneralPath generalPath)
            return generalPath.createTransformedShape(AffineTransform.getTranslateInstance(offsetX, offsetY));
        return new TranslatedShape(originalShape, offsetX, offsetY);
    }

    private static Shape translatePolygon(Polygon polygon, double dx, double dy) {
        int pointCount = polygon.npoints;
        if ((int) dx == dx && (int) dy == dy) {
            int offsetX = (int) dx;
            int offsetY = (int) dy;
            int[] xPoints = new int[pointCount];
            int[] yPoints = new int[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                xPoints[pointIndex] = polygon.xpoints[pointIndex] + offsetX;
                yPoints[pointIndex] = polygon.ypoints[pointIndex] + offsetY;
            }
            return new Polygon(xPoints, yPoints, pointCount);
        }

        Point2D.Double[] points = new Point2D.Double[pointCount];
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
            points[pointIndex] = new Point2D.Double(polygon.xpoints[pointIndex] + dx, polygon.ypoints[pointIndex] + dy);
        return new Polygon2D.Double(points);
    }

    private static Shape translatePolygon(Polygon2D polygon, double dx, double dy) {
        Point2D[] sourcePoints = polygon.getPoints();
        Point2D.Double[] translatedPoints = new Point2D.Double[sourcePoints.length];
        for (int pointIndex = 0; pointIndex < sourcePoints.length; pointIndex++) {
            Point2D point = sourcePoints[pointIndex];
            translatedPoints[pointIndex] = new Point2D.Double(point.getX() + dx, point.getY() + dy);
        }
        return new Polygon2D.Double(translatedPoints);
    }

    private ShapeUtil() {
    }
}
