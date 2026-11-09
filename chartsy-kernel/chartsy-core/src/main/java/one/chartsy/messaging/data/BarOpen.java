package one.chartsy.messaging.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;

/** An opening observation. No unfinished high, low or close is exposed. */
public record BarOpen(SymbolIdentity symbol, long barId, long time, double open) implements MarketEvent { }
