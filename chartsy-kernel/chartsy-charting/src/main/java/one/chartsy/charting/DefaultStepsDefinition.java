package one.chartsy.charting;

import java.awt.FontMetrics;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.text.NumberFormatFactory;

/// Supplies linear numeric major and minor ticks for ordinary non-category scales.
///
/// [Scale#setStepUnit(Double, Double)] installs this definition when a scale should step in fixed
/// numeric increments instead of category positions, calendar units, or logarithmic decades. One
/// instance resolves three closely related pieces of state from the current visible range:
/// - a major `stepUnit`
/// - an optional `subStepUnit`
/// - the [NumberFormat] used to label those values
///
/// Automatic major-step selection supports two heuristics:
/// - [#SIMPLE_MODE] chooses a human-friendly `1/2/5 x 10^n` interval from the visible span alone
/// - [#COMPLEX_MODE] also estimates how many labels fit on the current scale, so wide formatted
///   values can force coarser major steps
///
/// Minor steps may be fixed explicitly through [#setSubStepUnit(double)], derived from an explicit
/// [#setSubStepCount(int)], or chosen automatically from the resolved major-step magnitude plus the
/// current scale length. Automatic number formatting is cached because complex-mode label width
/// feeds back into step selection; when the owning chart locale changes, only automatically managed
/// formatters are rebuilt.
///
/// When the owning axis uses an [AffineAxisTransformer], major-step generation runs in transformed
/// visible coordinates and labels are formatted from the transformed values rather than the raw
/// axis values. Instances are mutable, retain any explicit [NumberFormat] by reference, and are
/// not thread-safe.
public class DefaultStepsDefinition extends StepsDefinition {
    /// Chooses automatic major steps from the visible span only.
    public static final int SIMPLE_MODE = 1;

    /// Chooses automatic major steps from the visible span and estimated label density.
    public static final int COMPLEX_MODE = 2;

    private static final int FLAG_AUTO_NUMBER_FORMAT = 1;
    private static final int FLAG_AUTO_STEP_UNIT = 2;
    private static final int FLAG_AUTO_SUB_STEP_UNIT = 4;
    private static final int FLAG_ROUND_STEP_VALUES = 16;
    private static final int FLAG_EXPLICIT_SUB_STEP_COUNT = 32;

    private static final int TARGET_VISIBLE_STEP_COUNT = 10;
    private static final double DEFAULT_STEP_UNIT = 1.0;
    private static final double DEFAULT_STEP_BASE = 10.0;

    private final Flags flags;
    private double stepUnit;
    private double subStepUnit;
    private double stepExponent;
    private double stepBase;
    private double roundingUnit;
    private int subStepCount;
    private int autoMode;
    private NumberFormat numberFormat;

    /// Creates a steps definition that starts in [#SIMPLE_MODE] with automatic major and minor
    /// step selection.
    public DefaultStepsDefinition() {
        this(SIMPLE_MODE);
    }

    /// Creates a steps definition with the requested automatic major-step heuristic.
    ///
    /// Callers should pass either [#SIMPLE_MODE] or [#COMPLEX_MODE]. Other values currently behave
    /// like the label-aware path because only [#SIMPLE_MODE] is treated specially.
    ///
    /// @param autoMode the automatic major-step heuristic to enable
    public DefaultStepsDefinition(int autoMode) {
        flags = new Flags(FLAG_AUTO_NUMBER_FORMAT | FLAG_AUTO_STEP_UNIT | FLAG_AUTO_SUB_STEP_UNIT);
        stepUnit = DEFAULT_STEP_UNIT;
        stepBase = DEFAULT_STEP_BASE;
        setResolvedNumberFormat(null);
        setAutoMode(autoMode);
    }

    private static String formatValue(NumberFormat numberFormat, double value) {
        return numberFormat.format(value);
    }

