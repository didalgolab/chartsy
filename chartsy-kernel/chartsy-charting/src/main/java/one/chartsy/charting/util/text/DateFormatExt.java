package one.chartsy.charting.util.text;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

/// Wraps a pattern-based {@link SimpleDateFormat} together with the locale used to create it.
///
/// {@link DateFormatFactoryExt} caches instances of this type for chart axis label generation. This
/// wrapper keeps the pattern, locale, and optional time zone together while rejecting API calls
/// that would replace the underlying calendar, number formatter, or time zone after construction.
///
/// Instances remain mutable through {@link #setLenient(boolean)} and are not thread-safe, just
/// like {@link DateFormat} and {@link SimpleDateFormat}.
///
/// ### API Note
///
/// The historically named ICU-language registry is still exposed for callers that configure
/// preferred languages centrally. Current instances still delegate formatting and parsing to
/// {@link SimpleDateFormat} for every locale; the registry only preserves a legacy construction-time
/// distinction in how formatter-creation failures surface.
public class DateFormatExt extends DateFormat {
    private static final Set<String> preferredIcuLanguages = new HashSet<>(Set.of(
            "pl", "gu", "hi", "kn", "mr", "pa", "sa", "ar", "as", "bn", "bo", "fa",
            "ml", "ne", "or", "sk", "ta", "te"));

    private final SimpleDateFormat delegate;

    private final Locale locale;

    /// Creates a formatter backed by {@link SimpleDateFormat} for `pattern` and `locale`.
    ///
    /// A `null` locale falls back to {@link Locale#getDefault()}. When `timeZone` is not `null`,
    /// it is applied immediately after the delegate is created.
    ///
    /// @param pattern  pattern string understood by {@link SimpleDateFormat}
    /// @param locale   locale that supplies localized date symbols, or `null` to use the default
    ///                 locale
    /// @param timeZone time zone fixed on the delegate, or `null` to keep the delegate default
    public DateFormatExt(String pattern, Locale locale, TimeZone timeZone) {
        Locale effectiveLocale = Objects.requireNonNullElse(locale, Locale.getDefault());
        this.locale = effectiveLocale;
        delegate = createDelegate(pattern, effectiveLocale);
        if (timeZone != null)
            delegate.setTimeZone(timeZone);
    }

    /// Registers a language in the legacy ICU-preference registry.
    ///
    /// The language code is normalized before storage so the old ISO aliases `iw`, `in`, and `ji`
    /// are treated the same as `he`, `id`, and `yi`. Existing formatter instances are unaffected,
    /// and current code does not switch to a different formatter implementation for registered
    /// languages.
    ///
    /// @param languageCode ISO language code to record for later formatter construction
    public static void registerPreferenceForICU(String languageCode) {
        preferredIcuLanguages.add(normalizeLegacyLanguageCode(languageCode));
    }

    private static SimpleDateFormat createDelegate(String pattern, Locale locale) {
        boolean preferredIcuLanguage = preferredIcuLanguages
                .contains(normalizeLegacyLanguageCode(locale.getLanguage()));
        try {
            return new SimpleDateFormat(pattern, locale);
        } catch (IllegalArgumentException ex) {
            if (preferredIcuLanguage)
                throw ex;
            throw new AssertionError("shouldn't happen", ex);
        }
    }

    private static String normalizeLegacyLanguageCode(String languageCode) {
        return switch (Objects.requireNonNull(languageCode, "languageCode")) {
            case "iw" -> "he";
            case "in" -> "id";
            case "ji" -> "yi";
            default -> languageCode;
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DateFormatExt other = (DateFormatExt) obj;
        return isLenient() == other.isLenient()
                && toPattern().equals(other.toPattern())
                && locale.equals(other.locale)
                && getTimeZone().equals(other.getTimeZone());
    }

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        return delegate.format(date, toAppendTo, fieldPosition);
    }

    /// Returns a defensive copy of the delegate calendar.
    ///
    /// Replacing the calendar is unsupported because factory-created instances are intended to keep
    /// their formatting collaborators stable after construction.
    @Override
    public Calendar getCalendar() {
        return (Calendar) delegate.getCalendar().clone();
    }

    /// Returns the locale that was resolved at construction time.
    ///
    /// This is the locale used to create the underlying {@link SimpleDateFormat}, including the
    /// default-locale fallback applied when the constructor receives `null`.
    public Locale getLocale() {
        return locale;
    }

    @Override
    public NumberFormat getNumberFormat() {
        return delegate.getNumberFormat();
    }

    @Override
    public TimeZone getTimeZone() {
        return delegate.getTimeZone();
    }

    @Override
    public int hashCode() {
        return Objects.hash(toPattern(), locale, getTimeZone(), isLenient());
    }

    @Override
    public boolean isLenient() {
        return delegate.isLenient();
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
        return delegate.parse(source, pos);
    }

    /// Always throws because this wrapper keeps its calendar fixed once created.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public void setCalendar(Calendar calendar) {
        throw new UnsupportedOperationException();
    }

    /// Changes the delegate leniency used for subsequent parsing operations.
    ///
    /// This is the only mutator deliberately forwarded to the underlying formatter. Code that
    /// shares one instance should therefore treat leniency as shared mutable state.
    @Override
    public void setLenient(boolean lenient) {
        delegate.setLenient(lenient);
    }

    /// Always throws because this wrapper keeps its number formatter fixed once created.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public void setNumberFormat(NumberFormat numberFormat) {
        throw new UnsupportedOperationException();
    }

    /// Always throws because this wrapper keeps its time zone fixed once created.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public void setTimeZone(TimeZone timeZone) {
        throw new UnsupportedOperationException();
    }

    /// Returns the {@link SimpleDateFormat} pattern currently used by the delegate.
    public String toPattern() {
        return delegate.toPattern();
    }
}
