package one.chartsy.charting;

import java.awt.FontMetrics;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.text.DateFormatFactoryExt;

/// Supplies calendar-aware major and minor tick sequences for time-based axes.
///
/// [Scale#setTimeUnit(TimeUnit)] installs this definition when a scale should step in calendar
/// units instead of fixed numeric increments. The definition maintains one resolved major
/// [TimeUnit] and one optional sub-step unit:
/// - the major unit may be fixed explicitly or chosen automatically from [#getAutoUnits()]
/// - the sub-step unit may be fixed explicitly, derived from an explicit [#setSubStepCount(int)],
///   or chosen automatically from the current major unit and visible range
///
/// Automatic selection balances two constraints. [#computeTimeUnit()] chooses the unit whose
/// nominal duration keeps the visible range inside a practical tick-count window while still
/// leaving enough display-space for representative labels. [#computeAutoSubStepUnit()] then looks
/// for a finer unit that yields a usable minor-tick density inside the current major interval.
///
/// Time arithmetic uses the installed [TimeUnit] implementations rather than raw millisecond
/// addition, so month, quarter, year, and daylight-saving transitions follow calendar rules. The
/// nominal `getMillis()` values are used only for ordering and spacing heuristics.
///
/// Instances cache locale-sensitive `Calendar` and `DateFormat` state, are mutable, and are not
/// thread-safe.
public class TimeStepsDefinition extends StepsDefinition {

    /// Orders candidate units from the finest nominal duration to the coarsest.
    ///
    /// [#setAutoUnits(TimeUnit[])] sorts caller-supplied arrays with this comparator before they
    /// are stored so automatic unit selection can scan them in ascending order.
    private static final class TimeUnitComparator implements Comparator<TimeUnit> {
        private static final TimeUnitComparator INSTANCE = new TimeUnitComparator();

        private TimeUnitComparator() {
        }

        /// Compares units by their nominal [TimeUnit#getMillis()] magnitude.
        @Override
        public int compare(TimeUnit left, TimeUnit right) {
            return Double.compare(left.getMillis(), right.getMillis());
        }
    }

    private static final TimeUnit[] DEFAULT_AUTO_UNITS = {
            TimeUnit.MILLISECOND,
            TimeUnit.SECOND,
            TimeUnit.MINUTE,
            TimeUnit.HOUR,
            TimeUnit.DAY,
            TimeUnit.WEEK,
            TimeUnit.MONTH,
            TimeUnit.QUARTER,
            TimeUnit.YEAR,
            TimeUnit.DECADE,
            TimeUnit.CENTURY
    };

    /// Bit `0x1` keeps major-unit selection automatic.
    /// Bit `0x2` follows the previous parallel scale when auto-unit selection is disabled.
    /// Bit `0x4` keeps sub-step selection automatic.
    /// Bit `0x8` records that [#setSubStepCount(int)] owns sub-step resolution.
    private final Flags flags;

    /// The currently resolved major tick unit.
    private TimeUnit unit;

    /// The currently resolved sub-step unit, or `null` when no distinct sub-step sequence exists.
    private TimeUnit subStepUnit;

    /// Explicit sub-step count requested through [#setSubStepCount(int)].
    private int subStepCount;

    /// Ascending candidate list consulted by automatic major-unit selection.
    protected TimeUnit[] autoUnits;

    /// Reusable locale-aware calendar used for step flooring and incrementing.
    private Calendar scratchCalendar;

    /// Cached formatter for labels produced by the current resolved major unit.
    private DateFormat labelDateFormat;

    /// Creates a time steps definition with automatic major and minor unit selection.
    ///
    /// The initial fallback major unit is [TimeUnit#DAY] until the definition is attached to a
    /// scale and [#update()] chooses a better fit for the current visible range.
    public TimeStepsDefinition() {
        flags = new Flags(1);
        unit = TimeUnit.DAY;
        autoUnits = DEFAULT_AUTO_UNITS;
        initializeCalendars();
        refreshLabelDateFormat();
    }

    /// Recreates locale-sensitive calendar helpers after construction or locale changes.
    void initializeCalendars() {
        scratchCalendar = Calendar.getInstance(getLocale());
    }

