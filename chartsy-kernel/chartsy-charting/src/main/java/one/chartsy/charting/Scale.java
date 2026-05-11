package one.chartsy.charting;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.internal.PolarProjector;
import one.chartsy.charting.internal.TextRenderer;
import one.chartsy.charting.util.DoubleArray;
import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.MathUtil;
import one.chartsy.charting.util.text.BidiUtil;

/// Renders and measures one chart axis together with its ticks, labels, title, and annotations.
///
/// `Scale` is the drawable companion to an [Axis]. The axis owns numeric range and transformation,
/// while the scale turns that model into presentation state such as major and minor ticks,
/// formatted labels, title placement, annotation placement, and value-to-pixel conversion.
///
/// Once attached through [Chart#setXScale(Scale)] or [Chart#setYScale(int, Scale)], the scale
/// maintains lazy caches of prepared tick values, display points, measured labels, and occupied
/// bounds. Methods such as [#getStepValues()] and [#getSubStepValues()] expose those live caches
/// directly. Callers that need a stable immutable view should prefer [#snapshotVisibleTicks()],
/// [#snapshotPreparedTicks()], or [#snapshotDisplayedTicks()].
///
/// The type is mutable and intended for the owning chart's update and paint thread. Rebuilding
/// chart scales invalidates the prepared caches, so external code should not retain live arrays or
/// derived geometry across layout, data, or axis updates.
///
/// Subclasses typically customize [#computeLabel(double)],
/// [#computeLabelLocation(DoublePoint, double, int, double, double)], and
/// [#updateAnnotations()] rather than replacing the full scale pipeline.
public class Scale extends ChartOwnedDrawable implements Serializable {
    /// Immutable snapshot of one prepared tick on this scale.
    ///
    /// The record carries the raw axis value, the already formatted label text, and the chart-area
    /// display coordinates used to place that tick's label and tick mark.
    ///
    /// @param value    the axis value represented by the tick
    /// @param label    the formatted label text prepared for display
    /// @param displayX the x coordinate in chart-area display space
    /// @param displayY the y coordinate in chart-area display space
    public record VisibleTick(double value, String label, int displayX, int displayY) {
    }

    /// Axis listener that keeps this scale's prepared caches aligned with its owning [Axis].
    ///
    /// The scale installs one listener while it is attached to an axis. Property changes
    /// invalidate cached bounds immediately, and visible-range changes also invalidate the
    /// prepared step cache so ticks and labels are regenerated from the new range before the next
    /// paint.
    class ScaleAxisListener implements AxisListener, Serializable {

        ScaleAxisListener() {
        }

        /// Invalidates scale geometry after axis-property changes.
        @Override
        public void axisChanged(AxisChangeEvent event) {
            if (event.getType() != 1) {
                if (event.getType() == 2)
                    getSteps().invalidateValues();
                Scale.this.invalidateCrossingDependentScales();
            }
            if (!event.isAdjusting())
                invalidateLayout();
        }

        /// Invalidates prepared values after visible-range changes.
        @Override
        public void axisRangeChanged(AxisRangeEvent event) {
            if (event.isVisibleRangeEvent())
                if (event.isChangedEvent()) {
                    Scale.this.invalidateCrossingDependentScales();
                    getSteps().invalidateValues();
                    if (!event.isAdjusting())
                        invalidateLayout();
                }
        }
    }

    /// Step-cache specialization for circular scales.
    ///
    /// Closed circular axes wrap back onto their starting angle. This variant removes the terminal
    /// major tick when it coincides with the visible maximum so the seam does not display the same
    /// logical position twice.
    class CircularSteps extends Scale.Steps {

        CircularSteps() {
            super();
        }

        /// Trims the duplicated seam tick after the default step sequence is generated.
        @Override
        protected void computeValues() {
            super.computeValues();
            int majorStepCount = super.majorValues.size();
            if (majorStepCount > 1) {
                double lastMajorValue = super.majorValues.get(majorStepCount - 1);
                if (lastMajorValue == getAxis().getVisibleMax())
                    super.majorValues.remove(majorStepCount - 1, 1);
            }
        }
    }

    /// Step-cache specialization for radial scales in polar and radar charts.
    ///
    /// Radial axes keep the ordinary step-generation algorithm from [Steps] but can present labels
    /// differently. On symmetric polar projectors the same distance from the origin may appear as
    /// both a positive and negative value; this variant formats both sides from the absolute
    /// distance so opposite radii share the same label text.
    class RadialSteps extends Scale.Steps {

        RadialSteps() {
            super();
        }

        /// Formats prepared labels, normalizing symmetric polar values to their absolute magnitude.
        @Override
        protected String[] computeLabels() {
            if (!((PolarProjector) Scale.this.getProjector2D()).isSymmetric())
                return super.computeLabels();
            DoubleArray displayedValues =
                    (Scale.this.areCategoryStepsBetweenLabels()
                            != Scale.this.areCategoryLabelsBetweenSteps())
                            ? super.subStepValues
                            : super.majorValues;
            int labelCount = displayedValues.size();
            String[] computedLabels = (super.labels.length != labelCount) ? new String[labelCount] : super.labels;
            for (int labelIndex = 0; labelIndex < labelCount; labelIndex++)
                computedLabels[labelIndex] = Scale.this
                        .formatLabel(Math.abs(displayedValues.get(labelIndex)));
            return computedLabels;
        }
    }

    /// Mutable preparation cache for one scale's ticks, projected points, and measured labels.
    ///
    /// The owning [Scale] rebuilds this state lazily in three stages:
    /// 1. generate major and sub-step values from the current [StepsDefinition]
    /// 2. project those values into display-space points through the current [ChartProjector]
    /// 3. format labels and prepare [TextRenderer] instances when labels are visible
    ///
    /// Accessors validate that the owning chart still reports up-to-date scales and throw
    /// [ConcurrentModificationException] when callers attempt to reuse stale prepared data after chart
    /// invalidation. The returned arrays, point buffers, and label renderer instances remain
    /// owned by the scale and are refreshed in place rather than copied for each caller.
    ///
    /// Subclasses override [#computeValues()] to customize value generation or [#computeLabels()] to customize
    /// label text while keeping the rest of the preparation pipeline unchanged.
    class Steps implements Serializable {
        protected transient DoubleArray majorValues;
        protected transient DoubleArray subStepValues;
        private transient boolean valuesPrepared;
        protected transient DoublePoints majorPoints;
        protected transient DoublePoints subStepPoints;
        private transient boolean pointsPrepared;
        protected transient String[] labels;
        private transient boolean labelsPrepared;
        private transient TextRenderer[] labelRenderers;
        private transient double maxLabelWidth;
        private transient double maxLabelHeight;
        private transient boolean antiAliasedText;
        private transient Font preparedLabelFont;

        Steps() {
            initializeTransientState();
        }

        /// Populates the major and sub-step value buffers for the current visible range.
        ///
        /// The default implementation delegates to [Scale#computeSteps(DoubleArray, DoubleArray)].
        /// Specializations can post-process the generated sequences without reimplementing point or
        /// label preparation.
        protected void computeValues() {
            Scale.this.computeSteps(majorValues, subStepValues);
        }

        /// Reapplies renderer presentation settings and recomputes aggregate label bounds.
        ///
        /// A metrics refresh is needed after label text changes and whenever the scale's effective
        /// label font, text anti-aliasing mode, or automatic wrapping width changes.
        private boolean refreshLabelMetrics(boolean forceRefresh) {
            boolean metricsChanged = false;
            Font labelFont = getLabelFont();
            boolean textAntiAliased = Scale.this.isTextAntiAliased();
            boolean antiAliasingChanged = forceRefresh || antiAliasedText != textAntiAliased;
            boolean fontChanged = forceRefresh || preparedLabelFont == null || !preparedLabelFont.equals(labelFont);

            if (antiAliasingChanged || fontChanged) {
                antiAliasedText = textAntiAliased;
                preparedLabelFont = labelFont;
                double widestLabel = 0.0;
                double tallestLabel = 0.0;
                for (int labelIndex = labels.length - 1; labelIndex >= 0; labelIndex--) {
                    TextRenderer labelRenderer = labelRenderers[labelIndex];
                    if (antiAliasingChanged)
                        labelRenderer.setAntiAliased(textAntiAliased);
                    if (fontChanged)
                        labelRenderer.setFont(labelFont);
                    Rectangle2D labelBounds = labelRenderer.getBounds(true);
                    widestLabel = Math.max(widestLabel, labelBounds.getWidth());
                    tallestLabel = Math.max(tallestLabel, labelBounds.getHeight());
                }
                maxLabelWidth = widestLabel;
                maxLabelHeight = tallestLabel;
                metricsChanged = true;
            }

            if (isAutoWrapping()) {
                int labelCount = labels.length;
                if (labelCount > 0) {
                    if (labelCount > 1)
                        labelCount--;
                    float wrappingWidth = Scale.this.getScaleLength() / labelCount;
                    wrappingWidth = Math.max(6.0f, wrappingWidth - 4.0f);
                    metricsChanged |= wrappingWidth != labelRenderers[0].getWrappingWidth();
                    setWrappingWidth(wrappingWidth);
                }
            }
            return metricsChanged;
        }

        void setLabelRotation(double rotation) {
            for (int index = labelRenderers.length - 1; index >= 0; index--)
                labelRenderers[index].setRotation(rotation);
        }

        void setWrappingWidth(float wrappingWidth) {
            for (int index = labelRenderers.length - 1; index >= 0; index--)
                labelRenderers[index].setWrappingWidth(wrappingWidth);
            Scale.this.invalidateBoundsCache();
        }

        final TextRenderer getLabelRenderer(int index) {
            return labelRenderers[index];
        }

        private TextRenderer prepareLabelRenderer(String text, TextRenderer existingRenderer) {
            TextRenderer labelRenderer = existingRenderer;
            if (labelRenderer != null)
                labelRenderer.setText(text);
            else
                labelRenderer = new TextRenderer(text);
            labelRenderer.setAutoWrapping(isAutoWrapping());
            labelRenderer.setRotation(getLabelRotation());
            labelRenderer.setAlignment(getLabelAlignment());
            return labelRenderer;
        }

        /// Ensures the cache layers needed for the current layout pass are ready to paint.
        ///
        /// If the owning chart has already marked its scales stale, this method delegates back to
        /// [Chart#updateScales()] instead of consuming the invalid cache locally. Otherwise it
        /// rebuilds only the missing preparation stages and reuses existing label renderers when
        /// their text remains valid.
        ///
        /// @return `true` when any cached values, points, labels, or text metrics changed
        boolean prepare() {
            if (!getChart().scalesUpToDate) {
                getChart().updateScales();
                return true;
            }
            boolean cacheChanged = false;
            if (!hasPreparedValues()) {
                prepareValues();
                preparePoints();
                if (needsLabelPreparation()) {
                    prepareLabels();
                    prepareLabelRenderers();
                } else
                    clearLabelPreparation();
                cacheChanged = true;
            } else {
                if (!hasPreparedPoints()) {
                    preparePoints();
                    cacheChanged = true;
                }
                if (needsLabelPreparation()) {
                    if (!hasPreparedLabels()) {
                        prepareLabels();
                        prepareLabelRenderers();
                        cacheChanged = true;
                    } else
                        cacheChanged |= refreshLabelMetrics(false);
                } else if (!hasPreparedLabels() || labelRenderers.length != 0 || maxLabelWidth != 0.0 || maxLabelHeight != 0.0) {
                    clearLabelPreparation();
                    cacheChanged = true;
                }
            }
            if (cacheChanged)
                Scale.this.invalidateBoundsCache();
            return cacheChanged;
        }

        /// Recreates the transient caches that are intentionally excluded from serialization.
        private void initializeTransientState() {
            majorValues = new DoubleArray(0);
            subStepValues = new DoubleArray(0);
            labelRenderers = new TextRenderer[0];
            labels = new String[0];
        }

        /// Regenerates tick values without yet projecting them or formatting labels.
        final void prepareValues() {
            computeValues();
            valuesPrepared = true;
        }

        void setAutoWrapping(boolean autoWrapping) {
            for (int index = labelRenderers.length - 1; index >= 0; index--)
                labelRenderers[index].setAutoWrapping(autoWrapping);
        }

        void setLabelAlignment(int alignment) {
            for (int index = labelRenderers.length - 1; index >= 0; index--)
                labelRenderers[index].setAlignment(alignment);
        }

