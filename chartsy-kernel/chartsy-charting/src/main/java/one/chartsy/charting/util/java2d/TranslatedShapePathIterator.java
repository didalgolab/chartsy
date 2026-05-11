package one.chartsy.charting.util.java2d;

import java.awt.geom.PathIterator;

/// [PathIterator] that adds a fixed translation to every coordinate emitted by another iterator.
///
/// The wrapped iterator still owns segment ordering, winding rule, and completion state. This
/// adapter only mutates the caller-provided coordinate buffer in place after the delegate has
/// written the current segment into it.
record TranslatedShapePathIterator(TranslatedShape shape, PathIterator delegate) implements PathIterator {

    /// Adds the wrapper offsets to one segment represented in `double` coordinates.
    private void translateCoordinates(double[] coords, int segmentType) {
        switch (segmentType) {
            case SEG_CUBICTO -> {
                coords[4] += shape.getOffsetX();
                coords[5] += shape.getOffsetY();
                coords[2] += shape.getOffsetX();
                coords[3] += shape.getOffsetY();
                coords[0] += shape.getOffsetX();
                coords[1] += shape.getOffsetY();
            }
            case SEG_QUADTO -> {
                coords[2] += shape.getOffsetX();
                coords[3] += shape.getOffsetY();
                coords[0] += shape.getOffsetX();
                coords[1] += shape.getOffsetY();
            }
            case SEG_MOVETO, SEG_LINETO -> {
                coords[0] += shape.getOffsetX();
                coords[1] += shape.getOffsetY();
            }
            default -> {
            }
        }
    }

    /// Adds the wrapper offsets to one segment represented in `float` coordinates.
    private void translateCoordinates(float[] coords, int segmentType) {
        float offsetX = (float) shape.getOffsetX();
        float offsetY = (float) shape.getOffsetY();
        switch (segmentType) {
            case SEG_CUBICTO -> {
                coords[4] += offsetX;
                coords[5] += offsetY;
                coords[2] += offsetX;
                coords[3] += offsetY;
                coords[0] += offsetX;
                coords[1] += offsetY;
            }
            case SEG_QUADTO -> {
                coords[2] += offsetX;
                coords[3] += offsetY;
                coords[0] += offsetX;
                coords[1] += offsetY;
            }
            case SEG_MOVETO, SEG_LINETO -> {
                coords[0] += offsetX;
                coords[1] += offsetY;
            }
            default -> {
            }
        }
    }

    @Override
    public int currentSegment(double[] coords) {
        int segmentType = delegate.currentSegment(coords);
        translateCoordinates(coords, segmentType);
        return segmentType;
    }

    @Override
    public int currentSegment(float[] coords) {
        int segmentType = delegate.currentSegment(coords);
        translateCoordinates(coords, segmentType);
        return segmentType;
    }

    @Override
    public int getWindingRule() {
        return delegate.getWindingRule();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public void next() {
        delegate.next();
    }
}
