package one.chartsy.trade.account;

import one.chartsy.SymbolIdentity;

/**
 * Converts a price-derived profit into account currency at a valuation or fill event.
 * For fixed symbol, price and time, implementations must be deterministic and side-effect-free,
 * preserve zero, and be proportional to quote profit. Position resizing and risk-per-unit
 * sizing rely on those properties. A missing rate must fail explicitly rather than use
 * a future rate; the account ledger rejects non-finite converted amounts before mutation.
 */
@FunctionalInterface
public interface ProfitConversion {
    /** Instrument quote currency already equals account currency. */
    ProfitConversion QUOTE_CURRENCY = (symbol, quoteProfit, price, time) -> quoteProfit;

    /**
     * Instrument base currency equals account currency, e.g. USD/CHF in a USD account.
     * Converts at the executable valuation/fill price, without a separate conversion charge.
     */
    ProfitConversion BASE_CURRENCY = (symbol, quoteProfit, price, time) -> {
        if (!Double.isFinite(price) || price <= 0)
            throw new IllegalArgumentException("Currency conversion requires a positive finite price");
        return quoteProfit / price;
    };

    /**
     * @param quoteProfit signed profit in the instrument's quote currency
     * @param price executable position valuation or fill price; for sizing, the proposed stop price
     * @param time event timestamp; an implementation using other rates must use only rates known by this event
     */
    double toAccountCurrency(SymbolIdentity symbol, double quoteProfit, double price, long time);
}