        /// Rejects callers that try to read value caches after the chart has invalidated them.
        final void requireValues() {
            if (valuesPrepared && getChart().scalesUpToDate)
                return;
            throw new ConcurrentModificationException(
                    "missing updateAll() for values - probably a multithreading problem");
        }

        /// Formats the values currently exposed as displayed labels.
        ///
        /// Category scales may expose sub-step values as the displayed label sequence when labels
        /// live between major category boundaries.
        protected String[] computeLabels() {
            DoubleArray displayedValues = (Scale.this.areCategoryStepsBetweenLabels()
                    != Scale.this.areCategoryLabelsBetweenSteps()) ? subStepValues : majorValues;
            int labelCount = displayedValues.size();
            String[] computedLabels = (labels.length != labelCount) ? new String[labelCount] : labels;
            for (int labelIndex = 0; labelIndex < labelCount; labelIndex++)
                computedLabels[labelIndex] = Scale.this.formatLabel(displayedValues.get(labelIndex));
            return computedLabels;
        }

        final DoubleArray getMajorValues() {
            requireValues();
            return majorValues;
        }

        final DoubleArray getSubStepValues() {
            requireValues();
            return subStepValues;
        }

        /// Returns the live value sequence currently used for labels and displayed ticks.
        ///
        /// Category layouts that place labels between major category boundaries use the prepared
        /// sub-step sequence instead of the major-step sequence.
        final DoubleArray getDisplayedValues() {
            requireValues();
            return (Scale.this.areCategoryStepsBetweenLabels()
                    != Scale.this.areCategoryLabelsBetweenSteps())
                    ? getSubStepValues()
                    : getMajorValues();
        }

        final boolean hasPreparedValues() {
            return valuesPrepared && getChart().scalesUpToDate;
        }

        void invalidateValues() {
            valuesPrepared = false;
        }

        /// Projects prepared values into display-space points while preserving the source arrays.
        ///
        /// [ChartProjector] mutates the supplied point buffer, so projection operates on clones of
        /// the prepared value arrays to keep the raw tick positions available for later formatting
        /// and hit testing.
        private void computePoints() {
            majorPoints = Scale.this.projectValues(majorValues.data().clone(), majorValues.size());
            subStepPoints = Scale.this.projectValues(subStepValues.data().clone(), subStepValues.size());
        }

        /// Regenerates the projected tick points from the already prepared value sequences.
        final void preparePoints() {
            computePoints();
            pointsPrepared = true;
        }

        /// Rejects callers that try to reuse point caches after scale invalidation.
        final void requirePoints() {
            if (valuesPrepared && pointsPrepared && getChart().scalesUpToDate)
                return;
            throw new ConcurrentModificationException(
                    "missing updateAll() for points - probably a multithreading problem");
        }

        final DoublePoints getMajorPoints() {
            requirePoints();
            return majorPoints;
        }

        final DoublePoints getSubStepPoints() {
            requirePoints();
            return subStepPoints;
        }

        /// Returns the live point sequence currently used for labels and displayed ticks.
        final DoublePoints getDisplayedPoints() {
            requirePoints();
            return (Scale.this.areCategoryStepsBetweenLabels()
                    != Scale.this.areCategoryLabelsBetweenSteps())
                    ? subStepPoints
                    : majorPoints;
        }

        final boolean hasPreparedPoints() {
            return pointsPrepared
                    && !Scale.this.requiresDynamicPointPreparation()
                    && getChart().scalesUpToDate;
        }

        void invalidatePoints() {
            pointsPrepared = false;
        }

        /// Regenerates the formatted label text for the current displayed value sequence.
        final void prepareLabels() {
            labels = computeLabels();
            labelsPrepared = true;
        }

        /// Clears prepared label state when labels are hidden or no longer needed.
        private void clearLabelPreparation() {
            labels = new String[0];
            labelRenderers = new TextRenderer[0];
            maxLabelWidth = 0.0;
            maxLabelHeight = 0.0;
            antiAliasedText = Scale.this.isTextAntiAliased();
            preparedLabelFont = getLabelFont();
            labelsPrepared = true;
        }

        /// Returns whether this scale currently needs formatted labels and label renderers.
        private boolean needsLabelPreparation() {
            return isLabelVisible();
        }

        /// Rejects callers that try to reuse prepared label state after invalidation.
        final void requireLabels() {
            if (valuesPrepared && labelsPrepared && getChart().scalesUpToDate)
                return;
            throw new ConcurrentModificationException(
                    "missing updateAll() for labels - probably a multithreading problem"
                            + " [axis=" + ((getAxis() == null) ? "null" : (getAxis().isXAxis() ? "X" : "Y"))
                            + ", visibleRange=" + ((getAxis() == null) ? "null" : getAxis().getVisibleRange())
                            + ", stepsDefinition=" + ((getStepsDefinition() == null) ? "null" : getStepsDefinition().getClass().getName())
                            + ", chart=" + getChart()
                            + "]");
        }

        /// Restores the empty transient caches that back later preparation passes.
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            input.defaultReadObject();
            initializeTransientState();
        }

        final String[] getLabels() {
            requireLabels();
            return labels;
        }

        final boolean hasPreparedLabels() {
            return labelsPrepared && getChart().scalesUpToDate;
        }

        void invalidateLabels() {
            labelsPrepared = false;
        }

        /// Rebuilds label renderers for the current prepared labels and updates their metrics.
        ///
        /// Existing renderers are reused when the label count still matches so repeated scale
        /// preparation can update text and styling without unnecessary allocation churn.
        void prepareLabelRenderers() {
            requireLabels();
            String[] preparedLabels = getLabels();
            int labelCount = preparedLabels.length;
            int reusableCount = Math.min(labelRenderers.length, labelCount);
            TextRenderer[] updatedRenderers = (labelCount != reusableCount)
                    ? new TextRenderer[labelCount]
                    : labelRenderers;
            int labelIndex = 0;
            while (labelIndex < reusableCount) {
                updatedRenderers[labelIndex] = prepareLabelRenderer(preparedLabels[labelIndex], labelRenderers[labelIndex]);
                labelIndex++;
            }
            while (labelIndex < labelCount) {
                updatedRenderers[labelIndex] = prepareLabelRenderer(preparedLabels[labelIndex], null);
                labelIndex++;
            }
            labelRenderers = updatedRenderers;
            refreshLabelMetrics(true);
        }

        /// Returns the widest prepared label in display-space units.
        ///
        /// Scale configurations use this after [#prepare()] to reserve enough room for labels and
        /// titles around the axis.
        final double getMaxLabelWidth() {
            return maxLabelWidth;
        }

        /// Returns the tallest prepared label in display-space units.
        final double getMaxLabelHeight() {
            return maxLabelHeight;
        }

        /// Invalidates the first cache stage, which forces all dependent stages to rebuild later.
        ///
        /// Points and labels are derived entirely from the prepared value buffers, so clearing the
        /// value-prepared flag is enough to make the next [#prepare()] pass regenerate all three
        /// stages.
        void invalidateAll() {
            invalidateValues();
        }