    /// Expands `range` to human-friendly step boundaries around its current endpoints.
    ///
    /// The interval is mutated in place. The algorithm snaps the bounds to a
    /// `1/2/5 x 10^n` unit derived from the current span and repeats that snap once when the first
    /// expansion changes the preferred unit. [DefaultDataRangePolicy] and financial helpers use
    /// this to keep auto-ranged axes aligned to readable round values.
    ///
    /// @param range the interval to widen in place
    public static void adjustRange(DataInterval range) {
        double initialStepUnit = chooseNiceStepUnit(range.getLength());
        if (initialStepUnit == 0.0) {
            return;
        }

        snapRangeToUnit(range, initialStepUnit);
        double adjustedStepUnit = chooseNiceStepUnit(range.getLength());
        if (adjustedStepUnit != initialStepUnit) {
            snapRangeToUnit(range, adjustedStepUnit);
        }
    }

    private static void snapRangeToUnit(DataInterval range, double unit) {
        range.setMin(Math.floor(range.getMin() / unit) * unit);
        range.setMax(Math.ceil(range.getMax() / unit) * unit);
    }

    private static double floorLogBase(double value, double base) {
        return Math.floor(Math.log(Math.abs(value)) / Math.log(base) + 1.0E-6);
    }

    private static double chooseNiceStepUnit(double visibleSpan) {
        if (visibleSpan == 0.0) {
            return 0.0;
        }

        double exponent = floorLogBase(visibleSpan, 10.0);
        if (exponent == 0.0) {
            return 1.0;
        }

        double scale = Math.pow(10.0, exponent);
        double normalizedSpan = visibleSpan / scale;
        if (exponent < 0.0) {
            if (normalizedSpan <= 1.0) {
                return 2.0 * scale / 10.0;
            }
            if (normalizedSpan > 5.0) {
                return scale;
            }
            return 5.0 * scale / 10.0;
        }
        if (normalizedSpan < 1.0) {
            return scale / 10.0;
        }
        if (normalizedSpan < 2.0) {
            return 2.0 * scale / 10.0;
        }
        if (normalizedSpan >= 5.0) {
            return scale;
        }
        return 5.0 * scale / 10.0;
    }

    private void setAutoSubStepUnitMode(boolean autoSubStepUnit) {
        flags.setFlag(FLAG_AUTO_SUB_STEP_UNIT, autoSubStepUnit);
        flags.setFlag(FLAG_EXPLICIT_SUB_STEP_COUNT, false);
    }

    @Override
    void adjustRange(DataInterval dataRange, DataInterval visibleRange) {
        DefaultStepsDefinition.adjustRange(visibleRange);
    }

    private int estimateVisibleStepCapacity(DataInterval visibleRange, NumberFormat numberFormat) {
        FontMetrics labelMetrics = getScale().getFontMetricsFor(getScale().getLabelFont());
        int labelWidth = labelMetrics.stringWidth(formatValue(numberFormat, visibleRange.min));
        labelWidth = Math.max(labelWidth, labelMetrics.stringWidth(formatValue(numberFormat, visibleRange.max)));
        return getScale().estimateVisibleItemCount(labelWidth, labelMetrics.getHeight(), TARGET_VISIBLE_STEP_COUNT);
    }

    private void updateStepMagnitude(double stepExponent, double stepBase) {
        this.stepExponent = stepExponent;
        this.stepBase = stepBase;
        roundingUnit = (stepExponent > 0.0) ? Math.pow(stepBase, stepExponent) : Math.pow(stepBase, -stepExponent);
        if (isAutoNumberFormat()) {
            refreshAutoNumberFormat();
        }
    }