    /// Enables or disables following the previous parallel scale when auto-unit selection is off.
    void setFollowPreviousParallelScale(boolean followPreviousParallelScale) {
        flags.setFlag(2, followPreviousParallelScale);
        refreshScale();
    }

    private boolean followsPreviousParallelScale() {
        return flags.getFlag(2);
    }

    private Calendar toScratchCalendar(Date date) {
        scratchCalendar.setTime(date);
        return scratchCalendar;
    }

    private Calendar toCalendar(double value) {
        return toScratchCalendar(new Date((long) value));
    }

    private String formatLabel(DateFormat dateFormat, TimeUnit timeUnit, Locale locale, double value) {
        return timeUnit.format(dateFormat, toCalendar(value), locale);
    }

    private TimeUnit createSubStepUnit(int divisor) {
        int autoUnitIndex = autoUnits.length - 1;
        while (autoUnitIndex >= 0 && autoUnits[autoUnitIndex] != unit)
            autoUnitIndex--;

        while (autoUnitIndex >= 0) {
            long unitRatio = (long) (unit.getMillis() / autoUnits[autoUnitIndex].getMillis());
            if (unitRatio % divisor == 0L && unitRatio / divisor <= Integer.MAX_VALUE)
                return new MultipleTimeUnit(autoUnits[autoUnitIndex], (int) (unitRatio / divisor), null, unit);
            autoUnitIndex--;
        }

        int fallbackIndex = 0;
        while (fallbackIndex < autoUnits.length) {
            double multiplier = unit.getMillis() / autoUnits[fallbackIndex].getMillis() / divisor;
            if (multiplier <= Integer.MAX_VALUE)
                return new MultipleTimeUnit(autoUnits[fallbackIndex], (int) (multiplier + 0.5), null, unit);
            fallbackIndex++;
        }

        for (TimeUnit defaultUnit : DEFAULT_AUTO_UNITS) {
            double multiplier = unit.getMillis() / defaultUnit.getMillis() / divisor;
            if (multiplier <= Integer.MAX_VALUE)
                return new MultipleTimeUnit(defaultUnit, (int) (multiplier + 0.5), null, unit);
        }
        return null;
    }

    private void setResolvedUnit(TimeUnit resolvedUnit) {
        if (unit == resolvedUnit)
            return;
        unit = resolvedUnit;
        refreshLabelDateFormat();
    }

    private void setResolvedSubStepUnit(TimeUnit resolvedSubStepUnit) {
        subStepUnit = resolvedSubStepUnit;
    }

    private void setAutoSubStepUnitMode(boolean autoSubStepUnit) {
        flags.setFlag(4, autoSubStepUnit);
        flags.setFlag(8, false);
    }

    private TimeUnit getNextAutoUnit(TimeUnit currentUnit) {
        int unitIndex = 0;
        while (unitIndex < autoUnits.length && currentUnit != autoUnits[unitIndex])
            unitIndex++;
        return (unitIndex >= autoUnits.length) ? TimeUnit.YEAR : autoUnits[Math.min(unitIndex + 1, autoUnits.length - 1)];
    }

    /// Applies the next larger automatic unit from the previous parallel time scale.
    void applyUnitFromPreviousParallelScale() {
        Scale scale = super.getScale();
        if (scale == null)
            return;
        Scale previousParallelScale = scale.getPreviousParallelScale();
        if (previousParallelScale == null)
            return;
        if (previousParallelScale.getStepsDefinition() instanceof TimeStepsDefinition previousStepsDefinition)
            setResolvedUnit(getNextAutoUnit(previousStepsDefinition.getUnit()));
    }

    /// Formats one tick label with the current resolved major unit.
    @Override
    public String computeLabel(double value) {
        return formatLabel(getLabelDateFormat(), unit, super.getLocale(), value);
    }

