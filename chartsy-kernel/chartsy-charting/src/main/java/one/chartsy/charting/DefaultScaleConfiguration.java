package one.chartsy.charting;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

import one.chartsy.charting.util.GraphicUtil;

/// Shared base for scale configurations whose extra state is limited to drawing policy rather than
/// dedicated geometry caches.
///
/// This class provides the common parts of the scale paint pipeline used by both straight and
/// circular configurations:
/// - attaching the owning [Scale] and installing its default `Scale.Steps` implementation,
/// - drawing axis decorations in the standard order of axis line, ticks, labels, annotations, and
///   title,
/// - skipping minor ticks when category labels already occupy the in-between step positions, and
/// - filtering annotations to the current visible axis range before they are painted.
///
/// Unlike [RectangularScaleConfiguration], this base keeps no additional cache state of its own.
/// Its `invalidate`, `ensureUpToDate`, `requireUpToDate`, and `isUpToDate` hooks therefore
/// default to no-op or always-up-to-date behavior until a subclass adds stricter geometry
/// requirements.
abstract class DefaultScaleConfiguration extends ScaleConfiguration {
    protected Scale scale;

    DefaultScaleConfiguration() {
    }

    @Override
    abstract Axis.Crossing getAutoCrossing();

    @Override
    int getTickSize(boolean majorTick) {
        return majorTick ? scale.getMajorTickSize() : scale.getMinorTickSize();
    }

    @Override
    abstract double getAxisAngle(double value);

    /// Paints only the annotations whose values still fall inside the current visible axis range.
    void drawAnnotations(Graphics g) {
        List<ScaleAnnotation> annotations = scale.getAttachedAnnotations();
        if (annotations == null)
            return;

        DataInterval visibleRange = scale.getAxis().getVisibleRange();
        for (ScaleAnnotation annotation : annotations) {
            if (visibleRange.isInside(annotation.getValue()))
                annotation.draw(g);
        }
    }

    @Override
    abstract int estimateVisibleItemCount(int width, int height, int spacing);

    /// Leaves the collected bounds unchanged.
    ///
    /// Subclasses override this when the rendered scale is translated or otherwise adjusted after
    /// the base bounds pass.
    @Override
    void adjustBounds(Rectangle2D bounds) {
    }

    /// Attaches this configuration to `scale` and installs the default step-cache implementation.
    ///
    /// Subclasses that need a specialized `Scale.Steps` variant can override [#createSteps()].
    @Override
    void setScale(Scale scale) {
        this.scale = scale;
        if (scale != null)
            scale.setSteps(createSteps());
    }

    /// Does nothing because this base class keeps no independent cache state.
    @Override
    void invalidate() {
    }

    /// Returns `true` because this base class keeps no independent cache state.
    @Override
    boolean isUpToDate() {
        return true;
    }

    /// Places the title along the current visible axis interval and offsets it away from the axis.
    @Override
    protected void computeTitleLocation(DoublePoint titleAnchor) {
        DataInterval visibleRange = scale.getAxis().getVisibleRange();
        double titleValue = visibleRange.getMin() + visibleRange.getLength() * scale.getTitleState().getPlacement() / 100.0;
        scale.computeValueLocation(titleValue, titleAnchor);
        Dimension2D titleSize = scale.getTitleState().getSize2D(true);
        GraphicUtil.computeTextLocation(titleAnchor, getAxisAngle(titleValue), getTitleDistance(),
                titleSize.getWidth(), titleSize.getHeight());
    }

    /// Uses the scale's occupied bounds as the default hit region.
    ///
    /// More specialized configurations such as [CircularScaleConfiguration] override this when the
    /// interactive region should follow the rendered axis shape more closely than its bounding box.
    @Override
    public boolean contains(Point2D point) {
        return scale.getBounds(null).contains(point);
    }

    /// Creates the ordinary `Scale.Steps` implementation for the attached scale.
    protected Scale.Steps createSteps() {
        Scale attachedScale = Objects.requireNonNull(scale, "scale");
        return attachedScale.new Steps();
    }

    /// Does nothing because this base class keeps no independent cache state to verify.
    @Override
    void requireUpToDate() {
    }

    /// Paints the shared scale decorations in the standard order.
    ///
    /// Minor ticks are omitted when the current category-step layout already uses the in-between
    /// positions for labels. Linked parallel scales are drawn after the current scale so stacked
    /// y-axis groups can reuse the same traversal order.
    @Override
    protected void draw(Graphics g) {
        scale.prepare();
        if (scale.isAxisVisible())
            drawAxis(g);
        if (scale.isMajorTickVisible())
            drawTicks(g, true);
        if (scale.isMinorTickVisible()
                && !scale.areCategoryStepsBetweenLabels()
                && !scale.areCategoryLabelsBetweenSteps())
            drawTicks(g, false);
        if (scale.isLabelVisible())
            drawLabels(g);
        drawAnnotations(g);
        if (scale.hasTitle())
            scale.getTitleState().draw(g);

        if (scale.shouldDrawLinkedScale())
            scale.getNextParallelScale().draw(g);
    }

