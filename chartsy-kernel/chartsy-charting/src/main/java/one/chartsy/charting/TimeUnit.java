package one.chartsy.charting;

import one.chartsy.charting.util.CalendarUtil;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/// Describes a calendar-aware unit used to place and label time-axis ticks.
///
/// Implementations define three core operations:
/// - `previousUnitTime(Calendar)`: floor a timestamp to the current unit boundary.
/// - `incrementTime(Calendar)`: move one unit forward from a boundary.
/// - `getFormatString(...)`: provide the date-format pattern used for labels.
///
/// The built-in singleton instances (`MILLISECOND` through `CENTURY`) are used by
/// chart-axis step selection and label generation. Their `getMillis()` values are
/// approximate magnitudes intended for ordering and spacing heuristics.
///
/// **Mutation model:** all calendar-transforming methods mutate the provided
/// `Calendar` instance and return that same instance.
public abstract class TimeUnit implements Serializable {

    /// Implements century granularity for long-range time axes.
    ///
    /// This unit aligns timestamps to years divisible by `100` and advances by full
    /// 100-year steps. Labels use the locale-resolved `TimeUnit.CENTURY.Format`
    /// pattern with a fallback of `yyyy`.
    static class CenturyUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.CENTURY.Format", "yyyy");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The default fallback pattern renders the aligned century boundary year.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// The returned value is a nominal magnitude used for spacing heuristics.
        @Override
        public double getMillis() {
            return 3.1536E12;
        }