    /// Chooses the automatic major unit that best matches the current visible span and label width.
    ///
    /// Candidate units outside the rough `0.1 .. 200` visible-item window are ignored before label
    /// width is considered. The remaining candidates are scored against the number of labels the
    /// current scale length could fit for a representative sample label near the visible maximum.
    protected TimeUnit computeTimeUnit() {
        Axis axis = super.getAxis();
        if (axis == null)
            return TimeUnit.DAY;
        FontMetrics labelMetrics = super.getScale().getFontMetricsFor(super.getScale().getLabelFont());
        double visibleSpan = axis.getVisibleMax() - axis.getVisibleMin();
        double bestRelativeError = Double.MAX_VALUE;
        int bestUnitIndex = Integer.MAX_VALUE;
        Locale locale = super.getLocale();

        for (int unitIndex = 0; unitIndex < autoUnits.length; unitIndex++) {
            TimeUnit candidateUnit = autoUnits[unitIndex];
            double visibleItemCount = visibleSpan / candidateUnit.getMillis();
            if (visibleItemCount < 0.1 || visibleItemCount > 200.0)
                continue;

            DateFormat candidateFormat =
                    DateFormatFactoryExt.getInstance(candidateUnit.getFormatString(locale), locale, null);
            String sampleLabel = formatLabel(candidateFormat, candidateUnit, locale, axis.getVisibleMax());
            int estimatedVisibleItems =
                    super.getScale().estimateVisibleItemCount(labelMetrics.stringWidth(sampleLabel), labelMetrics.getHeight(), 6);
            double relativeError = Math.abs(visibleItemCount - estimatedVisibleItems) / estimatedVisibleItems;
            if (relativeError < bestRelativeError) {
                bestRelativeError = relativeError;
                bestUnitIndex = unitIndex;
            }
        }

        return (bestUnitIndex >= autoUnits.length) ? TimeUnit.YEAR : autoUnits[bestUnitIndex];
    }

    /// Uses transformed visible coordinates when the axis transformer is affine.
    @Override
    boolean usesTransformedVisibleRange() {
        return super.getAxisTransformer() instanceof AffineAxisTransformer;
    }

    /// Returns the preferred sub-step divisors for `timeUnit`.
    ///
    /// The returned values describe how many evenly spaced sub-intervals the current major unit can
    /// be split into while still producing culturally familiar labels such as 2, 4, 6, 12, or 24
    /// hours.
    private int[] getSubdivisionCandidates(TimeUnit timeUnit) {
        if (timeUnit == TimeUnit.MILLISECOND)
            return new int[0];
        if (timeUnit == TimeUnit.SECOND)
            return new int[] {2, 4, 10};
        if (timeUnit == TimeUnit.MINUTE || timeUnit == TimeUnit.HOUR)
            return new int[] {2, 4, 6, 10, 20, 30, 60};
        if (timeUnit == TimeUnit.DAY)
            return new int[] {2, 4, 6, 8, 12, 24};
        if (timeUnit == TimeUnit.WEEK)
            return new int[] {7};
        if (timeUnit == TimeUnit.MONTH)
            return new int[] {2, 4};
        if (timeUnit == TimeUnit.QUARTER)
            return new int[] {3};
        if (timeUnit == TimeUnit.YEAR)
            return new int[] {2, 4, 12};
        if (timeUnit == TimeUnit.DECADE)
            return new int[] {2, 10};
        if (timeUnit == TimeUnit.CENTURY)
            return new int[] {2, 4, 10};
        if (!(timeUnit instanceof MultipleTimeUnit multipleTimeUnit))
            return new int[0];

        return switch (multipleTimeUnit.getMultiplier()) {
            case 4 -> new int[] {2, 4};
            case 6 -> new int[] {2, 3};
            case 8 -> new int[] {2, 4, 8};
            case 10 -> new int[] {2, 5, 10};
            case 12 -> new int[] {2, 3, 4, 6, 12};
            case 15 -> new int[] {3, 5, 15};
            case 20 -> new int[] {2, 4, 5, 10, 20};
            case 24 -> new int[] {2, 3, 4, 6, 8, 12, 24};
            case 30 -> new int[] {2, 3, 5, 6, 10, 15, 30};
            default -> new int[] {multipleTimeUnit.getMultiplier()};
        };
    }

    private DateFormat getLabelDateFormat() {
        return labelDateFormat;
    }

