package one.chartsy.charting.util.text;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import one.chartsy.charting.util.collections.Pair;

/// Caches shared date-format instances used for chart time-axis labels.
///
/// The factory keys entries by pattern, resolved locale, and optional time zone, then reuses the
/// same {@link DateFormatExt} on subsequent lookups. The returned formatter is therefore shared
/// mutable state rather than a fresh instance.
///
/// ### API Note
///
/// Callers should treat the returned {@link DateFormat} as read-only. Mutating leniency or other
/// exposed collaborators on the shared formatter affects later callers that hit the same cache key.
public final class DateFormatFactoryExt {
    private static final Map<Object, DateFormat> cachedFormats = new HashMap<>(7);


    /// Returns the cached formatter for the requested formatting context.
    ///
    /// A `null` locale falls back to {@link Locale#getDefault()} before the cache key is built. A
    /// `null` time zone means the key only distinguishes pattern and locale, and the resulting
    /// formatter keeps the default time zone chosen by {@link DateFormatExt}.
    ///
    /// @param pattern  non-`null` {@link DateFormatExt} pattern string
    /// @param locale   locale whose date symbols should be used, or `null` to use the default
    ///                 locale
    /// @param timeZone time zone to pin on the cached formatter, or `null` to use the formatter
    ///                 default
    /// @return shared formatter cached for the supplied pattern, locale, and optional time zone
    /// @throws IllegalArgumentException if `pattern` is `null`
    public static synchronized DateFormat getInstance(String pattern, Locale locale, TimeZone timeZone) {
        if (pattern == null)
            throw new IllegalArgumentException("Pattern cannot be NULL");

        Locale effectiveLocale = Objects.requireNonNullElse(locale, Locale.getDefault());
        Object cacheKey = new Pair<>(pattern, effectiveLocale);
        if (timeZone != null)
            cacheKey = new Pair<>(cacheKey, timeZone);

        DateFormat format = cachedFormats.get(cacheKey);
        if (format == null) {
            format = new DateFormatExt(pattern, effectiveLocale, timeZone);
            cachedFormats.put(cacheKey, format);
        }
        return format;
    }

    private DateFormatFactoryExt() {
    }
}