    private void setResolvedNumberFormat(NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    private void refreshAutoNumberFormat() {
        setResolvedNumberFormat(createAutoNumberFormat(stepExponent, stepBase));
    }

    private void setResolvedStepUnit(double resolvedStepUnit) {
        if (resolvedStepUnit == stepUnit) {
            return;
        }

        stepUnit = resolvedStepUnit;
        if (resolvedStepUnit == 0.0) {
            updateStepMagnitude(0.0, stepBase);
            flags.setFlag(FLAG_ROUND_STEP_VALUES, false);
            return;
        }

        updateStepMagnitude(floorLogBase(resolvedStepUnit, stepBase), stepBase);
        double unitScale = Math.pow(stepBase, stepExponent);
        double unitMultiplier = resolvedStepUnit / unitScale;
        flags.setFlag(FLAG_ROUND_STEP_VALUES, Math.floor(unitMultiplier) == unitMultiplier);
    }

    private NumberFormat createAutoNumberFormat(double stepExponent, double stepBase) {
        NumberFormat autoNumberFormat = (NumberFormat) createLocaleNumberFormat().clone();
        if (stepExponent >= 0.0) {
            if (stepExponent > 4.0 && autoNumberFormat instanceof DecimalFormat decimalFormat) {
                decimalFormat.applyPattern("0.######E0");
            } else {
                autoNumberFormat.setMaximumFractionDigits(2);
            }
            return autoNumberFormat;
        }

        if (stepExponent < -4.0 && autoNumberFormat instanceof DecimalFormat decimalFormat) {
            decimalFormat.applyPattern("0.######E0");
            return autoNumberFormat;
        }

        int decimalExponent = (stepBase == 10.0)
                ? (int) Math.round(stepExponent)
                : (int) Math.round(Math.log(Math.pow(stepBase, stepExponent)) / Math.log(10.0));
        autoNumberFormat.setMinimumFractionDigits(-decimalExponent);
        return autoNumberFormat;
    }

    /// Formats one prepared major or minor tick label.
    ///
    /// When [#usesTransformedVisibleRange()] is active, the stored axis value is projected through
    /// the owning transformer before formatting so affine zoom or scale transforms are reflected in
    /// the label text. Transformer failures are treated as an unlabelled tick.
    @Override
    public String computeLabel(double value) {
        if (usesTransformedVisibleRange()) {
            AxisTransformer axisTransformer = getAxisTransformer();
            if (axisTransformer != null) {
                try {
                    return formatValue(getNumberFormat(), axisTransformer.apply(value));
                } catch (AxisTransformerException e) {
                    return "";
                }
            }
        }
        return formatValue(getNumberFormat(), value);
    }

    /// Resolves the automatic major step size for the current visible range.
    ///
    /// [#SIMPLE_MODE] delegates to the visible-span-only heuristic. All other modes use the
    /// label-aware heuristic so label width can force coarser steps than span alone would suggest.
    ///
    /// @return the major-step unit that should be used after the next [#update()]
    protected double computeStepUnit() {
        return getAutoMode() == SIMPLE_MODE ? computeSimpleStepUnit() : computeLabelAwareStepUnit();
    }

    @Override
    boolean usesTransformedVisibleRange() {
        return getAxisTransformer() instanceof AffineAxisTransformer;
    }

    private void setResolvedSubStepUnit(double resolvedSubStepUnit) {
        subStepUnit = resolvedSubStepUnit;
    }

    final DataInterval getEffectiveVisibleRange() {
        Axis axis = getAxis();
        if (axis == null) {
            return new DataInterval();
        }
        return !usesTransformedVisibleRange() ? axis.getVisibleRange() : axis.getTVisibleRange();
    }

    private double roundStepValue(double value) {
        if (!flags.getFlag(FLAG_ROUND_STEP_VALUES)) {
            return value;
        }
        if (stepExponent < 0.0) {
            return Math.floor(value * roundingUnit + 0.5) / roundingUnit;
        }
        return Math.floor(value / roundingUnit + 0.5) * roundingUnit;
    }

    /// Returns the automatic major-step heuristic currently in effect.
    public final int getAutoMode() {
        return autoMode;
    }

    /// Returns the formatter currently used for tick labels.
    ///
    /// Explicit formatters supplied through [#setNumberFormat(NumberFormat)] are retained and
    /// returned as-is. Otherwise this returns either the cached automatically managed formatter or
    /// a fresh locale formatter when no cached auto formatter has been resolved yet.
    public NumberFormat getNumberFormat() {
        return numberFormat == null ? createLocaleNumberFormat() : numberFormat;
    }

    /// Returns the current resolved major-step unit.
    ///
    /// In automatic mode this may trigger one preparation pass so the returned value matches the
    /// scale's current visible range.
    public final double getStepUnit() {
        Scale scale = getScale();
        if (isAutoStepUnit() && scale != null && !scale.getSteps().hasPreparedValues()) {
            scale.getSteps().prepare();
        }
        return stepUnit;
    }

    /// Returns the number of minor ticks inserted between successive major ticks.
    ///
    /// When [#setSubStepCount(int)] is active, this reports that explicit count. Otherwise the
    /// value is derived from the current resolved major and minor step units, which may trigger one
    /// preparation pass when those values are still automatic.
    public int getSubStepCount() {
        prepareDerivedStepValuesIfNeeded();
        double effectiveSubStepUnit = getEffectiveSubStepUnit();
        if (flags.getFlag(FLAG_EXPLICIT_SUB_STEP_COUNT)) {
            return subStepCount;
        }
        return effectiveSubStepUnit == 0.0 ? 0 : (int) (stepUnit / subStepUnit) - 1;
    }

    /// Returns the current resolved minor-step unit.
    ///
    /// This may trigger one preparation pass when the minor-step spacing is still automatic or is
    /// derived from an explicit [#setSubStepCount(int)] on top of an automatic major step unit.
    public final double getSubStepUnit() {
        prepareDerivedStepValuesIfNeeded();
        return subStepUnit;
    }

    /// Returns whether this definition currently exposes a distinct minor-tick sequence.
    @Override
    public boolean hasSubStep() {
        return getEffectiveSubStepUnit() > 0.0;
    }

    private NumberFormat createLocaleNumberFormat() {
        return NumberFormatFactory.getInstance(getLocale());
    }

    /// Returns the next major tick after `value`.
    @Override
    public double incrementStep(double value) {
        return roundStepValue(value + stepUnit);
    }

    /// Returns the next minor tick after `value`.
    @Override
    public double incrementSubStep(double value) {
        return value + subStepUnit;
    }

    /// Returns whether label formatting is managed automatically from the current step magnitude.
    public final boolean isAutoNumberFormat() {
        return flags.getFlag(FLAG_AUTO_NUMBER_FORMAT);
    }

    /// Returns whether the major-step unit is resolved automatically from the visible range.
    public final boolean isAutoStepUnit() {
        return flags.getFlag(FLAG_AUTO_STEP_UNIT);
    }

    /// Returns whether the minor-step unit is resolved automatically from the current major step.
    public final boolean isAutoSubStepUnit() {
        return flags.getFlag(FLAG_AUTO_SUB_STEP_UNIT);
    }

    private double getEffectiveSubStepUnit() {
        return (subStepUnit > stepUnit) ? 0.0 : subStepUnit;
    }

    private void updateStepUnit() {
        if (getAxis() == null) {
            setResolvedStepUnit(DEFAULT_STEP_UNIT);
            setResolvedSubStepUnit(0.0);
            return;
        }
        setResolvedStepUnit(computeStepUnit());
    }

    private double computeSimpleStepUnit() {
        return chooseNiceStepUnit(getEffectiveVisibleRange().getLength());
    }

    private double computeLabelAwareStepUnit() {
        DataInterval visibleRange = getEffectiveVisibleRange();
        double visibleSpan = visibleRange.getLength();
        if (visibleSpan == 0.0) {
            return 0.0;
        }

        int estimatedLabelCapacity = Math.max(2,
                estimateVisibleStepCapacity(visibleRange, getNumberFormat()));
        double candidateStepUnit = visibleSpan / (estimatedLabelCapacity - 1);
        double candidateStepExponent = floorLogBase(candidateStepUnit, stepBase);
        double unitScale = Math.pow(stepBase, candidateStepExponent);

        if (stepBase == 10.0 && candidateStepUnit > 2.0 * unitScale) {
            if (candidateStepUnit <= 5.0 * unitScale) {
                candidateStepUnit = 5.0 * unitScale;
            } else {
                candidateStepExponent += 1.0;
                unitScale = Math.pow(stepBase, candidateStepExponent);
                candidateStepUnit = unitScale;
            }
        }

        if (candidateStepUnit == stepUnit) {
            return candidateStepUnit;
        }

        int visibleStepCount = (int) Math.floor(visibleSpan / candidateStepUnit) + 1;
        if (isAutoNumberFormat()) {
            NumberFormat autoNumberFormat = createAutoNumberFormat(candidateStepExponent, stepBase);
            estimatedLabelCapacity = Math.max(2,
                    estimateVisibleStepCapacity(visibleRange, autoNumberFormat));
            if (visibleStepCount > estimatedLabelCapacity) {
                if (stepUnit != 0.0) {
                    candidateStepUnit = stepUnit;
                } else {
                    double overflowRatio = visibleStepCount / (double) estimatedLabelCapacity;
                    candidateStepUnit = (overflowRatio <= 2.0)
                            ? 2.0 * unitScale
                            : (overflowRatio <= 5.0) ? 5.0 * unitScale : Math.ceil(overflowRatio) * unitScale;
                }
            }
        }
        return candidateStepUnit;
    }

    private void updateAutoSubStepUnit() {
        if (stepUnit <= 0.0) {
            setResolvedSubStepUnit(0.0);
            return;
        }

        double candidateSubStepUnit = Math.pow(stepBase, stepExponent);
        int majorUnitMultiplier = (int) Math.round(stepUnit / candidateSubStepUnit);
        if (majorUnitMultiplier == 1) {
            candidateSubStepUnit /= stepBase;
            majorUnitMultiplier = (int) stepBase;
        }

        if (getAxis() != null) {
            int scaleLength = getScale().getScaleLength();
            int visibleStepCount = (int) Math.floor(getEffectiveVisibleRange().getLength() / stepUnit) + 1;
            if (scaleLength < 4 * visibleStepCount * majorUnitMultiplier) {
                setResolvedSubStepUnit(0.0);
                return;
            }
        }
        setResolvedSubStepUnit(candidateSubStepUnit);
    }

    private void prepareDerivedStepValuesIfNeeded() {
        Scale scale = getScale();
        if (scale != null
                && !scale.getSteps().hasPreparedValues()
                && (isAutoStepUnit() || isAutoSubStepUnit() || flags.getFlag(FLAG_EXPLICIT_SUB_STEP_COUNT))) {
            scale.getSteps().prepare();
        }
    }

    /// Rebuilds the cached automatic formatter after the owning chart locale changes.
    ///
    /// Explicit formatters supplied through [#setNumberFormat(NumberFormat)] are left untouched.
    @Override
    public void localeChanged() {
        if (isAutoNumberFormat() && numberFormat != null) {
            refreshAutoNumberFormat();
        }
    }

    /// Returns the greatest major tick not greater than `value`.
    @Override
    public double previousStep(double value) {
        return (stepUnit == 0.0) ? value : roundStepValue(Math.floor(value / stepUnit) * stepUnit);
    }

    /// Returns the greatest minor tick not greater than `value`.
    @Override
    public double previousSubStep(double value) {
        return (subStepUnit == 0.0) ? value : Math.floor(value / subStepUnit) * subStepUnit;
    }

    /// Switches the automatic major-step heuristic.
    ///
    /// Changing the mode invalidates prepared values and layout on the owning scale. Callers
    /// should use [#SIMPLE_MODE] or [#COMPLEX_MODE].
    ///
    /// @param autoMode the automatic major-step heuristic to enable
    public void setAutoMode(int autoMode) {
        this.autoMode = autoMode;
        refreshScale();
    }

    /// Enables or disables automatic formatter management.
    ///
    /// In [#COMPLEX_MODE] this also invalidates prepared values because formatted label width feeds
    /// back into major-step selection. In [#SIMPLE_MODE] the current implementation preserves the
    /// historical behavior of leaving prepared values intact.
    ///
    /// @param autoNumberFormat whether label formatting should follow the current step magnitude
    public void setAutoNumberFormat(boolean autoNumberFormat) {
        if (isAutoNumberFormat() == autoNumberFormat) {
            return;
        }
        flags.setFlag(FLAG_AUTO_NUMBER_FORMAT, autoNumberFormat);
        if (autoNumberFormat) {
            refreshAutoNumberFormat();
        }
        if (getAutoMode() != SIMPLE_MODE) {
            refreshScale();
        }
    }

    /// Enables or disables automatic major-step selection.
    ///
    /// @param autoStepUnit whether [#update()] should resolve the major step from the visible range
    public void setAutoStepUnit(boolean autoStepUnit) {
        if (isAutoStepUnit() == autoStepUnit) {
            return;
        }
        flags.setFlag(FLAG_AUTO_STEP_UNIT, autoStepUnit);
        refreshScale();
    }

    /// Enables or disables automatic minor-step selection.
    ///
    /// Turning automatic selection on clears any explicit sub-step-count mode previously installed
    /// through [#setSubStepCount(int)].
    ///
    /// @param autoSubStepUnit whether [#update()] should resolve the minor step automatically
    public void setAutoSubStepUnit(boolean autoSubStepUnit) {
        if (isAutoSubStepUnit() == autoSubStepUnit) {
            return;
        }
        setAutoSubStepUnitMode(autoSubStepUnit);
        refreshScale();
    }

    /// Replaces the formatter used for tick labels.
    ///
    /// The supplied formatter is retained by reference. Passing `null` disables automatic
    /// formatter management but still falls back to a locale formatter created on demand, so the
    /// visible effect is "use default locale formatting without width-driven auto adjustments."
    ///
    /// @param numberFormat explicit formatter to retain, or `null` to use the locale default
    public void setNumberFormat(NumberFormat numberFormat) {
        setResolvedNumberFormat(numberFormat);
        setAutoNumberFormat(false);
        if (getAutoMode() != SIMPLE_MODE) {
            refreshScale();
        }
    }

    /// Replaces the major-step unit directly.
    ///
    /// This disables automatic major-step selection. The unit is also used as the basis for
    /// explicit minor-step-count mode configured through [#setSubStepCount(int)].
    ///
    /// @param stepUnit the major-step spacing to use
    public void setStepUnit(double stepUnit) {
        if (stepUnit == this.stepUnit && !isAutoStepUnit()) {
            return;
        }
        setResolvedStepUnit(stepUnit);
        setAutoStepUnit(false);
        refreshScale();
    }

    /// Requests a fixed number of minor ticks between successive major ticks.
    ///
    /// This disables automatic minor-step selection and records that future [#update()] calls
    /// should derive the actual minor-step unit from the current major step plus this count. A
    /// count of `0` suppresses distinct minor ticks.
    ///
    /// @param subStepCount the number of minor ticks to place between major ticks
    public void setSubStepCount(int subStepCount) {
        if (subStepCount == this.subStepCount
                && flags.getFlag(FLAG_EXPLICIT_SUB_STEP_COUNT)
                && !isAutoSubStepUnit()) {
            return;
        }
        this.subStepCount = subStepCount;
        setAutoSubStepUnitMode(false);
        flags.setFlag(FLAG_EXPLICIT_SUB_STEP_COUNT, true);
        refreshScale();
    }

    /// Replaces the minor-step unit directly.
    ///
    /// This disables automatic minor-step selection and clears any explicit minor-step-count mode.
    ///
    /// @param subStepUnit the minor-step spacing to use
    public void setSubStepUnit(double subStepUnit) {
        if (subStepUnit == this.subStepUnit
                && !flags.getFlag(FLAG_EXPLICIT_SUB_STEP_COUNT)
                && !isAutoSubStepUnit()) {
            return;
        }
        setResolvedSubStepUnit(subStepUnit);
        setAutoSubStepUnitMode(false);
        refreshScale();
    }

    /// Refreshes the resolved major step, minor step, and auto formatter from the owning scale.
    ///
    /// Automatic major and minor steps are recomputed first. As a safety fallback, any
    /// configuration that would otherwise expose more than roughly `500` visible major steps is
    /// coarsened to about ten visible intervals. When [#setSubStepCount(int)] owns minor-step
    /// resolution, the final minor-step spacing is always recomputed from the current major unit.
    @Override
    public void update() {
        if (getScale() == null) {
            setResolvedStepUnit(DEFAULT_STEP_UNIT);
            setResolvedSubStepUnit(0.0);
            return;
        }

        if (isAutoStepUnit()) {
            updateStepUnit();
        }
        if (isAutoSubStepUnit()) {
            updateAutoSubStepUnit();
        }

        double visibleSpan = getEffectiveVisibleRange().getLength();
        if (stepUnit == 0.0 || visibleSpan > 500.0 * stepUnit) {
            setResolvedStepUnit(visibleSpan / 10.0);
        }

        if (flags.getFlag(FLAG_EXPLICIT_SUB_STEP_COUNT)) {
            setResolvedSubStepUnit((subStepCount == 0) ? 0.0 : stepUnit / (subStepCount + 1));
        }
    }
}