    /// Returns the automatic-unit candidates currently used by [#computeTimeUnit()].
    ///
    /// The returned array is a defensive copy. The stored sequence is always kept in ascending
    /// nominal-duration order.
    public TimeUnit[] getAutoUnits() {
        return autoUnits.clone();
    }

    /// Returns the current number of minor ticks inserted between successive major ticks.
    ///
    /// When [#setSubStepCount(int)] is active, this reports the explicit manual count. Otherwise
    /// the value is derived from the current resolved major and sub-step units.
    public int getSubStepCount() {
        Scale scale = super.getScale();
        if (isAutoSubStepUnit() && scale != null && !scale.getSteps().hasPreparedValues())
            scale.getSteps().prepare();
        return flags.getFlag(8)
                ? subStepCount
                : (subStepUnit == null) ? 0 : (int) (unit.getMillis() / subStepUnit.getMillis() + 0.5) - 1;
    }

    /// Returns the current resolved sub-step unit.
    ///
    /// In automatic mode this may trigger one preparation pass so the returned unit matches the
    /// scale's current visible range.
    public final TimeUnit getSubStepUnit() {
        Scale scale = super.getScale();
        if (isAutoSubStepUnit() && scale != null && !scale.getSteps().hasPreparedValues())
            scale.getSteps().prepare();
        return subStepUnit;
    }

    /// Returns the current resolved major unit.
    ///
    /// In automatic mode this may trigger one preparation pass so the returned unit matches the
    /// scale's current visible range.
    public final TimeUnit getUnit() {
        Scale scale = super.getScale();
        if (isAutoUnit() && scale != null && !scale.getSteps().hasPreparedValues())
            scale.getSteps().prepare();
        return unit;
    }

    /// Returns whether the current configuration exposes a distinct minor-tick sequence.
    @Override
    public boolean hasSubStep() {
        return subStepUnit != null;
    }

    /// Rebuilds the cached formatter for the current major unit and locale.
    void refreshLabelDateFormat() {
        Locale locale = super.getLocale();
        labelDateFormat = DateFormatFactoryExt.getInstance(unit.getFormatString(locale), locale, null);
    }

    /// Returns the next major tick after `value`.
    @Override
    public double incrementStep(double value) {
        return unit.incrementTime(toCalendar(value)).getTimeInMillis();
    }

    /// Returns the next minor tick after `value`.
    @Override
    public double incrementSubStep(double value) {
        return value + subStepUnit.getMillis();
    }

    /// Returns whether the sub-step unit is resolved automatically.
    public final boolean isAutoSubStepUnit() {
        return flags.getFlag(4);
    }

    /// Returns whether the major unit is resolved automatically.
    public final boolean isAutoUnit() {
        return flags.getFlag(1);
    }

    /// Returns the visible range used for major-unit and sub-step selection.
    ///
    /// When [#usesTransformedVisibleRange()] is enabled this is the transformer's visible-space
    /// range; otherwise it is the raw axis visible range.
    final DataInterval getEffectiveVisibleRange() {
        Axis axis = super.getAxis();
        if (axis == null)
            return new DataInterval();
        return !usesTransformedVisibleRange() ? axis.getVisibleRange() : axis.getTVisibleRange();
    }

    private void updateUnit() {
        if (super.getScale() == null) {
            setResolvedUnit(TimeUnit.DAY);
            return;
        }
        if (isAutoUnit())
            selectAutoUnit();
        else if (followsPreviousParallelScale())
            applyUnitFromPreviousParallelScale();
    }

    private void selectAutoUnit() {
        setResolvedUnit(computeTimeUnit());
    }

    /// Rebuilds locale-sensitive calendar and formatter state after the owning chart locale changes.
    @Override
    public void localeChanged() {
        super.localeChanged();
        initializeCalendars();
        refreshLabelDateFormat();
    }

    private void updateSubStepUnit() {
        if (super.getScale() == null) {
            setResolvedSubStepUnit(null);
            return;
        }
        if (isAutoSubStepUnit())
            setResolvedSubStepUnit(computeAutoSubStepUnit());
        else if (flags.getFlag(8))
            setResolvedSubStepUnit(createSubStepUnit(subStepCount + 1));
    }