    protected abstract void drawAxis(Graphics g);

    /// Draws prepared labels using the current skip strategy.
    ///
    /// When adaptive label skipping is active, labels whose display bounds intersect the most
    /// recently accepted label are dropped.
    protected void drawLabels(Graphics g) {
        DoublePoints labelPoints = scale.getDisplayedLabelPoints();
        int labelCount = labelPoints.size();
        if (labelCount == 0)
            return;

        int labelDistance = scale.getLabelDistance();
        int skipStride = scale.computeSkipStride(labelPoints, labelDistance);
        double[] labelValues = scale.getSteps().getDisplayedValues().data();
        Color originalColor = g.getColor();
        Font originalFont = g.getFont();
        try {
            Color labelColor = scale.getLabelColor();
            DoublePoint labelAnchor = new DoublePoint();
            Rectangle2D currentBounds = null;
            Rectangle2D previousBounds = null;
            Rectangle clipBounds = g.getClipBounds();
            boolean adaptiveSkip = scale.isSkippingLabel() && scale.getSkipLabelMode() == Scale.ADAPTIVE_SKIP;
            boolean shouldCheckBounds = adaptiveSkip || clipBounds != null;
            for (int labelIndex = 0; labelIndex < labelCount; labelIndex += skipStride) {
                labelAnchor.setLocation(labelPoints.getX(labelIndex), labelPoints.getY(labelIndex));
                Rectangle2D labelBounds = scale.getPreparedLabelBounds(labelIndex);
                double labelWidth = labelBounds.getWidth();
                double labelHeight = labelBounds.getHeight();
                labelAnchor = scale.computeLabelLocation(labelAnchor, getAxisAngle(labelValues[labelIndex]),
                        labelDistance, labelWidth, labelHeight);
                if (shouldCheckBounds) {
                    currentBounds = Scale.updateCenteredRect(labelAnchor, labelWidth, labelHeight, currentBounds);
                    if (adaptiveSkip) {
                        if (previousBounds == null) {
                            previousBounds = currentBounds;
                            currentBounds = null;
                        } else {
                            if (previousBounds.intersects(currentBounds))
                                continue;
                            Rectangle2D swap = previousBounds;
                            previousBounds = currentBounds;
                            currentBounds = swap;
                        }
                    }
                    if (clipBounds != null && previousBounds != null && !clipBounds.intersects(previousBounds))
                        continue;
                }
                scale.getSteps().getLabelRenderer(labelIndex).draw(g, labelColor, null, labelAnchor.x, labelAnchor.y);
            }
        } finally {
            g.setColor(originalColor);
            g.setFont(originalFont);
        }
    }

    /// Draws either the prepared major or prepared minor tick sequence.
    protected void drawTicks(Graphics g, boolean majorTicks) {
        DoublePoints tickPoints = majorTicks ? scale.getMajorTickPoints() : scale.getMinorTickPoints();
        int tickCount = tickPoints.size();
        if (tickCount == 0)
            return;

        double[] tickValues = majorTicks ? scale.getSteps().getMajorValues().data() : scale.getSteps().getSubStepValues().data();
        int tickSize = getTickSize(majorTicks);
        int[] tickLine = new int[4];
        scale.getAxisStyle().applyStroke(g);
        for (int tickIndex = 0; tickIndex < tickCount; tickIndex++) {
            scale.computeTickLine(tickPoints.getX(tickIndex), tickPoints.getY(tickIndex), tickSize,
                    scale.getAxisAngle(tickValues[tickIndex]), tickLine);
            g.drawLine(tickLine[0], tickLine[1], tickLine[2], tickLine[3]);
        }
        scale.getAxisStyle().restoreStroke(g);
    }

    /// Does nothing because this base class keeps no independent cache state to prepare.
    @Override
    void ensureUpToDate() {
    }

    @Override
    abstract Rectangle2D getAxisBounds(Rectangle2D bounds);

    /// Returns a conservative axis-length estimate based on the smaller plot-rectangle dimension.
    ///
    /// Straight and circular subclasses override this when they can derive a more precise rendered
    /// length from their cached geometry.
    @Override
    protected int getScaleLength() {
        return Math.min(scale.getPlotRect().width, scale.getPlotRect().height);
    }

    /// Returns the distance used to offset the title away from the rendered axis.
    abstract int getTitleDistance();
}
