package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.account.ProfitConversion;

import java.util.Map;

/** Full-fill execution in one account currency; candle behavior is the default and financing is excluded. */
public record BacktestExecutionOptions(double initialBalance, double spread, EndOfDataPolicy endOfDataPolicy,
                                       CapitalMode capitalMode, ProfitConversion profitConversion, ExecutionMode executionMode,
                                       Map<SymbolIdentity, Double> symbolSpreads) {
    public enum ExecutionMode {
        /** Existing bid-OHLC execution with the configured fixed spread. */
        CANDLE_FIXED_SPREAD,
        /** Ordered bid quotes with ask synthesized from the configured spread. */
        TICK_FIXED_SPREAD,
        /** Ordered native bid/ask quotes; configured fixed spread is unused. */
        TICK_NATIVE_SPREAD;

        public boolean usesQuotes() { return this != CANDLE_FIXED_SPREAD; }
    }
    public enum EndOfDataPolicy { LIQUIDATE, KEEP_OPEN }
    public enum CapitalMode {
        /** Each account is funded separately; the run report describes the default account. */
        INDEPENDENT_ACCOUNTS,
        /** One funded portfolio; named accounts own independent positions and contribute only their PnL. */
        SHARED
    }
    public static final BacktestExecutionOptions DEFAULT = new BacktestExecutionOptions(10_000, 0);

    public BacktestExecutionOptions(double initialBalance, double spread) {
        this(initialBalance, spread, EndOfDataPolicy.LIQUIDATE);
    }

    public BacktestExecutionOptions(double initialBalance, double spread, EndOfDataPolicy endOfDataPolicy) {
        this(initialBalance, spread, endOfDataPolicy, CapitalMode.INDEPENDENT_ACCOUNTS);
    }

    public BacktestExecutionOptions(double initialBalance, double spread, EndOfDataPolicy endOfDataPolicy,
                                    CapitalMode capitalMode) {
        this(initialBalance, spread, endOfDataPolicy, capitalMode, ProfitConversion.QUOTE_CURRENCY);
    }

    public BacktestExecutionOptions(double initialBalance, double spread, EndOfDataPolicy endOfDataPolicy,
                                    CapitalMode capitalMode, ProfitConversion profitConversion) {
        this(initialBalance, spread, endOfDataPolicy, capitalMode, profitConversion, ExecutionMode.CANDLE_FIXED_SPREAD);
    }

    public BacktestExecutionOptions(double initialBalance, double spread, EndOfDataPolicy endOfDataPolicy,
                                    CapitalMode capitalMode, ProfitConversion profitConversion, ExecutionMode executionMode) {
        this(initialBalance, spread, endOfDataPolicy, capitalMode, profitConversion, executionMode, Map.of());
    }

    /** Fixed spread in price units, with the scalar spread as fallback; unused in native quote mode. */
    public double spreadFor(SymbolIdentity symbol) {
        return symbolSpreads.getOrDefault(symbol, spread);
    }

    public BacktestExecutionOptions {
        if (!Double.isFinite(initialBalance) || initialBalance <= 0)
            throw new IllegalArgumentException("initialBalance must be positive and finite");
        if (!Double.isFinite(spread) || spread < 0)
            throw new IllegalArgumentException("spread must be nonnegative and finite");
        symbolSpreads = Map.copyOf(symbolSpreads);
        for (double value : symbolSpreads.values())
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Symbol spreads must be nonnegative and finite");
        if (executionMode == null)
            throw new IllegalArgumentException("executionMode is required");
        if (endOfDataPolicy == null)
            throw new IllegalArgumentException("endOfDataPolicy is required");
        if (profitConversion == null)
            throw new IllegalArgumentException("profitConversion is required");
        if (capitalMode == null)
            throw new IllegalArgumentException("capitalMode is required");
    }
}