    /// Chooses the automatic minor-tick unit for the current major unit and visible range.
    ///
    /// The algorithm tries to keep roughly one quarter of a major interval between successive minor
    /// ticks and then snaps that density to one of the familiar subdivision factors returned by
    /// [#getSubdivisionCandidates(TimeUnit)].
    private TimeUnit computeAutoSubStepUnit() {
        double visibleMajorUnits = getEffectiveVisibleRange().getLength() / unit.getMillis();
        int scaleLength = super.getScale().getScaleLength();
        double maxDivisions = (visibleMajorUnits <= 0.0) ? scaleLength / 4.0 : scaleLength / 4.0 / visibleMajorUnits;
        if (maxDivisions < 2.0)
            return null;

        int[] subdivisionCandidates = getSubdivisionCandidates(unit);
        for (int index = subdivisionCandidates.length - 1; index >= 0; index--) {
            if (subdivisionCandidates[index] <= maxDivisions)
                return createSubStepUnit(subdivisionCandidates[index]);
        }
        return null;
    }

    /// Returns the greatest major tick not greater than `value`.
    @Override
    public double previousStep(double value) {
        return unit.previousUnitTime(toCalendar(value)).getTimeInMillis();
    }

    /// Returns the greatest minor tick not greater than `value`.
    @Override
    public double previousSubStep(double value) {
        return subStepUnit.previousUnitTime(toCalendar(value)).getTimeInMillis();
    }

    /// Enables or disables automatic sub-step unit selection.
    ///
    /// Turning automatic selection on clears any explicit sub-step-count mode previously installed
    /// through [#setSubStepCount(int)].
    public void setAutoSubStepUnit(boolean autoSubStepUnit) {
        if (isAutoSubStepUnit() == autoSubStepUnit)
            return;
        setAutoSubStepUnitMode(autoSubStepUnit);
        refreshScale();
    }

    /// Enables or disables automatic major-unit selection.
    public void setAutoUnit(boolean autoUnit) {
        if (isAutoUnit() == autoUnit)
            return;
        flags.setFlag(1, autoUnit);
        refreshScale();
    }

    /// Replaces the candidate units consulted by automatic major-unit selection.
    ///
    /// The supplied array is copied and sorted by nominal [TimeUnit#getMillis()] magnitude.
    /// Passing `null` restores the built-in [#DEFAULT_AUTO_UNITS] sequence.
    public void setAutoUnits(TimeUnit[] autoUnits) {
        if (autoUnits == null) {
            this.autoUnits = DEFAULT_AUTO_UNITS;
        } else {
            this.autoUnits = autoUnits.clone();
            Arrays.sort(this.autoUnits, TimeUnitComparator.INSTANCE);
        }
        refreshScale();
    }

    /// Requests a fixed number of minor ticks between major ticks.
    ///
    /// This disables automatic sub-step selection and records that future updates should derive the
    /// actual sub-step unit from the current major unit plus this count.
    public void setSubStepCount(int subStepCount) {
        if (subStepCount == this.subStepCount && flags.getFlag(8) && !isAutoSubStepUnit())
            return;
        this.subStepCount = subStepCount;
        setAutoSubStepUnitMode(false);
        flags.setFlag(8, true);
        refreshScale();
    }

    /// Replaces the resolved minor-tick unit directly.
    ///
    /// This disables automatic sub-step selection and clears any explicit sub-step-count mode.
    public void setSubStepUnit(TimeUnit subStepUnit) {
        if (subStepUnit == this.subStepUnit && !flags.getFlag(8) && !isAutoSubStepUnit())
            return;
        setResolvedSubStepUnit(subStepUnit);
        setAutoSubStepUnitMode(false);
        refreshScale();
    }

    /// Replaces the resolved major tick unit directly.
    ///
    /// Passing the same unit again is ignored unless automatic unit selection is currently enabled.
    public void setUnit(TimeUnit unit) {
        if (this.unit == unit && !isAutoUnit())
            return;
        flags.setFlag(1, false);
        setResolvedUnit(unit);
        refreshScale();
    }

    /// Refreshes the current major and minor units for the next tick-generation pass.
    @Override
    public void update() {
        updateUnit();
        updateSubStepUnit();
    }
}