        /// {@inheritDoc}
        ///
        /// Adds exactly 100 calendar years using `Calendar#add`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.YEAR, 100);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the calendar to the start of the current year and then rewrites
        /// the year to the current century boundary.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            int year = cal.get(Calendar.YEAR) / 100 * 100;
            CalendarUtil.yearFloor(cal);
            cal.set(Calendar.YEAR, year);
            return cal;
        }

        @Override
        public String toString() {
            return "CENTURY UNIT";
        }
    }

    /// Implements day granularity for date-axis ticks.
    ///
    /// This is the default coarse unit used by several charting entry points when
    /// no finer timeframe is inferred. Labels use `TimeUnit.DAY.Format` with a
    /// fallback pattern of `E dd`.
    static class DayUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.DAY.Format", "E dd");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern includes short weekday and day-of-month.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This value is nominal (`24h`) and is used for spacing heuristics, not as
        /// an exact elapsed-duration guarantee across daylight-saving transitions.
        @Override
        public double getMillis() {
            return 8.64E7;
        }

        /// {@inheritDoc}
        ///
        /// Uses calendar-day arithmetic (`DAY_OF_MONTH`) rather than fixed
        /// millisecond addition.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the timestamp to local midnight in the calendar's current zone.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.dayFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "DAY UNIT";
        }
    }

    /// Implements decade granularity for long-horizon date axes.
    ///
    /// This unit aligns timestamps to years divisible by `10` and advances in
    /// 10-year calendar steps. Labels use `TimeUnit.DECADE.Format` with a fallback
    /// pattern of `yyyy`.
    static class DecadeUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.DECADE.Format", "yyyy");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern emits a four-digit aligned year.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// The returned value is nominal and is used for relative spacing decisions.
        @Override
        public double getMillis() {
            return 3.1536E11;
        }

        /// {@inheritDoc}
        ///
        /// Adds exactly 10 calendar years using `Calendar#add`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.YEAR, 10);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the timestamp to the start of its year and then rewrites the year
        /// to the containing decade boundary.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            int year = cal.get(Calendar.YEAR) / 10 * 10;
            CalendarUtil.yearFloor(cal);
            cal.set(Calendar.YEAR, year);
            return cal;
        }

        @Override
        public String toString() {
            return "DECADE UNIT";
        }
    }

    /// Implements hour granularity for intraday axis labels and step alignment.
    ///
    /// This unit is used as the canonical one-hour base in adaptive intraday
    /// planning and in multiplied steps such as 2h/3h/6h/8h. Labels use
    /// `TimeUnit.HOUR.Format` with a fallback pattern of `HH:mm`.
    static class HourUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.HOUR.Format", "HH:mm");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern renders hour and minute in 24-hour form.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This value is nominal (`3_600_000`) and is used for spacing and
        /// comparison heuristics rather than exact elapsed duration.
        @Override
        public double getMillis() {
            return 3_600_000.0;
        }

        /// {@inheritDoc}
        ///
        /// Uses calendar-hour arithmetic (`HOUR_OF_DAY`) so date rollovers are
        /// handled by `Calendar`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the timestamp to the beginning of the current local hour.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.hourFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "HOUR UNIT";
        }
    }

    /// Memoizes locale-specific values looked up by resource key with a fallback.
    ///
    /// Each locale is resolved lazily on first access and then cached in this map.
    /// Resolution failures (missing bundle/key or lookup exceptions) are treated as
    /// cacheable misses and fall back to the constructor-provided default value.
    ///
    /// The current charting module ships no `TimeUnit` localization bundle, so this
    /// helper currently serves mainly as a per-locale fallback cache.
    static class LocaleDependent<T> extends HashMap<Locale, T> {
        private final String resourceKey;
        private final T fallbackValue;

        /// Creates a cache bound to one resource key and one fallback value.
        ///
        /// @param resourceKey the bundle key to resolve for each locale
        /// @param fallbackValue the value used when lookup cannot provide a value
        LocaleDependent(String resourceKey, T fallbackValue) {
            this.resourceKey = resourceKey;
            this.fallbackValue = fallbackValue;
        }

        /// Returns the cached or resolved value for `locale`.
        ///
        /// On first access for a locale, this method attempts to read
        /// `resourceKey` from the locale's resource bundle. If that lookup yields
        /// no value, `fallbackValue` is cached and returned.
        ///
        /// @param locale the lookup locale
        /// @return the localized value, or the fallback when unavailable
        @SuppressWarnings("unchecked")
        public T get(Locale locale) {
            T value = super.get(locale);
            if (value == null) {
                try {
                    ResourceBundle bundle = getCachedBundle(locale);
                    if (bundle != null)
                        value = (T) bundle.getObject(resourceKey);
                } catch (Exception ignored) {
                }
                if (value == null)
                    value = fallbackValue;
                super.put(locale, value);
            }
            return value;
        }
    }

    /// Implements millisecond granularity for the finest supported time-axis ticks.
    ///
    /// This unit acts as the smallest built-in base for auto-unit selection and
    /// multiplied units. Labels use `TimeUnit.MILLISECOND.Format` with a fallback
    /// pattern of `HH:mm:ss.SSS`.
    static class MillisecondUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.MILLISECOND.Format", "HH:mm:ss.SSS");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern renders time down to millisecond precision.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// Millisecond is the base smallest nominal duration and is used as an
        /// exact `1.0` reference in unit comparisons.
        @Override
        public double getMillis() {
            return 1.0;
        }

        /// {@inheritDoc}
        ///
        /// Advances by one calendar millisecond using `Calendar#add`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.MILLISECOND, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// No flooring is applied because this unit already represents the finest
        /// supported calendar granularity.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            return cal;
        }

        @Override
        public String toString() {
            return "MILLISECOND UNIT";
        }
    }

    /// Implements minute granularity for intraday tick alignment and labeling.
    ///
    /// This is the canonical one-minute unit used by fixed-duration mappings and
    /// by multiplied intraday steps. Labels use `TimeUnit.MINUTE.Format` with a
    /// fallback pattern of `HH:mm`.
    static class MinuteUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.MINUTE.Format", "HH:mm");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern renders hour and minute in 24-hour form.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This value is nominal (`60_000`) and is used for step-size comparison
        /// and spacing heuristics.
        @Override
        public double getMillis() {
            return 60_000.0;
        }

        /// {@inheritDoc}
        ///
        /// Uses calendar-minute arithmetic (`Calendar.MINUTE`) so hour/day rollover
        /// is handled by `Calendar`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.MINUTE, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the timestamp to the start of the current local minute.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.minuteFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "MINUTE UNIT";
        }
    }

    /// Implements calendar-month granularity for medium-range axis planning.
    ///
    /// This unit is used when charts step in whole months and when lane planning
    /// transitions from day-level ticks toward year-level ticks. Labels use
    /// `TimeUnit.MONTH.Format` with a fallback pattern of `MMM yy`.
    static class MonthUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.MONTH.Format", "MMM yy");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern renders an abbreviated month plus two-digit year.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This is a nominal 30-day duration (`2_592_000_000`) used for unit
        /// ordering and spacing heuristics, not a precise elapsed-month guarantee.
        @Override
        public double getMillis() {
            return 2.592E9;
        }

        /// {@inheritDoc}
        ///
        /// Uses calendar-month arithmetic (`Calendar.MONTH`) so year rollover and
        /// calendar-specific month transitions are delegated to `Calendar`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.MONTH, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors the timestamp to the first day of the current month at local
        /// midnight.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.monthFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "MONTH UNIT";
        }
    }

    /// Implements calendar-quarter granularity for medium-to-long horizon axes.
    ///
    /// Labels combine a localized quarter prefix (`TimeUnit.QUARTER.String`,
    /// fallback `Q`) with a year fragment formatted from
    /// `TimeUnit.QUARTER.Format` (fallback `yy`), for example `Q1 24`.
    static class QuarterUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.QUARTER.Format", "yy");
        private static final LocaleDependent<String> QUARTER_PREFIX =
                new LocaleDependent<>("TimeUnit.QUARTER.String", "Q");
        private static final int[] QUARTER_START_MONTHS = {0, 3, 6, 9, 12};

        /// {@inheritDoc}
        ///
        /// The quarter number is derived from the calendar month and prepended to
        /// the base formatted date text.
        @Override
        public String format(DateFormat dateFormat, Calendar cal, Locale locale) {
            int quarterIndex = 0;
            while (quarterIndex < 4 && cal.get(Calendar.MONTH) >= QUARTER_START_MONTHS[quarterIndex + 1])
                quarterIndex++;
            return QUARTER_PREFIX.get(locale) + (quarterIndex + 1) + " " + super.format(dateFormat, cal, locale);
        }

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern contributes the year fragment rendered after the
        /// quarter prefix.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This is a nominal 90-day duration (`7_776_000_000`) used for ordering
        /// and spacing heuristics.
        @Override
        public double getMillis() {
            return 7.776E9;
        }

        /// {@inheritDoc}
        ///
        /// Advances by one calendar quarter using three calendar-month steps.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.MONTH, 3);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors to the start of the current month, then rewrites month to the
        /// containing quarter boundary (`Jan`, `Apr`, `Jul`, or `Oct`).
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            int month = cal.get(Calendar.MONTH);
            CalendarUtil.monthFloor(cal);
            int quarterIndex = 0;
            while (quarterIndex < 4 && month >= QUARTER_START_MONTHS[quarterIndex + 1])
                quarterIndex++;
            cal.set(Calendar.MONTH, QUARTER_START_MONTHS[quarterIndex]);
            return cal;
        }

        @Override
        public String toString() {
            return "QUARTER UNIT";
        }
    }

    /// Implements one-second granularity for short-horizon time axes.
    ///
    /// This unit is used as the canonical one-second step in automatic unit
    /// selection and in fixed-multiple compositions such as 2s, 4s, or 10s.
    /// Labels resolve `TimeUnit.SECOND.Format` with fallback `HH:mm:ss`.
    static class SecondUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.SECOND.Format", "HH:mm:ss");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern includes second precision in 24-hour format.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// The value (`1_000`) is nominal and is used for ordering and spacing
        /// heuristics.
        @Override
        public double getMillis() {
            return 1_000.0;
        }

        /// {@inheritDoc}
        ///
        /// Advances by one calendar second.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.SECOND, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors to the start of the current second in the calendar time zone.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.secondFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "SECOND UNIT";
        }
    }

    /// Implements calendar-week granularity for medium-range timeline axes.
    ///
    /// This unit is used when step planning crosses from day-scale labels to
    /// coarser date buckets (for example in financial category timelines). Labels
    /// use `TimeUnit.WEEK.Format` with a fallback pattern of `'W'w`.
    static class WeekUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.WEEK.Format", "'W'w");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern emits the week number token used by
        /// `DateFormat` in the active locale/calendar system.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This is a nominal 7-day duration (`604_800_000`) used for
        /// ordering and spacing heuristics.
        @Override
        public double getMillis() {
            return 6.048E8;
        }

        /// {@inheritDoc}
        ///
        /// Advances by one calendar week using `Calendar.WEEK_OF_YEAR`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors to the first day of the current calendar week at local
        /// midnight, following `Calendar#getFirstDayOfWeek()`.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.weekFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "WEEK UNIT";
        }
    }

    /// Implements calendar-year granularity for long-range timeline axes.
    ///
    /// This unit is selected once month-level granularity becomes too dense for
    /// labeling and lane planning. Labels use `TimeUnit.YEAR.Format` with a
    /// fallback pattern of `yyyy`.
    static class YearUnit extends TimeUnit {
        private static final LocaleDependent<String> FORMAT =
                new LocaleDependent<>("TimeUnit.YEAR.Format", "yyyy");

        @Override
        public String getFormatString() {
            return getFormatString(Locale.getDefault());
        }

        /// {@inheritDoc}
        ///
        /// The fallback pattern emits the four-digit calendar year.
        @Override
        public String getFormatString(Locale locale) {
            return FORMAT.get(locale);
        }

        /// {@inheritDoc}
        ///
        /// This is a nominal year duration (`31_536_000_000`) used for
        /// ordering and spacing heuristics.
        @Override
        public double getMillis() {
            return 3.1536E10;
        }

        /// {@inheritDoc}
        ///
        /// Advances by one calendar year using `Calendar.YEAR`.
        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.add(Calendar.YEAR, 1);
            return cal;
        }

        /// {@inheritDoc}
        ///
        /// Floors to the start of the current year at local midnight.
        @Override
        public Calendar previousUnitTime(Calendar cal) {
            CalendarUtil.yearFloor(cal);
            return cal;
        }

        @Override
        public String toString() {
            return "YEAR UNIT";
        }
    }

    private static final Map<Locale, ResourceBundle> RESOURCE_BUNDLE_CACHE = new HashMap<>();

    /// Singleton time unit representing one millisecond.
    public static final TimeUnit MILLISECOND = new MillisecondUnit();
    /// Singleton time unit representing one second.
    public static final TimeUnit SECOND = new SecondUnit();
    /// Singleton time unit representing one minute.
    public static final TimeUnit MINUTE = new MinuteUnit();
    /// Singleton time unit representing one hour.
    public static final TimeUnit HOUR = new HourUnit();
    /// Singleton time unit representing one day.
    public static final TimeUnit DAY = new DayUnit();
    /// Singleton time unit representing one week.
    public static final TimeUnit WEEK = new WeekUnit();
    /// Singleton time unit representing one month.
    public static final TimeUnit MONTH = new MonthUnit();
    /// Singleton time unit representing one calendar quarter.
    public static final TimeUnit QUARTER = new QuarterUnit();
    /// Singleton time unit representing one year.
    public static final TimeUnit YEAR = new YearUnit();
    /// Singleton time unit representing one decade.
    public static final TimeUnit DECADE = new DecadeUnit();
    /// Singleton time unit representing one century.
    public static final TimeUnit CENTURY = new CenturyUnit();

    private static ResourceBundle getCachedBundle(Locale locale) {
        ResourceBundle bundle = RESOURCE_BUNDLE_CACHE.get(locale);
        if (bundle == null)
            RESOURCE_BUNDLE_CACHE.put(locale, bundle);
        return bundle;
    }

    protected TimeUnit() {
    }

    /// Formats a calendar instant using the supplied formatter.
    ///
    /// This default implementation ignores locale-specific overrides and delegates
    /// directly to `dateFormat`.
    ///
    /// @param dateFormat the formatter to apply
    /// @param cal the calendar holding the instant to format
    /// @return formatted timestamp text
    public String format(DateFormat dateFormat, Calendar cal) {
        return dateFormat.format(cal.getTime());
    }

    /// Formats a calendar instant for a locale-aware call site.
    ///
    /// The base implementation behaves exactly like `format(DateFormat, Calendar)`.
    /// Units may override this method when the label text depends on locale beyond
    /// the formatter pattern itself, for example quarter prefixes.
    ///
    /// @param dateFormat the formatter to apply
    /// @param cal the calendar holding the instant to format
    /// @param locale the locale requested by the caller
    /// @return formatted timestamp text
    public String format(DateFormat dateFormat, Calendar cal, Locale locale) {
        return dateFormat.format(cal.getTime());
    }

    /// Returns the default date-format pattern for this unit.
    ///
    /// The pattern is consumed by `DateFormatFactoryExt` in axis-step label
    /// generation.
    ///
    /// @return a `DateFormat` pattern string
    public abstract String getFormatString();

    /// Returns the locale-specific date-format pattern for this unit.
    ///
    /// The default implementation delegates to `getFormatString()` and ignores
    /// locale. Units with localized pattern resources may override this method.
    ///
    /// @param locale the target locale
    /// @return a `DateFormat` pattern string
    public String getFormatString(Locale locale) {
        return getFormatString();
    }

    /// Returns the nominal duration of this unit in milliseconds.
    ///
    /// The value is used for relative comparisons and spacing heuristics. Calendar
    /// units with variable physical duration (for example months or years) expose an
    /// average-like constant rather than an exact elapsed-time guarantee.
    ///
    /// @return nominal unit duration in milliseconds
    public abstract double getMillis();

    /// Moves `cal` forward by one unit.
    ///
    /// Implementations mutate and return the same calendar instance.
    ///
    /// @param cal the calendar to advance
    /// @return the same `cal` instance after mutation
    public abstract Calendar incrementTime(Calendar cal);

    /// Floors `cal` to the start of this unit.
    ///
    /// Implementations mutate and return the same calendar instance.
    ///
    /// @param cal the calendar to align
    /// @return the same `cal` instance after mutation
    public abstract Calendar previousUnitTime(Calendar cal);
}
