package one.chartsy.charting;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/// Represents a composite `TimeUnit` that advances in fixed multiples of a base unit.
///
/// The instance delegates formatting to `baseUnit` and scales the nominal duration by
/// `multiplier`. Boundary alignment used by `previousUnitTime(Calendar)` follows this
/// priority:
/// - explicit `alignmentReference` passed by the caller,
/// - reference derived from `anchorUnit` (if present),
/// - calendar-field bucketing when the base field range is divisible by `multiplier`,
/// - millisecond bucketing relative to reference time (or Unix epoch if absent).
///
/// Instances are immutable and can be shared safely. Calendar methods still mutate and
/// return the caller-provided `Calendar`.
public class MultipleTimeUnit extends TimeUnit {
    private final TimeUnit baseUnit;
    private final int multiplier;
    private final int calendarField;
    private final int fieldRange;
    private final Date alignmentReference;
    private final TimeUnit anchorUnit;
    
    /// Creates a multiplied unit without an explicit alignment reference.
    ///
    /// @param baseUnit the supported base unit to multiply
    /// @param multiplier positive multiplication factor applied to `baseUnit`
    /// @throws IllegalArgumentException if `multiplier` is not positive or
    ///     `baseUnit` is not one of the supported units (`MILLISECOND` through
    ///     `YEAR`)
    public MultipleTimeUnit(TimeUnit baseUnit, int multiplier) throws IllegalArgumentException {
        this(baseUnit, multiplier, null, null);
    }
    
    /// Creates a multiplied unit aligned to a fixed reference instant.
    ///
    /// The reference is used when flooring values in
    /// `previousUnitTime(Calendar)`.
    ///
    /// @param baseUnit the supported base unit to multiply
    /// @param multiplier positive multiplication factor applied to `baseUnit`
    /// @param alignmentReference fixed alignment instant, or `null` to use dynamic
    ///     alignment rules
    /// @throws IllegalArgumentException if `multiplier` is not positive or
    ///     `baseUnit` is not one of the supported units (`MILLISECOND` through
    ///     `YEAR`)
    public MultipleTimeUnit(TimeUnit baseUnit, int multiplier, Date alignmentReference)
            throws IllegalArgumentException {
        this(baseUnit, multiplier, alignmentReference, null);
    }
    
    /// Creates a multiplied unit with optional reference and anchor unit.
    ///
    /// When `alignmentReference` is `null` and `anchorUnit` is present, the anchor
    /// is first floored on a clone of the target calendar and that time becomes the
    /// effective alignment reference.
    ///
    /// @param baseUnit the supported base unit to multiply
    /// @param multiplier positive multiplication factor applied to `baseUnit`
    /// @param alignmentReference optional fixed alignment instant
    /// @param anchorUnit optional unit used to derive alignment when no fixed
    ///     reference is provided
    /// @throws IllegalArgumentException if `multiplier` is not positive or
    ///     `baseUnit` is not one of the supported units (`MILLISECOND` through
    ///     `YEAR`)
    MultipleTimeUnit(TimeUnit baseUnit, int multiplier, Date alignmentReference, TimeUnit anchorUnit)
            throws IllegalArgumentException {
        this.baseUnit = baseUnit;
        this.multiplier = multiplier;
        if (multiplier <= 0)
            throw new IllegalArgumentException("Invalid unit multiplier");
        if (baseUnit == TimeUnit.MILLISECOND) {
            calendarField = Calendar.MILLISECOND;
            fieldRange = 1000;
        } else if (baseUnit == TimeUnit.SECOND) {
            calendarField = Calendar.SECOND;
            fieldRange = 60;
        } else if (baseUnit == TimeUnit.MINUTE) {
            calendarField = Calendar.MINUTE;
            fieldRange = 60;
        } else if (baseUnit == TimeUnit.HOUR) {
            calendarField = Calendar.HOUR_OF_DAY;
            fieldRange = 24;
        } else if (baseUnit == TimeUnit.DAY) {
            calendarField = Calendar.DAY_OF_MONTH;
            fieldRange = 1;
        } else if (baseUnit == TimeUnit.WEEK) {
            calendarField = Calendar.WEEK_OF_YEAR;
            fieldRange = 1;
        } else if (baseUnit == TimeUnit.MONTH) {
            calendarField = Calendar.MONTH;
            fieldRange = 1;
        } else {
            if (baseUnit != TimeUnit.YEAR)
                throw new IllegalArgumentException("Invalid base unit");
            calendarField = Calendar.YEAR;
            fieldRange = 1;
        }
        this.alignmentReference = alignmentReference;
        this.anchorUnit = anchorUnit;
    }
    
    /// Returns the raw multiplier used by tick subdivision logic.
    ///
    /// This package-private accessor is used by `TimeStepsDefinition` to derive
    /// candidate subdivision factors for already-multiplied units.
    int getMultiplier() {
        return multiplier;
    }
    
    /// {@inheritDoc}
    ///
    /// The format pattern is always delegated to `baseUnit`.
    @Override
    public String getFormatString() {
        return baseUnit.getFormatString();
    }
    
    /// {@inheritDoc}
    ///
    /// The locale-specific format pattern is always delegated to `baseUnit`.
    @Override
    public String getFormatString(Locale locale) {
        return baseUnit.getFormatString(locale);
    }
    
    /// {@inheritDoc}
    ///
    /// Returns `multiplier * baseUnit.getMillis()` using the base unit's nominal
    /// duration semantics.
    @Override
    public double getMillis() {
        return multiplier * baseUnit.getMillis();
    }
    
    /// {@inheritDoc}
    ///
    /// For `multiplier == 1`, delegates directly to `baseUnit` to preserve base-unit
    /// increment semantics.
    @Override
    public Calendar incrementTime(Calendar cal) {
        if (multiplier == 1)
            return baseUnit.incrementTime(cal);
        cal.add(calendarField, multiplier);
        return cal;
    }
    
    /// {@inheritDoc}
    ///
    /// The method first floors to the base unit, then aligns to the multiplied grid
    /// using the class alignment policy described in the type documentation.
    @Override
    public Calendar previousUnitTime(Calendar cal) {
        Calendar floored = baseUnit.previousUnitTime(cal);
        Date reference = alignmentReference;
        if (anchorUnit != null)
            if (reference == null)
                reference = anchorUnit.previousUnitTime((Calendar) floored.clone()).getTime();
        block: {
                if (reference == null)
                    if (multiplier == 1)
                        break block;
                if (reference == null)
                    if (fieldRange % multiplier == 0) {
                        int fieldValue = floored.get(calendarField);
                        fieldValue = fieldValue / multiplier * multiplier;
                        floored.set(calendarField, fieldValue);
                        break block;
                    }
                long stepMillis = multiplier * (long) baseUnit.getMillis();
                long timeMillis = floored.getTimeInMillis();
                if (reference != null)
                    timeMillis = timeMillis - reference.getTime();
                timeMillis = timeMillis / stepMillis * stepMillis;
                if (reference != null)
                    timeMillis = timeMillis + reference.getTime();
                floored.setTimeInMillis(timeMillis);
            } // end block
        
        return floored;
    }
    
    @Override
    public String toString() {
        return multiplier + " " + baseUnit;
    }
}