        /// Returns whether values, points, and labels are all currently ready to paint.
        boolean isPrepared() {
            return hasPreparedValues() && hasPreparedPoints() && hasPreparedLabels();
        }
    }

    /// Mutable title state owned lazily by one [Scale].
    ///
    /// The title keeps the source text, its bidi-composed display text, placement percentage along
    /// the axis, one cached anchor point in display space, and the live [LabelRenderer] used to
    /// measure and paint the title. It is prepared as part of [Scale#prepare()] and becomes stale
    /// whenever scale geometry, bidi context, or renderer presentation changes.
    final class Title implements Serializable {
        /// Title-specific renderer that routes presentation changes back through scale invalidation.
        class TitleLabelRenderer extends LabelRenderer {
            TitleLabelRenderer() {
            }

            /// Forwards renderer changes to the owning scale's layout invalidation path.
            @Override
            public void stateChanged() {
                invalidateLayout();
            }
        }

        transient DoublePoint anchorPoint;
        private transient boolean prepared;
        int placement;
        String text;
        String displayText;

        LabelRenderer labelRenderer;

        /// Creates title state for one scale.
        ///
        /// `text` stores the source title string exactly as supplied by callers. The renderer
        /// rotation is configured immediately so later measurement and painting share the same
        /// geometry assumptions.
        ///
        /// @param text     the initial title text, or `null` for no title
        /// @param rotation the initial title rotation in degrees
        public Title(String text, double rotation) {
            placement = 50;
            this.text = text;
            updateDisplayText();
            labelRenderer = new TitleLabelRenderer();
            labelRenderer.setRotation(rotation);
            initializeTransientState();
        }

        /// Paints the prepared title through the owning chart area.
        void draw(Graphics g) {
            DoublePoint anchorPoint = getAnchorPoint();
            labelRenderer.paintLabel(getChart().getChartArea(), g, displayText, anchorPoint.xFloor(), anchorPoint.yFloor());
        }

        /// Returns the prepared title bounds in chart-area display space.
        Rectangle2D getBounds(Rectangle2D bounds) {
            requirePrepared();
            DoublePoint anchorPoint = getAnchorPoint();
            return labelRenderer.getBounds(getChart().getChartArea(), anchorPoint.xFloor(), anchorPoint.yFloor(),
                    displayText, bounds);
        }

        /// Recomputes the cached anchor by asking the active scale configuration to place the title.
        private void computeAnchorPoint() {
            if (text != null)
                Scale.this.computeTitleLocation(anchorPoint);
        }

        /// Refreshes the cached title anchor for the current scale geometry.
        void prepare() {
            computeAnchorPoint();
            prepared = true;
        }

        /// Rejects callers that try to reuse the cached title anchor after scale invalidation.
        private void requirePrepared() {
            if (prepared && getChart().scalesUpToDate)
                return;
            throw new ConcurrentModificationException("missing updateAll() in title - probably a multithreading problem");
        }

        /// Returns the live cached title anchor in chart-area display space.
        ///
        /// The returned point is owned by this title state and refreshed in place during later
        /// preparation passes.
        DoublePoint getAnchorPoint() {
            requirePrepared();
            return anchorPoint;
        }

        /// Marks the cached title anchor stale until the next preparation pass.
        void invalidate() {
            prepared = false;
        }

        /// Returns the live renderer used for title measurement and painting.
        public LabelRenderer getLabelRenderer() {
            return labelRenderer;
        }

        /// Returns title placement as a percentage along the axis line.
        public int getPlacement() {
            return placement;
        }

        /// Returns the current title footprint measured against the owning chart area.
        ///
        /// @param rotatedBounds whether to account for the renderer's current rotation
        public Dimension2D getSize2D(boolean rotatedBounds) {
            return labelRenderer.getSize2D(getChart().getChartArea(), displayText, true, rotatedBounds);
        }

        /// Returns the original title text before bidi composition.
        public String getText() {
            return text;
        }

        boolean isPrepared() {
            return prepared && getChart().scalesUpToDate;
        }

        Shape getBackgroundShape() {
            requirePrepared();
            DoublePoint anchorPoint = getAnchorPoint();
            return labelRenderer.getBackgroundShape(getChart().getChartArea(), anchorPoint.xFloor(),
                    anchorPoint.yFloor(), displayText);
        }

        /// Rebuilds the bidi-composed display string after text-direction-sensitive changes.
        void invalidateDisplayText() {
            updateDisplayText();
        }

        /// Recomputes the display string resolved against the chart's current bidi context.
        private void updateDisplayText() {
            displayText = BidiUtil.getCombinedString(text, getResolvedBaseTextDirection(), getComponentOrientation(), false);
        }

        /// Restores transient anchor storage excluded from serialization.
        private void initializeTransientState() {
            anchorPoint = new DoublePoint();
        }

        /// Recreates transient anchor storage after deserialization.
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            input.defaultReadObject();
            initializeTransientState();
        }

        /// Sets title placement as a percentage along the axis line.
        ///
        /// Values outside the `[0, 100]` range are clamped before the title is invalidated.
        public void setPlacement(int placement) {
            int clampedPlacement = placement;
            if (clampedPlacement > 100)
                clampedPlacement = 100;
            else if (clampedPlacement < 0)
                clampedPlacement = 0;
            if (clampedPlacement != this.placement) {
                this.placement = clampedPlacement;
                stateChanged();
            }
        }

        /// Replaces the title text and rebuilds the cached bidi-composed display string.
        public void setText(String text) {
            if (Objects.equals(text, this.text))
                return;
            this.text = text;
            updateDisplayText();
            stateChanged();
        }

        /// Invalidates scale layout after title state or title-renderer changes.
        private void stateChanged() {
            invalidateLayout();
        }

    }

    /// Tick-layout constant that draws tick marks toward the plot interior.
    public static final int TICK_INSIDE = 1;
    /// Tick-layout constant that draws tick marks away from the plot interior.
    public static final int TICK_OUTSIDE = 2;
    /// Tick-layout constant that centers tick marks on the axis line.
    public static final int TICK_CROSS = 3;
    /// Label-skipping mode that applies one fixed skip stride across the entire axis.
    public static final int CONSTANT_SKIP = 1;
    /// Label-skipping mode that lets the active configuration suppress labels adaptively.
    public static final int ADAPTIVE_SKIP = 2;
    /// Cartesian-side constant for the lower x-axis or left y-axis position.
    public static final int LOWER_SIDE = -1;
    /// Cartesian-side constant for the upper x-axis or right y-axis position.
    public static final int UPPER_SIDE = 1;
    /// Radial-side constant for labels and ticks on the left side of a radial axis.
    public static final int LEFT_SIDE = 1;
    /// Radial-side constant for labels and ticks on the right side of a radial axis.
    public static final int RIGHT_SIDE = 2;
    /// Circular-side constant that places the circular scale outside the circle.
    public static final int OUTSIDE = 1;
    /// Circular-side constant that places the circular scale inside the circle.
    public static final int INSIDE = 2;

    static Rectangle2D updateCenteredRect(DoublePoint center, double width, double height, Rectangle2D bounds) {
        Rectangle2D updatedBounds = bounds;
        if (updatedBounds != null)
            updatedBounds.setRect(center.x - width / 2.0, center.y - height / 2.0, width, height);
        else
            updatedBounds = new Rectangle2D.Double(center.x - width / 2.0, center.y - height / 2.0, width, height);
        return updatedBounds;
    }

    /// Returns the owning chart's axis index for `scale`.
    ///
    /// X-axis scales use index `0`; y-axis scales use the owning axis-element index inside the
    /// chart.
    ///
    /// @param scale the attached scale whose owning axis index should be returned
    /// @return the chart axis-element index for `scale`
    /// @throws IllegalArgumentException if `scale` is not currently attached to a chart
    public static int getAxisIndex(Scale scale) {
        if (scale.axisElement != null)
            return scale.axisElement.getAxisIndex();
        throw new IllegalArgumentException("scale not connected to a chart.");
    }

    Flags r;
    private int s;
    private int t;
    private int u;
    private int v;
    private int w;
    private int x;
    private int y;
    private int skipLabelMode;
    private double labelRotation;
    private boolean ab;
    private Chart.AxisElement axisElement;
    private AxisListener ad;
    private Axis.Crossing ae;
    private int af;
    private int ag;
    private int ah;
    transient Rectangle plotRect;
    transient Rectangle cachedBounds;
    Scale.Steps steps;
    StepsDefinition stepsDefinition;
    transient boolean am;
    private PlotStyle an;
    private Color ao;
    private Scale.Title title;
    private List<ScaleAnnotation> annotations;
    private ValueFormatter labelFormatter;

    private Font labelFont;

    private Scale previousParallelScale;

    private Scale nextParallelScale;

    private boolean explicitParallelLink;

    private ScaleConfiguration scaleConfiguration;

    /// Creates a numeric scale with automatically chosen major and minor step units.
    public Scale() {
        this(0.0, 0.0);
    }

    /// Creates a numeric scale with explicit or automatic step units.
    ///
    /// A `0.0` argument is treated as "automatic" for the corresponding unit so the constructor
    /// can preserve the historical API shape used throughout the charting module.
    ///
    /// @param majorStepUnit the major-step size, or `0.0` to choose it automatically
    /// @param subStepUnit   the minor-step size, or `0.0` to choose it automatically
    public Scale(double majorStepUnit, double subStepUnit) {
        r = new Flags(63);
        s = 100;
        t = 6;
        u = 3;
        v = 3;
        w = 3;
        x = 0;
        y = 2;
        skipLabelMode = 1;
        labelRotation = 0.0;
        ag = 1;
        ah = 1;
        scaleConfiguration = ScaleConfiguration.forChartType(1);
        scaleConfiguration.setScale(this);
        Double majorStep = (majorStepUnit == 0.0) ? null : Double.valueOf(majorStepUnit);
        Double subStep = (subStepUnit == 0.0) ? null : Double.valueOf(subStepUnit);
        setStepUnit(majorStep, subStep);
        initializeTransientState();
    }

    /// Creates a time scale backed by [TimeStepsDefinition].
    ///
    /// @param unit the fixed time unit to use, or `null` to keep time-unit selection automatic
    public Scale(TimeUnit unit) {
        r = new Flags(63);
        s = 100;
        t = 6;
        u = 3;
        v = 3;
        w = 3;
        x = 0;
        y = 2;
        skipLabelMode = 1;
        labelRotation = 0.0;
        ag = 1;
        ah = 1;
        scaleConfiguration = ScaleConfiguration.forChartType(1);
        scaleConfiguration.setScale(this);
        setTimeUnit(unit);
        initializeTransientState();
    }

    final ScaleConfiguration getScaleConfiguration() {
        return scaleConfiguration;
    }

    private void setCrossingInternal(Axis.Crossing crossing) {
        ae = crossing;
        scaleConfiguration.invalidate();
        getSteps().invalidatePoints();
        invalidateLayout();
    }

    void updateAutoTitleRotation(boolean autoScaleTitleRotation, boolean invalidateLayout) {
        if (autoScaleTitleRotation) {
            double rotation = getAxis().isXAxis() ? 0.0 : -90.0;
            if (getChart() != null && getChart().isProjectorReversed())
                rotation = (rotation != 0.0) ? 0.0 : -90.0;
            getOrCreateTitleState().getLabelRenderer().setRotation(rotation);
        }
        if (invalidateLayout)
            invalidateLayout();
    }

    /// Attaches this scale to `axisElement`, or detaches it when `axisElement` is `null`.
    ///
    /// Axis-listener wiring, auto title rotation, bidi-sensitive prepared state, and explicit
    /// linked-scale propagation are all updated together so the scale tree stays internally
    /// consistent after the owning chart swaps or removes one axis element.
    void setAxisElement(Chart.AxisElement axisElement) {
        if (axisElement != this.axisElement) {
            ComponentOrientation previousOrientation = getComponentOrientation();
            if (this.axisElement != null)
                this.axisElement.getAxis().removeAxisListener(getOrCreateAxisListener());
            this.axisElement = axisElement;
            if (this.axisElement != null) {
                this.axisElement.getAxis().addAxisListener(getOrCreateAxisListener());
                if (getChart().isAutoScaleTitleRotation())
                    updateAutoTitleRotation(true, false);
            }
            componentOrientationChanged(previousOrientation, getComponentOrientation());
            baseTextDirectionChanged();
        }
        if (shouldDrawLinkedScale())
            getNextParallelScale().setAxisElement(axisElement);
    }

    /// Returns the axis-direction angle, in degrees, for a specific axis value.
    ///
    /// The active [ScaleConfiguration] resolves the actual angle. Rectangular scales usually return
    /// a constant orientation, while circular or radial configurations derive the angle from the
    /// value being projected.
    final double getAxisAngle(double value) {
        return scaleConfiguration.getAxisAngle(value);
    }

    /// Populates `tickLine` with the display-space segment for one tick mark.
    ///
    /// The resulting array always contains `x1`, `y1`, `x2`, and `y2` in that order. Tick layout
    /// decides whether the tick extends outward, inward, or symmetrically around the anchor point.
    void computeTickLine(double anchorX, double anchorY, int tickSize, double axisAngle, int[] tickLine) {
        double cosine = MathUtil.cosDeg(axisAngle);
        double sine = MathUtil.sinDeg(axisAngle);
        if (getTickLayout() == 2) {
            tickLine[0] = GraphicUtil.toInt(anchorX);
            tickLine[1] = GraphicUtil.toInt(anchorY);
            tickLine[2] = GraphicUtil.toInt(anchorX + tickSize * cosine);
            tickLine[3] = GraphicUtil.toInt(anchorY - tickSize * sine);
        } else if (getTickLayout() == 1) {
            tickLine[0] = GraphicUtil.toInt(anchorX);
            tickLine[1] = GraphicUtil.toInt(anchorY);
            tickLine[2] = GraphicUtil.toInt(anchorX - tickSize * cosine);
            tickLine[3] = GraphicUtil.toInt(anchorY + tickSize * sine);
        } else {
            double xOffset = tickSize * cosine;
            double yOffset = -tickSize * sine;
            tickLine[0] = GraphicUtil.toInt(anchorX + xOffset);
            tickLine[1] = GraphicUtil.toInt(anchorY + yOffset);
            tickLine[2] = GraphicUtil.toInt(anchorX - xOffset);
            tickLine[3] = GraphicUtil.toInt(anchorY - yOffset);
        }
    }

    /// Projects one axis value onto the current crossing line in display space.
    ///
    /// The supplied `location` is overwritten in place. Temporary projection buffers are disposed
    /// before the method returns.
    void computeValueLocation(double value, DoublePoint location) {
        DoublePoints projectedValue = (!getAxis().isXAxis()) ? new DoublePoints(getCrossingValue(), value)
                : new DoublePoints(value, getCrossingValue());
        projectToDisplay(projectedValue);
        location.x = projectedValue.getX(0);
        location.y = projectedValue.getY(0);
        projectedValue.dispose();
    }

    /// Projects the first `valueCount` axis values into display-space points.
    ///
    /// The returned [DoublePoints] is newly allocated and remains owned by the caller, who must
    /// dispose it after use.
    DoublePoints projectValues(double[] values, int valueCount) {
        if (valueCount == 0)
            return new DoublePoints(0);
        DoublePoints projectedValues = createProjectionInput(values, valueCount);
        projectToDisplay(projectedValues);
        return projectedValues;
    }

    /// Populates the prepared major-step and sub-step arrays for the current visible range.
    ///
    /// Definitions that opt into [StepsDefinition#usesTransformedVisibleRange()] are iterated in
    /// transformed axis space, but the stored prepared values are mapped back through
    /// [AxisTransformer#inverse(double)] so the exposed step arrays stay in the axis' source
    /// coordinate system. Both output arrays are reset before generation starts.
    ///
    /// When the active transformer rejects one of the generated values, the partially prepared output is
    /// discarded and both arrays are left empty.
    void computeSteps(DoubleArray majorSteps, DoubleArray subSteps) {
        majorSteps.reset();
        subSteps.reset();

        StepsDefinition definition = getStepsDefinition();
        if (definition == null)
            return;

        definition.update();
        AxisTransformer transformer = getAxis().getTransformer();
        boolean useTransformedVisibleRange = transformer != null && definition.usesTransformedVisibleRange();
        DataInterval generationRange = useTransformedVisibleRange ? getAxis().getTVisibleRange() : getAxis().getVisibleRange();
        double rangeMin = generationRange.getMin();
        if (!definition.hasNext(rangeMin))
            return;

        try {
            double rangeMax = generationRange.getMax();
            if (!Double.isFinite(rangeMin) || !Double.isFinite(rangeMax) || rangeMin > rangeMax)
                return;

            if (!definition.hasSubStep()) {
                double nextMajorStep = definition.ceilingStep(rangeMin);
                while (nextMajorStep <= rangeMax) {
                    addPreparedStep(majorSteps, nextMajorStep, transformer, useTransformedVisibleRange);
                    if (!definition.hasNext(nextMajorStep))
                        return;

                    double previousMajorStep = nextMajorStep;
                    nextMajorStep = definition.incrementStep(nextMajorStep);
                    if (nextMajorStep == previousMajorStep)
                        return;
                }
                return;
            }

            int subStepOrdinal = 1;
            double nextSubStep = definition.ceilingSubStep(rangeMin);
            double nextMajorStep = definition.ceilingStep(rangeMin);
            if (nextMajorStep == nextSubStep) {
                nextSubStep = definition.incrementSubStep(nextSubStep);
                subStepOrdinal++;
            }
            while (isDistinctSubStep(nextSubStep, nextMajorStep, subStepOrdinal) && nextSubStep <= rangeMax) {
                addPreparedStep(subSteps, nextSubStep, transformer, useTransformedVisibleRange);

                double previousSubStep = nextSubStep;
                nextSubStep = definition.incrementSubStep(nextSubStep);
                if (nextSubStep == previousSubStep)
                    break;
                subStepOrdinal++;
            }

            while (nextMajorStep <= rangeMax) {
                addPreparedStep(majorSteps, nextMajorStep, transformer, useTransformedVisibleRange);

                nextSubStep = definition.ceilingSubStep(nextMajorStep);
                if (nextMajorStep == nextSubStep) {
                    nextSubStep = definition.incrementSubStep(nextSubStep);
                    subStepOrdinal++;
                }
                if (!definition.hasNext(nextMajorStep))
                    return;

                double previousMajorStep = nextMajorStep;
                nextMajorStep = definition.incrementStep(nextMajorStep);
                if (nextMajorStep == previousMajorStep)
                    return;

                while (isDistinctSubStep(nextSubStep, nextMajorStep, subStepOrdinal) && nextSubStep <= rangeMax) {
                    addPreparedStep(subSteps, nextSubStep, transformer, useTransformedVisibleRange);

                    double previousSubStep = nextSubStep;
                    nextSubStep = definition.incrementSubStep(nextSubStep);
                    if (nextSubStep == previousSubStep)
                        break;
                    subStepOrdinal++;
                }
            }
        } catch (AxisTransformerException ex) {
            ex.printStackTrace();
            majorSteps.reset();
            subSteps.reset();
        }
    }

    private void addPreparedStep(DoubleArray target, double stepValue, AxisTransformer transformer,
                                 boolean generatedInTransformedSpace) throws AxisTransformerException {
        target.add(generatedInTransformedSpace ? transformer.inverse(stepValue) : stepValue);
    }

    private static boolean isDistinctSubStep(double subStepValue, double nextMajorStep, int subStepOrdinal) {
        return subStepValue < nextMajorStep
                && !MathUtil.equalsWithinRelativeTolerance(subStepValue, nextMajorStep, subStepOrdinal * 1.0E-15);
    }

    private void computeTitleLocation(DoublePoint titleAnchor) {
        scaleConfiguration.computeTitleLocation(titleAnchor);
    }

    /// Returns the bounds for a label centered on `labelAnchor`, optionally rotated in place.
    ///
    /// The returned rectangle may reuse `reuseBounds` when no rotation is needed. Rotated cases can
    /// return a transformed replacement instance instead.
    Rectangle2D getRotatedLabelBounds(DoublePoint labelAnchor, int labelWidth, int labelHeight, int labelLayout,
                                      double rotationDegrees, Rectangle2D reuseBounds) {
        Rectangle2D bounds = reuseBounds;
        if (bounds != null)
            bounds.setRect(labelAnchor.x - labelWidth / 2.0, labelAnchor.y - labelHeight / 2.0, labelWidth, labelHeight);
        else
            bounds = new Rectangle2D.Double(labelAnchor.x - labelWidth / 2.0, labelAnchor.y - labelHeight / 2.0,
                    labelWidth, labelHeight);
        if (rotationDegrees != 0.0) {
            AffineTransform rotation = AffineTransform.getRotateInstance(MathUtil.toRadians(rotationDegrees),
                    labelAnchor.xFloor(), labelAnchor.yFloor());
            bounds = GraphicUtil.transform(bounds, rotation);
        }
        return bounds;
    }

    final void projectToDisplay(DoublePoints points) {
        getProjector().toDisplay(points, getPlotRect(), getCoordinateSystem());
    }

    /// Estimates the fixed label-skip stride for [#CONSTANT_SKIP] mode.
    ///
    /// The calculation measures each prepared label at its eventual painted position, grows the
    /// candidate bounds slightly to treat near-touching labels as overlapping, and returns the
    /// largest run of consecutive displayed labels that collide. [DefaultScaleConfiguration]
    /// then paints every `stride`-th label using that constant step.
    int computeSkipStride(DoublePoints labelPoints, int labelDistance) {
        getSteps().requireValues();

        int labelCount = labelPoints.size();
        if (labelCount == 0 || !isSkippingLabel() || getSkipLabelMode() != CONSTANT_SKIP)
            return 1;

        double[] labelValues = getSteps().getDisplayedValues().data();
        Rectangle2D preparedBounds = getPreparedLabelBounds(0);
        double labelWidth = preparedBounds.getWidth();
        double labelHeight = preparedBounds.getHeight();
        DoublePoint labelAnchor = new DoublePoint(labelPoints.getX(0), labelPoints.getY(0));
        labelAnchor = computeLabelLocation(labelAnchor, getAxisAngle(labelValues[0]), labelDistance, labelWidth, labelHeight);

        Rectangle2D acceptedBounds = Scale.updateCenteredRect(labelAnchor, labelWidth, labelHeight, null);
        GraphicUtil.grow(acceptedBounds, 1.0, 1.0);

        Rectangle2D candidateBounds = null;
        int longestOverlapRun = 1;
        int currentOverlapRun = 1;
        int labelIndex = 1;
        while (labelIndex < labelCount) {
            preparedBounds = getPreparedLabelBounds(labelIndex);
            labelWidth = preparedBounds.getWidth();
            labelHeight = preparedBounds.getHeight();
            labelAnchor.setLocation(labelPoints.getX(labelIndex), labelPoints.getY(labelIndex));
            labelAnchor = computeLabelLocation(labelAnchor, getAxisAngle(labelValues[labelIndex]), labelDistance,
                    labelWidth, labelHeight);

            candidateBounds = Scale.updateCenteredRect(labelAnchor, labelWidth, labelHeight, candidateBounds);
            GraphicUtil.grow(candidateBounds, 1.0, 1.0);
            if (acceptedBounds.intersects(candidateBounds)) {
                currentOverlapRun++;
            } else {
                Rectangle2D reusableBounds = acceptedBounds;
                acceptedBounds = candidateBounds;
                candidateBounds = reusableBounds;
                if (currentOverlapRun > longestOverlapRun)
                    longestOverlapRun = currentOverlapRun;
                currentOverlapRun = 1;
            }
            labelIndex += longestOverlapRun;
        }
        return Math.max(longestOverlapRun, currentOverlapRun);
    }

    FontMetrics getFontMetricsFor(Font font) {
        if (getChart() != null)
            return getChart().getChartArea().getFontMetrics(font);
        return new JLabel().getFontMetrics(font);
    }

    final Rectangle2D getPreparedLabelBounds(int index) {
        return steps.getLabelRenderer(index).getBounds(true);
    }

    /// Estimates how many items of one rendered extent can fit along the current axis.
    final int estimateVisibleItemCount(int itemWidth, int itemHeight, int spacing) {
        return scaleConfiguration.estimateVisibleItemCount(itemWidth, itemHeight, spacing);
    }

    /// Updates the plot rectangle used when projecting this scale into display space.
    ///
    /// When only the projection geometry changes, callers can pass `true` for
    /// `preservePreparedValues` so existing tick values survive and only display points are
    /// recomputed. A full plot-rect change invalidates both prepared values and cached bounds.
    void setPlotRect(Rectangle plotRect, boolean preservePreparedValues) {
        if (!this.plotRect.equals(plotRect)) {
            this.plotRect.setBounds(plotRect);
            if (!preservePreparedValues)
                steps.invalidateValues();
            else
                steps.invalidatePoints();
            scaleConfiguration.invalidate();
            invalidateBoundsCache();
        }
        if (shouldDrawLinkedScale())
            getNextParallelScale().setPlotRect(plotRect, preservePreparedValues);
    }

    Rectangle2D getAxisBounds(Rectangle2D bounds) {
        return scaleConfiguration.getAxisBounds(bounds);
    }

    /// Inflates `bounds` with the prepared major or minor tick-mark endpoints.
    ///
    /// The tick family is selected through `majorTicks`, and the method assumes the matching
    /// value and display-point caches are already prepared.
    Rectangle2D addTickBounds(Rectangle2D bounds, boolean majorTicks) {
        steps.requireValues();
        steps.requirePoints();
        DoublePoints tickPoints = majorTicks ? steps.getMajorPoints() : steps.getSubStepPoints();
        int tickCount = tickPoints.size();
        if (tickCount == 0)
            return bounds;
        double[] tickValues = majorTicks ? steps.getMajorValues().data() : steps.getSubStepValues().data();
        int tickSize = getTickSize(majorTicks);
        int[] tickLine = new int[4];
        for (int tickIndex = 0; tickIndex < tickCount; tickIndex++) {
            computeTickLine(tickPoints.getX(tickIndex), tickPoints.getY(tickIndex), tickSize,
                    getAxisAngle(tickValues[tickIndex]), tickLine);
            if (getTickLayout() == TICK_CROSS)
                GraphicUtil.addToRect(bounds, tickLine[0], tickLine[1]);
            GraphicUtil.addToRect(bounds, tickLine[2], tickLine[3]);
        }
        return bounds;
    }

    void setNextParallelScale(Scale nextParallelScale) {
        if (nextParallelScale == this.nextParallelScale)
            return;
        if (this.nextParallelScale != null)
            this.nextParallelScale.setPreviousParallelScale(null);
        this.nextParallelScale = nextParallelScale;
        if (this.nextParallelScale != null)
            this.nextParallelScale.setPreviousParallelScale(this);
        explicitParallelLink = false;
        invalidateLayout();
    }

    void setSteps(Scale.Steps steps) {
        this.steps = steps;
    }

    private void attachAnnotation(ScaleAnnotation annotation) {
        if (annotation == null)
            throw new IllegalArgumentException();
        if (annotation.getScale() != null)
            throw new IllegalArgumentException("Annotation already attached to a scale");
        if (annotations == null)
            annotations = new ArrayList<>();
        annotations.add(annotation);
        annotation.setScale(this);
    }

    void setScaleConfiguration(ScaleConfiguration scaleConfiguration) {
        if (scaleConfiguration != this.scaleConfiguration) {
            if (this.scaleConfiguration != null)
                this.scaleConfiguration.setScale(null);
            this.scaleConfiguration = scaleConfiguration;
            if (scaleConfiguration != null)
                scaleConfiguration.setScale(this);
            getSteps().invalidateValues();
        }
    }

    /// Ensures that the base occupied-bounds cache has been populated for the current layout state.
    ///
    /// The cached rectangle covers the axis line, ticks, labels, and title, but not the dynamic
    /// annotation inflation added by [#getBoundsUsingCache(Rectangle2D)].
    final void ensureBoundsCached() {
        if (!r.getFlag(1024))
            computeAndCacheBounds(null);
    }

    final int getScaleLength() {
        return scaleConfiguration.getScaleLength();
    }

    List<ScaleAnnotation> getAttachedAnnotations() {
        return annotations;
    }

    void invalidateLayout() {
        invalidateBoundsCache();
        Chart.Area chartArea = (getChart() == null) ? null : getChart().getChartArea();
        if (chartArea != null) {
            chartArea.clearLayoutCachesAfterPaint();
            if (!am)
                chartArea.revalidateLayout();
        }
    }

    /// Attaches `annotation` to this scale.
    ///
    /// Attached annotations participate in bounds calculation and drawing for this scale until they
    /// are removed again.
    ///
    /// @param annotation the annotation to attach
    /// @throws IllegalArgumentException if `annotation` is `null` or is already attached to a scale
    public void addAnnotation(ScaleAnnotation annotation) {
        attachAnnotation(annotation);
        invalidateLayout();
    }

    void disposeAnnotations() {
        if (annotations != null) {
            for (ScaleAnnotation annotation : annotations)
                annotation.dispose();
        }
    }

    /// Returns the chart-provided base text direction when this scale is attached.
    ///
    /// A detached scale uses `527` as the sentinel that forces [#getResolvedBaseTextDirection()]
    /// to keep treating the direction as unresolved until a chart or local orientation can supply
    /// a concrete bidi base direction.
    private int getInheritedBaseTextDirection() {
        Chart chart = getChart();
        if (chart == null)
            return 527;
        return chart.getResolvedBaseTextDirection();
    }

    /// Returns the fallback foreground inherited from the chart area or the current Swing UI.
    private Color getDefaultForeground() {
        Chart chart = getChart();
        return (chart == null) ? UIManager.getColor("Label.foreground") : chart.getChartArea().getForeground();
    }

    private synchronized Scale.Title getOrCreateTitleState() {
        if (title == null)
            title = new Scale.Title(null, 0.0);
        return title;
    }

    private Axis.Crossing getAutomaticCrossing() {
        return scaleConfiguration.getAutoCrossing();
    }

    private void invalidateBoundsCache() {
        r.setFlag(1024, false);
    }

    /// Returns whether this scale crosses its dual axis at a movable interior value.
    ///
    /// Edge-anchored scales that stay on [Axis#MIN_VALUE] or [Axis#MAX_VALUE] are excluded because
    /// their display position is already implied by the plot rectangle rather than by one changing
    /// dual-axis value.
    private boolean usesFloatingCrossing() {
        Axis.Crossing crossing = getCrossing();
        return crossing != Axis.MIN_VALUE && crossing != Axis.MAX_VALUE;
    }

    /// Invalidates point caches for sibling scales whose display position depends on this axis'
    /// current visible range.
    private void invalidateCrossingDependentScales() {
        if (!getAxis().isXAxis()) {
            Scale xScale = getChart().getXScale();
            if (xScale != null && xScale.usesFloatingCrossing())
                xScale.getSteps().invalidatePoints();
        } else {
            int yAxisCount = getChart().getYAxisCount();
            for (int yAxisIndex = 0; yAxisIndex < yAxisCount; yAxisIndex++) {
                Scale yScale = getChart().getYScale(yAxisIndex);
                if (yScale != null && yScale.usesFloatingCrossing())
                    yScale.getSteps().invalidatePoints();
            }
        }
    }

    private synchronized AxisListener getOrCreateAxisListener() {
        if (ad == null)
            ad = createAxisListener();
        return ad;
    }

    private AxisListener createAxisListener() {
        return new ScaleAxisListener();
    }

    private void initializeTransientState() {
        plotRect = new Rectangle(0, 0, -1, -1);
        cachedBounds = new Rectangle();
    }

    final PlotStyle getAxisStyle() {
        if (an == null)
            an = PlotStyle.createStroked(getForeground());
        else if (!r.getFlag(0x8000))
            an.setStrokePaintInternal(getForeground());
        return an;
    }

    final String formatLabel(double value) {
        return BidiUtil.getCombinedString(computeLabel(value), getResolvedBaseTextDirection(), getComponentOrientation(),
                false);
    }

    private DoublePoints createProjectionInput(double[] values, int valueCount) {
        double[] crossingValues = new double[valueCount];
        Arrays.fill(crossingValues, getCrossingValue());
        return getAxis().isXAxis()
                ? new DoublePoints(valueCount, values, crossingValues)
                : new DoublePoints(valueCount, crossingValues, values);
    }

    /// Inflates `bounds` with the prepared label footprints at their final painted locations.
    Rectangle2D addLabelBounds(Rectangle2D bounds) {
        getSteps().requireValues();
        int labelDistance = getLabelDistance();
        DoublePoints labelPoints = getDisplayedLabelPoints();
        double[] labelValues = getSteps().getDisplayedValues().data();
        Rectangle2D labelBounds = null;
        DoublePoint labelAnchor = new DoublePoint();
        for (int labelIndex = 0, labelCount = labelPoints.size(); labelIndex < labelCount; labelIndex++) {
            Rectangle2D preparedBounds = getPreparedLabelBounds(labelIndex);
            double labelWidth = preparedBounds.getWidth();
            double labelHeight = preparedBounds.getHeight();
            labelAnchor.setLocation(labelPoints.getX(labelIndex), labelPoints.getY(labelIndex));
            labelAnchor = computeLabelLocation(labelAnchor, getAxisAngle(labelValues[labelIndex]), labelDistance,
                    labelWidth, labelHeight);
            labelBounds = Scale.updateCenteredRect(labelAnchor, labelWidth, labelHeight, labelBounds);
            bounds = GraphicUtil.addToRect(bounds, labelBounds);
        }
        return bounds;
    }

    final void setPreviousParallelScale(Scale previousParallelScale) {
        this.previousParallelScale = previousParallelScale;
        getSteps().invalidatePoints();
        invalidateLayout();
    }

    private void detachAnnotation(ScaleAnnotation annotation) {
        if (annotation.getScale() != this)
            throw new IllegalArgumentException("Annotation not displayed by this scale");
        annotation.setScale(null);
        annotations.remove(annotation);
        if (annotations.size() == 0)
            annotations = null;
    }

    /// Invalidates bidi-sensitive title and label state after the base text direction changes.
    protected void baseTextDirectionChanged() {
        if (hasTitle())
            getTitleState().invalidateDisplayText();
        invalidatePreparedState();
    }

    int getTickSize(boolean majorTick) {
        return scaleConfiguration.getTickSize(majorTick);
    }

    private Rectangle2D copyCachedBounds(Rectangle2D bounds) {
        requireCachedBounds();
        if (bounds != null) {
            bounds.setRect(cachedBounds);
            return bounds;
        }
        return new Rectangle(cachedBounds);
    }

    /// Explicitly links `scale` so it shares this scale's axis element and cached bounds handling.
    ///
    /// This is stronger than the automatic sibling discovery used by [#getNextParallelScale()] and
    /// [#getPreviousParallelScale()], because explicit links also enable joint drawing through
    /// [#shouldDrawLinkedScale()].
    void linkParallelScale(Scale scale) {
        setNextParallelScale(scale);
        explicitParallelLink = scale != null;
        if (explicitParallelLink)
            scale.setAxisElement(getAxisElement());
    }

    /// Called when the owning chart connection changes.
    ///
    /// The base implementation keeps no extra chart-level state.
    @Override
    protected void chartConnected(Chart oldChart, Chart newChart) {
    }

    /// Reacts to component-orientation changes that may alter bidi label and title composition.
    protected void componentOrientationChanged(ComponentOrientation oldOrientation, ComponentOrientation newOrientation) {
        block:
        if (newOrientation.isLeftToRight() != oldOrientation.isLeftToRight()) {
            if (getInheritedBaseTextDirection() != 514)
                if (getResolvedBaseTextDirection() != 527)
                    break block;
            baseTextDirectionChanged();
        } // end block

    }

    /// Computes the occupied bounds of the decorated scale.
    ///
    /// The returned rectangle includes the axis line, tick marks, labels, title, and currently
    /// visible annotations.
    protected Rectangle2D computeBounds(Rectangle2D bounds) {
        Rectangle2D computedBounds = bounds;
        steps.requireValues();
        steps.requirePoints();
        if (hasTitle())
            title.requirePrepared();
        scaleConfiguration.requireUpToDate();
        computedBounds = getAxisBounds(computedBounds);
        if (isLabelVisible())
            computedBounds = addLabelBounds(computedBounds);
        boolean ticksVisible = isMajorTickVisible() || isMinorTickVisible();
        boolean ticksAffectBounds = !isLabelVisible() || getTickLayout() == TICK_CROSS || getTickLayout() == TICK_INSIDE;
        if (ticksVisible && ticksAffectBounds)
            computedBounds = addTickBounds(computedBounds, isMajorTickVisible());

        if (hasTitle()) {
            Rectangle2D titleBounds = title.getBounds(null);
            computedBounds.add(titleBounds);
        }
        try {
            am = true;
            updateAnnotations();
        } finally {
            am = false;
        }
        return computedBounds;
    }

    /// Computes display text for one axis value.
    ///
    /// A custom [ValueFormatter] installed through [#setLabelFormat(ValueFormatter)] takes
    /// precedence. Otherwise the current [StepsDefinition] formats the value according to its
    /// numeric, category, or time semantics.
    ///
    /// @param value the axis value to format
    /// @return the display label for `value`
    public String computeLabel(double value) {
        if (getLabelFormat() != null)
            return getLabelFormat().formatValue(value);
        String formattedValue = (getStepsDefinition() == null) ? "" : getStepsDefinition().computeLabel(value);
        return formattedValue;
    }

    /// Computes the anchor point used to paint one label.
    ///
    /// The default implementation delegates the geometric placement to [GraphicUtil] and then
    /// clamps y-axis labels to the chart draw rectangle so tall labels do not drift beyond the plot
    /// bounds. Subclasses can override this method to change label anchoring without changing the
    /// tick-generation pipeline.
    protected DoublePoint computeLabelLocation(DoublePoint anchor,
                                               double axisAngle,
                                               int offset,
                                               double labelWidth,
                                               double labelHeight) {
        GraphicUtil.computeTextLocation(anchor, axisAngle, offset, labelWidth, labelHeight);
        Chart chart = getChart();
        if (chart == null || chart.getChartArea() == null || getAxis() == null)
            return anchor;

        Rectangle drawRect = chart.getChartArea().getDrawRect();
        if (drawRect == null || drawRect.isEmpty())
            return anchor;

        if (getAxis().isYAxis()) {
            double halfHeight = labelHeight / 2.0;
            double minY = drawRect.y + halfHeight;
            double maxY = drawRect.y + drawRect.height - halfHeight;
            if (maxY >= minY)
                anchor.y = Math.clamp(anchor.y, minY, maxY);
        }
        return anchor;
    }

    final boolean isTextAntiAliased() {
        return getChart().isAntiAliasingText();
    }

    private void setCategoryFlag(boolean category) {
        r.setFlag(2048, category);
    }

    /// Inflates `bounds` with the visible annotation geometry attached to this scale.
    Rectangle2D addAnnotationBounds(Rectangle2D bounds) {
        if (annotations == null)
            return bounds;
        Rectangle2D annotationBounds = null;
        DataInterval visibleRange = getAxis().getVisibleRange();
        for (ScaleAnnotation annotation : annotations)
            if (visibleRange.isInside(annotation.getValue())) {
                annotationBounds = annotation.getBounds(annotationBounds);
                GraphicUtil.addToRect(bounds, annotationBounds);
            }
        return bounds;
    }

    /// Paints the scale when it is currently viewable.
    ///
    /// A scale is viewable only when it is visible, attached to an axis, and its configured
    /// crossing still intersects the dual axis' visible range.
    @Override
    public synchronized void draw(Graphics g) {
        if (!isViewable())
            return;
        scaleConfiguration.draw(g);
    }

    final Scale.Title getTitleState() {
        return title;
    }

    /// Recomputes and stores the base occupied-bounds cache for the current layout state.
    private Rectangle2D computeAndCacheBounds(Rectangle2D bounds) {
        Rectangle2D computedBounds = computeBounds(bounds);
        cachedBounds.setRect(computedBounds);
        r.setFlag(1024, true);
        return computedBounds;
    }

    /// Returns whether category steps are positioned between successive category labels.
    ///
    /// This affects both label placement and whether minor ticks can reuse the same in-between
    /// positions.
    final boolean areCategoryStepsBetweenLabels() {
        return isCategory() && ((CategoryStepsDefinition) getStepsDefinition()).isStepBetweenCategory();
    }

    /// Returns whether category labels are centered between adjacent category steps.
    final boolean areCategoryLabelsBetweenSteps() {
        return isCategory() && ((CategoryStepsDefinition) getStepsDefinition()).isLabelBetweenCategory();
    }

    /// Returns the annotations currently attached to this scale.
    ///
    /// This method preserves the historical `null`-when-empty contract used by the charting
    /// module.
    public ScaleAnnotation[] getAnnotations() {
        if (annotations == null)
            return null;
        return annotations.toArray(ScaleAnnotation[]::new);
    }

    /// Returns the axis drawn by this scale, or `null` while the scale is detached.
    public final Axis getAxis() {
        return (axisElement != null) ? axisElement.getAxis() : null;
    }

    public final Stroke getAxisStroke() {
        return getAxisStyle().getStroke();
    }

    /// Recalculates chart scales if needed and returns this scale's current occupied bounds.
    ///
    /// The returned bounds include the axis line, ticks, labels, title, and visible annotations.
    @Override
    public synchronized Rectangle2D getBounds(Rectangle2D bounds) {
        recalc();
        return getBoundsUsingCache(bounds);
    }

    /// Returns this scale's occupied bounds using the last prepared geometry when possible.
    ///
    /// Unlike [#getBounds(Rectangle2D)], this method does not force a chart-wide scale rebuild
    /// before consulting cached layout state. When the base bounds cache is missing it is rebuilt
    /// lazily for this scale only, then visible annotation bounds are merged on top.
    public Rectangle2D getBoundsUsingCache(Rectangle2D bounds) {
        ensureBoundsCached();
        Rectangle2D result = copyCachedBounds(bounds);
        result = addAnnotationBounds(result);
        scaleConfiguration.adjustBounds(result);
        return result;
    }

    /// Returns the owning chart, or `null` while this scale is detached.
    @Override
    public final Chart getChart() {
        return (axisElement != null) ? axisElement.getChart() : null;
    }

    public int getCircleSide() {
        return ah;
    }

    /// Returns the component orientation inherited from the owning chart, or `UNKNOWN` while detached.
    public final ComponentOrientation getComponentOrientation() {
        Chart chart = getChart();
        return (chart != null) ? chart.getComponentOrientation() : ComponentOrientation.UNKNOWN;
    }

    /// Returns the coordinate system used to translate this scale's values into chart-area space.
    public final CoordinateSystem getCoordinateSystem() {
        return (axisElement != null) ? axisElement.getCoordinateSystem() : null;
    }

    /// Returns the current crossing policy used to anchor this scale on its dual axis.
    ///
    /// When automatic crossing is enabled, the value is supplied by the active
    /// [ScaleConfiguration].
    public final Axis.Crossing getCrossing() {
        return (ae != null) ? ae : getAutomaticCrossing();
    }

    /// Returns the current crossing in the dual axis' value space.
    public final double getCrossingValue() {
        return getCrossing().getValue(getDualAxis());
    }

    /// Returns the default side used when automatic side selection is still enabled.
    ///
    /// Scales crossing at [Axis#MAX_VALUE] default to the positive side; other crossings use the
    /// opposite side.
    public int getDefaultSide() {
        return (getCrossing() != Axis.MAX_VALUE) ? -1 : 1;
    }

    @Override
    public final int getDrawOrder() {
        return s;
    }

    /// Returns the axis crossed by this scale, or `null` while detached.
    public final Axis getDualAxis() {
        return (axisElement != null) ? axisElement.getCrossAxis() : null;
    }

    /// Returns the effective foreground color for the axis line, ticks, and default label color.
    ///
    /// When no explicit foreground was set, the color falls back to the owning chart area or the
    /// current Swing label default.
    public Color getForeground() {
        if (an != null)
            if (r.getFlag(0x8000))
                return an.getStrokeColor();
        return getDefaultForeground();
    }

    public final int getLabelAlignment() {
        return x;
    }

    /// Returns the display-space bounds of the prepared label at `index`.
    ///
    /// The index is in the current prepared major-label sequence, not raw dataset order.
    ///
    /// @param index  the prepared major-label index
    /// @param bounds the destination rectangle to reuse, or `null` to allocate a new one
    /// @return the label bounds in chart-area display space
    /// @throws IndexOutOfBoundsException if `index` is not a valid prepared label index
    public Rectangle2D getLabelBounds(int index, Rectangle2D bounds) {
        if (index >= getLabelCount())
            throw new IndexOutOfBoundsException("Invalid step label index");
        getSteps().prepare();
        DoublePoints labelPoints = getDisplayedLabelPoints();
        Rectangle2D preparedBounds = getPreparedLabelBounds(index);
        double labelWidth = preparedBounds.getWidth();
        double labelHeight = preparedBounds.getHeight();
        DoublePoint labelAnchor = new DoublePoint(labelPoints.getX(index), labelPoints.getY(index));
        double labelValue = getSteps().getDisplayedValues().get(index);
        labelAnchor = computeLabelLocation(labelAnchor, getAxisAngle(labelValue), getLabelDistance(),
                labelWidth, labelHeight);
        return Scale.updateCenteredRect(labelAnchor, labelWidth, labelHeight, bounds);
    }

    /// Returns the effective label color after foreground fallback resolution.
    public final Color getLabelColor() {
        return (ao != null) ? ao : getForeground();
    }

    public int getLabelCount() {
        return getDisplayedLabelPoints().size();
    }

    /// Returns the effective label font after chart-level font scaling has been applied.
    ///
    /// When no explicit font was set, the chart-area font or Swing label font is used.
    public final Font getLabelFont() {
        Chart chart = getChart();
        if (labelFont == null) {
            return (chart == null) ? UIManager.getFont("Label.font") : chart.getChartArea().getFont();
        }
        if (chart != null && chart.getFontManager() != null)
            return chart.getFontManager().getDeriveFont(labelFont);
        return labelFont;
    }

    /// Returns the custom formatter that overrides [StepsDefinition#computeLabel(double)], or
    /// `null` when labels are generated directly by the current steps definition.
    public final ValueFormatter getLabelFormat() {
        return labelFormatter;
    }

    public final int getLabelOffset() {
        return v;
    }

    public double getLabelRotation() {
        return labelRotation;
    }

    /// Returns the axis value behind the prepared major label at `index`.
    ///
    /// @param index the prepared major-label index
    /// @return the axis value used to produce that label
    /// @throws IndexOutOfBoundsException if `index` is not a valid prepared label index
    public double getLabelValue(int index) {
        if (index >= getLabelCount())
            throw new IndexOutOfBoundsException("Invalid step label index");
        return getSteps().getDisplayedValues().get(index);
    }

    /// Rebuilds the current tick preparation if needed and returns an immutable tick snapshot.
    ///
    /// Unlike [#snapshotPreparedTicks()], this method is allowed to trigger a scale rebuild through
    /// [#recalc()] so callers receive the current prepared major ticks whenever the scale is
    /// attached to a chart area.
    public synchronized List<VisibleTick> snapshotVisibleTicks() {
        if (getChart() == null || getChart().getChartArea() == null)
            return List.of();

        refreshTickSnapshot();
        DoublePoints points = getDisplayedLabelPoints();
        DoubleArray values = getSteps().getDisplayedValues();
        String[] labels = getSteps().getLabels();
        int count = Math.min(values.size(), labels.length);
        if (count == 0)
            return List.of();

        List<VisibleTick> ticks = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            ticks.add(new VisibleTick(values.get(i), labels[i], points.getXFloor(i), points.getYFloor(i)));
        return List.copyOf(ticks);
    }

    /// Returns an immutable snapshot of the ticks that are already prepared for the current layout.
    ///
    /// If the chart has not yet prepared tick values, display points, and labels for the current
    /// layout pass, this method returns an empty list instead of forcing a rebuild.
    public synchronized List<VisibleTick> snapshotPreparedTicks() {
        if (getChart() == null || getChart().getChartArea() == null)
            return List.of();

        try {
            if (!getChart().scalesUpToDate || !getSteps().isPrepared())
                return List.of();

            DoublePoints points = getDisplayedLabelPoints();
            DoubleArray values = getSteps().getDisplayedValues();
            String[] labels = getSteps().getLabels();
            int count = Math.min(values.size(), labels.length);
            if (count == 0)
                return List.of();

            List<VisibleTick> ticks = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                ticks.add(new VisibleTick(values.get(i), labels[i], points.getXFloor(i), points.getYFloor(i)));
            return List.copyOf(ticks);
        } catch (ConcurrentModificationException ex) {
            return List.of();
        }
    }

    /// Returns the subset of prepared ticks that can currently be displayed without overlap.
    ///
    /// When label skipping is disabled this is identical to [#snapshotVisibleTicks()]. When
    /// skipping is enabled the method filters the visible ticks by their current label bounds and
    /// preserves at least the first tick when any prepared ticks exist.
    public synchronized List<VisibleTick> snapshotDisplayedTicks() {
        if (getChart() == null || getChart().getChartArea() == null)
            return List.of();

        List<VisibleTick> ticks = snapshotVisibleTicks();
        if (ticks.isEmpty() || !isSkippingLabel())
            return ticks;

        List<VisibleTick> displayed = new ArrayList<>(ticks.size());
        Rectangle2D previousBounds = null;
        for (int i = 0; i < ticks.size(); i++) {
            Rectangle2D bounds = snapshotLabelBounds(i);
            if (bounds == null) {
                displayed.add(ticks.get(i));
                continue;
            }

            GraphicUtil.grow(bounds, 1.0, 0.0);
            if (previousBounds == null || !previousBounds.intersects(bounds)) {
                displayed.add(ticks.get(i));
                previousBounds = bounds;
            }
        }
        return displayed.isEmpty() ? List.of(ticks.getFirst()) : List.copyOf(displayed);
    }

    private void refreshTickSnapshot() {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                recalc();
                getSteps().prepare();
                return;
            } catch (ConcurrentModificationException ex) {
                if (attempt == 1)
                    throw ex;
            }
        }
    }

    private Rectangle2D snapshotLabelBounds(int index) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return getLabelBounds(index, null);
            } catch (ConcurrentModificationException ex) {
                if (attempt == 1)
                    throw ex;
                refreshTickSnapshot();
            }
        }
        return null;
    }

    /// Returns the length of major tick marks in display pixels.
    public final int getMajorTickSize() {
        return t;
    }

    /// Returns the length of minor tick marks in display pixels.
    public final int getMinorTickSize() {
        return u;
    }

    /// Returns which side of a radial axis receives labels and tick marks.
    public int getRadialSide() {
        return ag;
    }

    /// Returns the base text direction used when composing bidi labels and title text.
    ///
    /// When the chart has not pinned a specific direction, the value is derived from the current
    /// [ComponentOrientation].
    public int getResolvedBaseTextDirection() {
        int inheritedBaseTextDirection = getInheritedBaseTextDirection();
        if (inheritedBaseTextDirection != 514)
            return inheritedBaseTextDirection;
        return (!getComponentOrientation().isLeftToRight()) ? 520 : 516;
    }

    /// Returns the resolved cartesian side currently used by this scale.
    public int getSide() {
        return !isAutoSide() ? af : getDefaultSide();
    }

    /// Returns the active label-skipping mode.
    public final int getSkipLabelMode() {
        return skipLabelMode;
    }

    /// Returns the current tick-generation strategy.
    public final StepsDefinition getStepsDefinition() {
        return stepsDefinition;
    }

    /// Returns the live cache of prepared major tick values.
    ///
    /// The returned [DoubleArray] belongs to this scale and is refreshed in place during scale
    /// updates. Use it only for immediate read-only inspection and prefer [#snapshotVisibleTicks()]
    /// when an immutable view is needed.
    public final DoubleArray getStepValues() {
        steps.prepare();
        return steps.getMajorValues();
    }

    /// Returns the live cache of prepared minor tick values.
    ///
    /// The returned [DoubleArray] follows the same cache and ownership rules as
    /// [#getStepValues()].
    public final DoubleArray getSubStepValues() {
        steps.prepare();
        return steps.getSubStepValues();
    }

    /// Returns how tick marks are drawn relative to the axis line.
    public final int getTickLayout() {
        return y;
    }

    /// Returns the current title text, or `null` when this scale has no title.
    public final String getTitle() {
        return getOrCreateTitleState().getText();
    }

    /// Returns the background shape that the title renderer would paint, or `null` when no title
    /// is configured.
    public Shape getTitleBackgroundShape() {
        if (!hasTitle())
            return null;
        prepare();
        return getTitleState().getBackgroundShape();
    }

    /// Returns the title font from the live title renderer.
    public Font getTitleFont() {
        return getOrCreateTitleState().getLabelRenderer().getFont();
    }

    public final int getTitleOffset() {
        return w;
    }

    /// Returns the title placement as a percentage along the axis line.
    public int getTitlePlacement() {
        return getOrCreateTitleState().getPlacement();
    }

    /// Returns the live [LabelRenderer] used to measure and paint the title.
    ///
    /// Mutating the returned renderer affects layout and drawing of this scale directly.
    public LabelRenderer getTitleRenderer() {
        return getOrCreateTitleState().getLabelRenderer();
    }

    /// Returns the current title rotation in degrees.
    public double getTitleRotation() {
        return getOrCreateTitleState().getLabelRenderer().getRotation();
    }

    /// Returns the next scale on the same chart side that shares this scale's edge crossing.
    ///
    /// Explicit links win. Otherwise, cartesian charts lazily discover the next y-scale with the
    /// same non-floating crossing so adjacent edge scales can be treated as one parallel family.
    final Scale getNextParallelScale() {
        if (nextParallelScale != null)
            return nextParallelScale;
        return findAutoParallelScale(true);
    }

    /// Returns whether `point` lies inside this scale's interactive region.
    ///
    /// The exact hit shape depends on the active [ScaleConfiguration]. Radial scales, for example,
    /// hit-test against the rendered axis line rather than the full cached bounds rectangle.
    public boolean hit(Point2D point) {
        return scaleConfiguration.contains(point);
    }

    /// Returns the previous scale on the same chart side that shares this scale's edge crossing.
    ///
    /// This mirrors [#getNextParallelScale()] but walks the y-scale list in reverse.
    final Scale getPreviousParallelScale() {
        if (previousParallelScale != null)
            return previousParallelScale;
        return findAutoParallelScale(false);
    }

    /// Returns whether the crossing policy is still inherited from the active configuration.
    public final boolean isAutoCrossing() {
        return ae == null;
    }

    /// Returns whether cartesian side selection is still derived from the current crossing.
    public final boolean isAutoSide() {
        return af == 0;
    }

    public final boolean isAutoWrapping() {
        return ab;
    }

    public final boolean isAxisVisible() {
        return r.getFlag(16);
    }

    public final boolean isCategory() {
        return r.getFlag(2048);
    }

    public final boolean isLabelVisible() {
        return r.getFlag(32);
    }

    public final boolean isMajorTickVisible() {
        return r.getFlag(4);
    }

    public final boolean isMinorTickVisible() {
        return r.getFlag(8);
    }

    public final boolean isSkippingLabel() {
        return r.getFlag(2);
    }

    /// Returns whether this scale should currently be drawn.
    ///
    /// Scales anchored to a floating crossing become non-viewable when that crossing value falls
    /// outside the dual axis' visible range. Edge-anchored scales remain viewable as long as the
    /// scale is attached and explicitly visible.
    public final boolean isViewable() {
        Axis dualAxis = getDualAxis();
        if (dualAxis == null || !isVisible() || getAxis() == null)
            return false;
        return !usesFloatingCrossing() || dualAxis.getVisibleRange().isInside(getCrossingValue());
    }

    @Override
    public final boolean isVisible() {
        return r.getFlag(1);
    }

    /// Returns whether [#linkParallelScale(Scale)] established an explicit linked-scale family.
    final boolean hasExplicitParallelLink() {
        return explicitParallelLink;
    }

    /// Returns whether this scale should also draw its explicitly linked parallel sibling.
    ///
    /// Auto-discovered siblings are excluded here; only explicit links participate in shared
    /// drawing and bounds inflation.
    final boolean shouldDrawLinkedScale() {
        return getNextParallelScale() != null
                && hasExplicitParallelLink()
                && (getChart() == null || getChart().getType() == 1);
    }

    final Scale.Steps getSteps() {
        return steps;
    }

    /// Returns whether projected tick points must be recomputed for each layout pass.
    ///
    /// Floating-crossing scales move with the dual axis' visible range, so they cannot safely
    /// reuse the cached edge-based point preparation path.
    final boolean requiresDynamicPointPreparation() {
        return usesFloatingCrossing();
    }

    final ChartProjector getProjector() {
        return (axisElement == null) ? null : axisElement.getChart().getProjector();
    }

    final ChartProjector getProjector2D() {
        return (axisElement == null) ? null : axisElement.getChart().getProjector2D();
    }

    final Rectangle getPlotRect() {
        return plotRect;
    }

    /// Returns the owning axis element, or `null` while this scale is detached from a chart.
    final Chart.AxisElement getAxisElement() {
        return axisElement;
    }

    final DoublePoints getMajorTickPoints() {
        return steps.getMajorPoints();
    }

    /// Restores transient caches and listeners after deserialization.
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        initializeTransientState();
    }

    /// Forces the owning chart to rebuild its scale caches.
    public final void recalc() {
        getChart().updateScales();
    }

    /// Detaches `annotation` from this scale.
    ///
    /// @param annotation the attached annotation to remove
    /// @throws IllegalArgumentException if `annotation` is not currently displayed by this scale
    public void removeAnnotation(ScaleAnnotation annotation) {
        detachAnnotation(annotation);
        invalidateLayout();
    }

    final DoublePoints getMinorTickPoints() {
        return steps.getSubStepPoints();
    }

    /// Replaces the currently attached annotations with `annotations`.
    ///
    /// Passing `null` removes all current annotations. Each supplied annotation must be unattached
    /// when passed in.
    public void setAnnotations(ScaleAnnotation[] annotations) {
        boolean changed = false;
        if (this.annotations != null) {
            while (!this.annotations.isEmpty()) {
                detachAnnotation(this.annotations.getFirst());
                changed = true;
            }
        }
        if (annotations != null) {
            for (ScaleAnnotation annotation : annotations) {
                attachAnnotation(annotation);
                changed = true;
            }
        }
        if (changed)
            invalidateLayout();
    }

    /// Enables or disables automatic crossing selection.
    ///
    /// Disabling automatic crossing freezes the scale at its current resolved [#getCrossing()]
    /// value.
    public void setAutoCrossing(boolean autoCrossing) {
        if (autoCrossing)
            setCrossingInternal((Axis.Crossing) null);
        else
            setCrossingInternal(getCrossing());
    }

    /// Enables or disables automatic cartesian side selection.
    ///
    /// Disabling automatic side selection freezes the current resolved [#getSide()] into explicit
    /// state until automatic mode is enabled again.
    public void setAutoSide(boolean autoSide) {
        if (autoSide != isAutoSide()) {
            if (!autoSide)
                af = getDefaultSide();
            else {
                int previousSide = getSide();
                af = 0;
                if (getSide() != previousSide) {
                    scaleConfiguration.invalidate();
                    invalidateLayout();
                }
            }
        }
    }

    /// Enables or disables label auto-wrapping based on the current scale length.
    public void setAutoWrapping(boolean autoWrapping) {
        if (ab == autoWrapping)
            return;
        ab = autoWrapping;
        getSteps().setAutoWrapping(autoWrapping);
        invalidateLayout();
    }

    /// Sets the stroke used for the axis line and tick marks.
    public void setAxisStroke(Stroke stroke) {
        an = getAxisStyle().setStroke(stroke);
    }

    /// Shows or hides the axis line itself.
    ///
    /// Tick marks and labels are controlled independently through their own visibility flags.
    public void setAxisVisible(boolean axisVisible) {
        if (axisVisible != isAxisVisible()) {
            r.setFlag(16, axisVisible);
            invalidateLayout();
        }
    }

    /// Switches between numeric and categorical stepping.
    ///
    /// Enabling category mode installs a default [CategoryStepsDefinition]. Disabling it restores
    /// automatic numeric stepping with [DefaultStepsDefinition].
    public final void setCategory(boolean category) {
        if (category != r.getFlag(2048))
            if (!category)
                setStepUnit(null, null);
            else
                this.setCategory(null, true);
    }

    /// Installs a [CategoryStepsDefinition] backed by `labelDataSet`.
    ///
    /// @param labelDataSet        the dataset used for category labels, or `null` to defer to the chart data source
    /// @param stepBetweenCategory whether major steps should fall on category boundaries instead of category centers
    public void setCategory(DataSet labelDataSet, boolean stepBetweenCategory) {
        setStepsDefinition(new CategoryStepsDefinition(stepBetweenCategory, labelDataSet));
    }

    /// Sets whether circular scales render [#OUTSIDE] or [#INSIDE] the circle.
    public void setCircleSide(int circleSide) {
        switch (circleSide) {
            case OUTSIDE:
            case INSIDE:
                int previousSide = getCircleSide();
                ah = circleSide;
                if (circleSide != previousSide) {
                    scaleConfiguration.invalidate();
                    invalidateLayout();
                }
                return;

            default:
                throw new IllegalArgumentException(
                        "invalid circle side: " + circleSide);

        }
    }

    /// Sets the crossing policy used to anchor this scale on the dual axis.
    ///
    /// Passing `null` re-enables the configuration's automatic crossing policy.
    public void setCrossing(Axis.Crossing crossing) {
        setCrossingInternal(crossing);
    }

    /// Anchors this scale to one explicit value on the dual axis.
    public void setCrossingValue(double value) {
        setCrossing(new Axis.AnchoredCrossing(value));
    }

    /// Changes the drawable ordering used by the owning chart.
    public void setDrawOrder(int drawOrder) {
        if (s == drawOrder)
            return;
        int previousDrawOrder = s;
        s = drawOrder;
        Chart chart = getChart();
        if (chart != null) {
            chart.handleDrawableDrawOrderChanged(this, previousDrawOrder, drawOrder);
            chart.getChartArea().repaint2D(getBounds(null));
        }
    }

    /// Sets the explicit foreground used for the axis stroke and, by default, label text.
    ///
    /// Passing `null` or a Swing UI-resource color clears the override and resumes the chart or UI
    /// default.
    public void setForeground(Color color) {
        Color resolvedColor = color;
        if (resolvedColor != null && !(resolvedColor instanceof ColorUIResource)) {
            r.setFlag(0x8000, true);
        } else {
            r.setFlag(0x8000, false);
            resolvedColor = getDefaultForeground();
        }
        an = getAxisStyle().setStrokePaint(resolvedColor);
    }

    /// Sets the horizontal alignment used by prepared label renderers.
    ///
    /// Accepted values are the centered, left, and right alignment constants used by
    /// [LabelRenderer].
    public void setLabelAlignment(int alignment) {
        if (alignment != 2)
            if (alignment != 0)
                if (alignment != 4)
                    throw new IllegalArgumentException("Invalid Alignment");
        if (x == alignment)
            return;
        x = alignment;
        getSteps().setLabelAlignment(alignment);
        if (getChart() != null)
            getChart().getChartArea().repaint();
    }

    /// Sets an explicit label color.
    ///
    /// Passing `null` makes labels follow [#getForeground()] again.
    public void setLabelColor(Color color) {
        ao = color;
    }

    /// Sets the base label font before chart-level font scaling is applied.
    ///
    /// Passing `null` restores inheritance from the chart area or Swing default.
    public void setLabelFont(Font font) {
        if (font != labelFont) {
            labelFont = font;
            invalidateLayout();
        }
    }

    /// Installs a formatter that overrides [StepsDefinition#computeLabel(double)] for label text.
    public void setLabelFormat(ValueFormatter formatter) {
        if (formatter != labelFormatter) {
            labelFormatter = formatter;
            getSteps().invalidateLabels();
            invalidateLayout();
        }
    }

    /// Sets the distance in display pixels between the axis decoration and label anchors.
    public void setLabelOffset(int offset) {
        if (offset < 0)
            throw new IllegalArgumentException("Value must be positive");
        if (offset != v) {
            v = offset;
            invalidateLayout();
        }
    }

    /// Sets label rotation in degrees.
    public void setLabelRotation(double rotation) {
        if (rotation != labelRotation) {
            labelRotation = rotation;
            getSteps().setLabelRotation(rotation);
            invalidateLayout();
        }
    }

    /// Shows or hides tick labels for this scale.
    public void setLabelVisible(boolean labelVisible) {
        if (labelVisible != isLabelVisible()) {
            r.setFlag(32, labelVisible);
            invalidateLayout();
        }
    }

    /// Installs a logarithmic axis transformer and matching [LogarithmicStepsDefinition].
    ///
    /// The scale must already be attached to a chart because the transformer lives on the owning
    /// [Axis].
    public void setLogarithmic(double base) {
        if (getAxis() == null)
            throw new UnsupportedOperationException("Scale should be added to a chart");
        getAxis().setTransformer(new LogarithmicAxisTransformer(base));
        setStepsDefinition(new LogarithmicStepsDefinition());
    }

    /// Sets the length of major tick marks in display pixels.
    public void setMajorTickSize(int majorTickSize) {
        if (majorTickSize < 0)
            throw new IllegalArgumentException("Value must be positive");
        if (majorTickSize != t) {
            t = majorTickSize;
            invalidateLayout();
        }
    }

    /// Shows or hides major tick marks.
    public void setMajorTickVisible(boolean majorTickVisible) {
        if (majorTickVisible != isMajorTickVisible()) {
            r.setFlag(4, majorTickVisible);
            invalidateLayout();
        }
    }

    /// Sets the length of minor tick marks in display pixels.
    public void setMinorTickSize(int minorTickSize) {
        if (minorTickSize < 0)
            throw new IllegalArgumentException("Value must be positive");
        if (minorTickSize != u) {
            u = minorTickSize;
            invalidateLayout();
        }
    }

    /// Shows or hides minor tick marks.
    public void setMinorTickVisible(boolean minorTickVisible) {
        if (minorTickVisible != isMinorTickVisible()) {
            r.setFlag(8, minorTickVisible);
            invalidateLayout();
        }
    }

    /// Sets which side of a radial axis receives labels and ticks.
    ///
    /// Accepted values are [#LEFT_SIDE] and [#RIGHT_SIDE].
    public void setRadialSide(int radialSide) {
        switch (radialSide) {
            case LEFT_SIDE:
            case RIGHT_SIDE:
                int previousSide = getRadialSide();
                ag = radialSide;
                if (radialSide != previousSide) {
                    scaleConfiguration.invalidate();
                    invalidateLayout();
                }
                return;

            default:
                throw new IllegalArgumentException(
                        "invalid radial side: " + radialSide);

        }
    }

    /// Pins a cartesian scale to one explicit side when automatic side selection is disabled.
    ///
    /// Accepted values are [#LOWER_SIDE] and [#UPPER_SIDE].
    public void setSide(int side) {
        switch (side) {
            case LOWER_SIDE:
            case UPPER_SIDE:
                int previousSide = getSide();
                af = side;
                if (side != previousSide) {
                    scaleConfiguration.invalidate();
                    invalidateLayout();
                }
                return;

            default:
                throw new IllegalArgumentException("invalid side: " + side);

        }
    }

    /// Selects how label skipping is applied when [#isSkippingLabel()] is enabled.
    ///
    /// Use [#CONSTANT_SKIP] for one fixed skip stride or [#ADAPTIVE_SKIP] for overlap-aware
    /// filtering.
    public void setSkipLabelMode(int skipLabelMode) {
        if (skipLabelMode == this.skipLabelMode)
            return;
        if (skipLabelMode != 1)
            if (skipLabelMode != 2)
                throw new IllegalArgumentException("Invalid constant");
        this.skipLabelMode = skipLabelMode;
        invalidateLayout();
    }

    /// Enables or disables label skipping for dense scales.
    public void setSkippingLabel(boolean skippingLabel) {
        if (skippingLabel == isSkippingLabel())
            return;
        r.setFlag(2, skippingLabel);
        invalidateLayout();
    }

    /// Replaces the current tick-generation strategy.
    ///
    /// The supplied definition becomes owned by this scale and must not already belong to another
    /// scale. Switching between category and non-category stepping may also force the chart to
    /// recompute its data range, and logarithmic step definitions upgrade the attached axis to a
    /// logarithmic transformer when needed.
    public void setStepsDefinition(StepsDefinition stepsDefinition) {
        if (stepsDefinition == null)
            throw new IllegalArgumentException("Steps definition cannot be null");
        if (stepsDefinition.getScale() != null)
            if (stepsDefinition.getScale() != this)
                throw new IllegalArgumentException("Steps definition owned by another scale");
        if (stepsDefinition == this.stepsDefinition)
            return;
        boolean categoryDefinition = stepsDefinition instanceof CategoryStepsDefinition;
        boolean categoryChanged = isCategory() != categoryDefinition;
        if (this.stepsDefinition != null)
            this.stepsDefinition.setScale(null);
        setCategoryFlag(categoryDefinition);
        this.stepsDefinition = stepsDefinition;
        stepsDefinition.setScale(this);
        Axis axis = getAxis();
        if (axis != null
                && stepsDefinition instanceof LogarithmicStepsDefinition
                && !(axis.getTransformer() instanceof LogarithmicAxisTransformer))
            axis.setTransformer(new LogarithmicAxisTransformer());
        if (steps != null)
            getSteps().invalidateValues();
        if (categoryChanged) {
            Chart chart = getChart();
            if (chart != null)
                chart.updateDataRange();
        }
        invalidateLayout();
    }

    /// Configures numeric major and minor step units through [DefaultStepsDefinition].
    ///
    /// Passing `null` for either argument keeps that unit automatic. If the current definition is
    /// not a [DefaultStepsDefinition], this method replaces it with one.
    public void setStepUnit(Double majorStepUnit, Double subStepUnit) {
        StepsDefinition currentDefinition = getStepsDefinition();
        DefaultStepsDefinition definition = (currentDefinition instanceof DefaultStepsDefinition defaultDefinition)
                ? defaultDefinition
                : new DefaultStepsDefinition();
        if (majorStepUnit == null)
            definition.setAutoStepUnit(true);
        else
            definition.setStepUnit(majorStepUnit.doubleValue());
        if (subStepUnit == null)
            definition.setAutoSubStepUnit(true);
        else
            definition.setSubStepUnit(subStepUnit.doubleValue());
        if (definition != currentDefinition)
            setStepsDefinition(definition);
    }

    /// Configures how tick marks are drawn relative to the axis line.
    ///
    /// Accepted values are [#TICK_INSIDE], [#TICK_OUTSIDE], and [#TICK_CROSS].
    public void setTickLayout(int tickLayout) {
        if (y == tickLayout)
            return;
        if (tickLayout != 1)
            if (tickLayout != 2)
                if (tickLayout != 3)
                    throw new IllegalArgumentException("Invalid constant");
        y = tickLayout;
        invalidateLayout();
    }

    /// Configures time-based stepping through [TimeStepsDefinition].
    ///
    /// Passing `null` re-enables automatic unit selection. If the current definition is not a
    /// [TimeStepsDefinition], this method replaces it with one.
    public void setTimeUnit(TimeUnit unit) {
        StepsDefinition currentDefinition = getStepsDefinition();
        TimeStepsDefinition definition = (currentDefinition instanceof TimeStepsDefinition timeDefinition)
                ? timeDefinition
                : new TimeStepsDefinition();
        if (unit == null)
            definition.setAutoUnit(true);
        else
            definition.setUnit(unit);
        if (definition != currentDefinition)
            setStepsDefinition(definition);
    }

    /// Sets the title text while preserving the current title rotation.
    public void setTitle(String text) {
        if (Objects.equals(text, getTitle()))
            return;
        getOrCreateTitleState().setText(text);
    }

    /// Sets title text and rotation together.
    ///
    /// Rotation is expressed in degrees and is forwarded to the live renderer returned by
    /// [#getTitleRenderer()].
    public void setTitle(String text, double rotation) {
        Title titleState = getOrCreateTitleState();
        if (Objects.equals(text, titleState.getText())
                && rotation == titleState.getLabelRenderer().getRotation())
            return;
        titleState.setText(text);
        titleState.getLabelRenderer().setRotation(rotation);
    }

    /// Sets the title font on the live title renderer.
    public void setTitleFont(Font font) {
        getOrCreateTitleState().getLabelRenderer().setFont(font);
    }

    /// Sets the distance in display pixels between the scale decoration and the title anchor.
    public void setTitleOffset(int offset) {
        if (offset < 0)
            throw new IllegalArgumentException("Value must be positive");
        if (offset != w) {
            w = offset;
            invalidateLayout();
        }
    }

    /// Sets title placement as a percentage along the axis line.
    ///
    /// `0` pins the title to one end, `100` to the other, and intermediate values interpolate
    /// between those extremes.
    public void setTitlePlacement(int placement) {
        getOrCreateTitleState().setPlacement(placement);
    }

    /// Sets title rotation in degrees.
    public void setTitleRotation(double rotation) {
        getOrCreateTitleState().getLabelRenderer().setRotation(rotation);
    }

    /// Shows or hides the entire scale.
    public void setVisible(boolean visible) {
        if (visible != isVisible()) {
            r.setFlag(1, visible);
            invalidateLayout();
        }
    }

    final DoublePoints getDisplayedLabelPoints() {
        return steps.getDisplayedPoints();
    }

    /// Converts one axis value to the display-space point where this scale crosses that value.
    public Point toPoint(double value) {
        DoublePoint location = new DoublePoint();
        computeValueLocation(value, location);
        return new Point(location.xFloor(), location.yFloor());
    }

    /// Converts one chart-area display-space point back into the value represented on this scale.
    ///
    /// The conversion uses the current [ChartProjector] and coordinate system, so callers should
    /// only reuse the result while the owning chart layout remains unchanged.
    public double toValue(int x, int y) {
        DoublePoints projectedPoint = new DoublePoints(x, y);
        try {
            getProjector().toData(projectedPoint, getPlotRect(), getCoordinateSystem());
            return (!getAxis().isXAxis()) ? projectedPoint.getY(0) : projectedPoint.getX(0);
        } finally {
            projectedPoint.dispose();
        }
    }

    /// Invalidates scale configuration, prepared steps, and optional title preparation together.
    final void invalidatePreparedState() {
        scaleConfiguration.invalidate();
        getSteps().invalidateAll();
        if (hasTitle())
            getTitleState().invalidate();
    }

    /// Updates subclass-defined annotation state before bounds calculation.
    ///
    /// The base implementation does nothing. Subclasses can override this method to add, remove,
    /// or reposition [ScaleAnnotation] instances during one layout pass.
    public void updateAnnotations() {
    }

    /// Returns whether the configuration, prepared ticks, and optional title are ready to paint.
    final boolean isFullyPrepared() {
        return scaleConfiguration.isUpToDate()
                && getSteps().isPrepared()
                && (!hasTitle() || getTitleState().isPrepared());
    }

    /// Prepares configuration, ticks, and title state for the current chart layout pass.
    ///
    /// If the owning chart has already marked all scales stale, this method delegates to
    /// [Chart#updateScales()] first. Concurrent modification while preparing tick caches aborts the
    /// current pass, clears the partially prepared step state, and asks the chart for a clean
    /// rebuild on the next cycle.
    final boolean prepare() {
        if (!getChart().scalesUpToDate) {
            getChart().updateScales();
            return true;
        }
        scaleConfiguration.ensureUpToDate();
        try {
            if (!getSteps().prepare())
                return false;
        } catch (ConcurrentModificationException ex) {
            // Abort this pass and force a clean rebuild on the next layout cycle
            // instead of trying to repair the partially prepared label cache.
            getSteps().invalidateValues();
            getSteps().invalidatePoints();
            getSteps().invalidateLabels();
            getChart().invalidateScales();
            return false;
        }
        if (hasTitle())
            getTitleState().prepare();
        return true;
    }

    final boolean hasTitle() {
        return title != null && title.getText() != null;
    }

    /// Returns the effective label offset after accounting for outward-facing major ticks.
    int getLabelDistance() {
        int labelDistance = getLabelOffset();
        if (areCategoryStepsBetweenLabels() == areCategoryLabelsBetweenSteps()
                && isMajorTickVisible()
                && (getTickLayout() == TICK_OUTSIDE || getTickLayout() == TICK_CROSS))
            labelDistance += getMajorTickSize();
        return labelDistance;
    }

    /// Throws when callers try to read cached bounds before one layout pass populated them.
    final void requireCachedBounds() {
        if (r.getFlag(1024))
            return;
        throw new ConcurrentModificationException("missing updateBounds() - probably a multithreading problem");
    }

    private Scale findAutoParallelScale(boolean forward) {
        Chart chart = getChart();
        Axis axis = getAxis();
        if (chart == null || chart.getType() != 1 || axis == null || axis.isXAxis() || usesFloatingCrossing())
            return null;

        Axis.Crossing crossing = getCrossing();
        boolean seenThisScale = false;
        if (forward) {
            int yAxisCount = chart.getYAxisCount();
            for (int yAxisIndex = 0; yAxisIndex < yAxisCount; yAxisIndex++) {
                Scale candidateScale = chart.getYScale(yAxisIndex);
                if (candidateScale == null)
                    continue;
                if (candidateScale == this) {
                    seenThisScale = true;
                    continue;
                }
                if (seenThisScale && candidateScale.getCrossing() == crossing)
                    return candidateScale;
            }
        } else {
            for (int yAxisIndex = chart.getYAxisCount() - 1; yAxisIndex >= 0; yAxisIndex--) {
                Scale candidateScale = chart.getYScale(yAxisIndex);
                if (candidateScale == null)
                    continue;
                if (candidateScale == this) {
                    seenThisScale = true;
                    continue;
                }
                if (seenThisScale && candidateScale.getCrossing() == crossing)
                    return candidateScale;
            }
        }
        return null;
    }
}
