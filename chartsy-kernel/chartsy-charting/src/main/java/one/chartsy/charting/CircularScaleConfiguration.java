package one.chartsy.charting;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.internal.PolarProjector;
import one.chartsy.charting.util.MathUtil;

class CircularScaleConfiguration extends DefaultScaleConfiguration {
    
    CircularScaleConfiguration() {
    }
    
    @Override
    Axis.Crossing getAutoCrossing() {
        return Axis.MAX_VALUE;
    }
    
    @Override
    double getAxisAngle(double value) {
        double angle = getPolarProjector().getAngleDeg(value, super.scale.getPlotRect(), super.scale.getCoordinateSystem());
        return switch (super.scale.getCircleSide()) {
            case Scale.OUTSIDE -> angle;
            case Scale.INSIDE -> angle + 180.0;
            default -> throw new IllegalStateException("invalid circle side");
        };
    }
    
    @Override
    int estimateVisibleItemCount(int width, int height, int spacing) {
        double scaleLength = getScaleLength();
        double itemExtent = Math.sqrt(width * width + height * height) + spacing;
        return (int) Math.max(Math.round(scaleLength / itemExtent) + 1L, 2L);
    }
    
    @Override
    public boolean contains(Point2D point) {
        BasicStroke hitStroke = new BasicStroke(getTitleDistance() / 2, 0, 0);
        Shape hitShape = hitStroke.createStrokedShape(getAxisShape());
        return hitShape.contains(point);
    }
    
    @Override
    protected Scale.Steps createSteps() {
        Scale scale = super.scale;
        scale.getClass();
        return scale.new CircularSteps();
    }
    
    @Override
    protected void drawAxis(Graphics g) {
        super.scale.getAxisStyle().draw(g, getAxisShape());
    }
    
    private PolarProjector getPolarProjector() {
        return (PolarProjector) super.scale.getProjector();
    }
    
    private Shape getAxisShape() {
        return getPolarProjector().getShape(super.scale.getCrossingValue(), 2,
                super.scale.getPlotRect(), super.scale.getCoordinateSystem());
    }
    
    @Override
    Rectangle2D getAxisBounds(Rectangle2D bounds) {
        if (super.scale.getAxis().getVisibleRange().isEmpty())
            return new Rectangle2D.Double();
        return super.scale.getAxisStyle().getShapeBounds(getAxisShape());
    }
    
    @Override
    protected int getScaleLength() {
        Shape axisShape = getAxisShape();
        if (axisShape instanceof Arc2D arc) {
            int axisDiameter = arc.getBounds().width;
            return (int) Math.abs(MathUtil.toRadians(arc.getAngleExtent()) / 2.0 * axisDiameter);
        }
        Rectangle plotRect = super.scale.getPlotRect();
        double radius = Math.min(plotRect.width, plotRect.height) / 2.0;
        double angleRange = MathUtil.toRadians(getPolarProjector().getRange());
        return (int) Math.abs(angleRange * radius);
    }
    
    @Override
    int getTitleDistance() {
        int titleDistance = super.scale.getTitleOffset() + super.scale.getLabelDistance();
        if (super.scale.isLabelVisible()) {
            double labelWidth = super.scale.getSteps().getMaxLabelWidth();
            double labelHeight = super.scale.getSteps().getMaxLabelHeight();
            titleDistance += (int) Math.sqrt(labelWidth * labelWidth + labelHeight * labelHeight);
        }
        return titleDistance;
    }
}
