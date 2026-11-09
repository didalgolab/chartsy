package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.account.ProfitConversion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.ExecutionMode.*;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExecutionOptionsTest {
    private static final SymbolIdentity EURUSD = SymbolIdentity.of("EURUSD");
    private static final SymbolIdentity GBPUSD = SymbolIdentity.of("GBPUSD");

    @Test void constructors_existing_signatures_keep_the_scalar_spread_as_the_default() {
        var options = List.of(new BacktestExecutionOptions(10000, .0003),
                new BacktestExecutionOptions(10000, .0003, KEEP_OPEN),
                new BacktestExecutionOptions(10000, .0003, KEEP_OPEN, SHARED),
                new BacktestExecutionOptions(10000, .0003, KEEP_OPEN, SHARED, ProfitConversion.QUOTE_CURRENCY),
                new BacktestExecutionOptions(10000, .0003, KEEP_OPEN, SHARED, ProfitConversion.QUOTE_CURRENCY, TICK_FIXED_SPREAD));
        for (var option : options) {
            assertTrue(option.symbolSpreads().isEmpty());
            assertEquals(.0003, option.spreadFor(EURUSD));
            assertEquals(.0003, option.spreadFor(GBPUSD));
        }
    }

    @Test void spreadFor_copies_overrides_and_keeps_fallback_and_zero_spread() {
        var overrides = new HashMap<SymbolIdentity, Double>();
        overrides.put(GBPUSD, .0005);
        var options = options(overrides);
        overrides.put(GBPUSD, .005);
        assertEquals(.0003, options.spreadFor(EURUSD));
        assertEquals(.0005, options.spreadFor(GBPUSD));
        assertThrows(UnsupportedOperationException.class, () -> options.symbolSpreads().put(EURUSD, 0.0));
        assertEquals(0, options(Map.of(GBPUSD, 0.0)).spreadFor(GBPUSD));
    }

    @Test void constructor_rejects_invalid_symbol_spreads() {
        for (double value : List.of(-.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
            assertThrows(IllegalArgumentException.class, () -> options(Map.of(GBPUSD, value)));
        var missing = new HashMap<SymbolIdentity, Double>();
        missing.put(GBPUSD, null);
        assertThrows(NullPointerException.class, () -> options(missing));
        assertThrows(NullPointerException.class, () -> options(null));
    }

    private static BacktestExecutionOptions options(Map<SymbolIdentity, Double> overrides) {
        return new BacktestExecutionOptions(10000, .0003, KEEP_OPEN, SHARED,
                ProfitConversion.QUOTE_CURRENCY, CANDLE_FIXED_SPREAD, overrides);
    }
}
