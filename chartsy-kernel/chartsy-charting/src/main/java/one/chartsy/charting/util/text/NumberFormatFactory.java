package one.chartsy.charting.util.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Caches shared numeric label formatters for chart components.
///
/// The factory keeps one {@link NumberFormat} per resolved {@link Locale} and reuses it for axis
/// labels, legend values, and other chart text that should follow the chart locale without
/// allocating a formatter on every paint pass.
///
/// ### API Note
///
/// Returned formatters are shared mutable objects. Callers should treat them as read-only because
/// changes to grouping, rounding, or symbols leak into later lookups of the same locale.
public final class NumberFormatFactory {
    private static final Map<Locale, NumberFormat> cachedFormats = new HashMap<>(7);

    private static NumberFormat createNumberFormat(Locale locale) {
        return new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(locale));
    }

    /// Returns the cached decimal formatter for `locale`.
    ///
    /// A `null` locale falls back to {@link Locale#getDefault()}. The created formatter uses the
    /// fixed pattern `0.###`, so chart labels omit grouping separators and show up to three
    /// fractional digits while still honoring the locale's decimal symbols.
    ///
    /// @param locale locale whose decimal symbols should be used, or `null` to use the default
    ///               locale
    /// @return shared formatter cached for the resolved locale
    public static synchronized NumberFormat getInstance(Locale locale) {
        Locale effectiveLocale = Objects.requireNonNullElse(locale, Locale.getDefault());
        return cachedFormats.computeIfAbsent(effectiveLocale, NumberFormatFactory::createNumberFormat);
    }

    private NumberFormatFactory() {
    }
}
