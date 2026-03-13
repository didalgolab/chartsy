package one.chartsy.charting;

import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Provides cached [DecimalFormatSymbols] lookups for locale-aware chart label formatting.
///
/// [CategoryStepsDefinition] is currently the only consumer. It uses this helper when a category
/// axis falls back to numeric labels and needs the locale's decimal separator without allocating a
/// formatter for each label.
///
/// Instances are cached per [Locale] and reused across lookups. Because
/// [DecimalFormatSymbols] remains mutable, callers inside this package should treat the returned
/// objects as read-only.
///
/// **Thread-safety:** cache access is serialized by [#getDecimalFormatSymbolsInstance(Locale)].
/// The returned symbol objects are shared and are not themselves synchronized.
class DecimalFormatSymbolsFactory {
    /// Stores the shared symbol instance for each locale requested by this package.
    private static final Map<Locale, DecimalFormatSymbols> CACHE = new HashMap<>(7);

    /// Creates an uncached symbol set from the JDK locale data for `locale`.
    ///
    /// @param locale the locale whose number punctuation should be loaded
    /// @return a new symbol set populated from the JDK locale data for `locale`
    private static DecimalFormatSymbols createDecimalFormatSymbols(Locale locale) {
        return DecimalFormatSymbols.getInstance(locale);
    }

    /// Returns the cached decimal-format symbols for `locale`.
    ///
    /// The first lookup creates a symbol set from the JDK locale data and stores that same object
    /// for later lookups of the same locale.
    ///
    /// Callers should extract the symbols they need and avoid mutating the returned instance.
    /// Mutations would leak into later lookups because this factory intentionally reuses one
    /// [DecimalFormatSymbols] object per locale.
    ///
    /// @param locale the locale whose decimal, grouping, and related number symbols are needed
    /// @return the shared symbol set cached for `locale`
    /// @throws NullPointerException if `locale` is `null`
    public static synchronized DecimalFormatSymbols getDecimalFormatSymbolsInstance(Locale locale) {
        Locale cacheKey = Objects.requireNonNull(locale, "locale");
        return CACHE.computeIfAbsent(cacheKey, DecimalFormatSymbolsFactory::createDecimalFormatSymbols);
    }

    private DecimalFormatSymbolsFactory() { }
}
