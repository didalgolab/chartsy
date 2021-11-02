package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Clock;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstrumentUniverse {

    private final Map<SymbolIdentity, InstrumentState> symbols = new HashMap<>();
    private final InstrumentState[] instruments;

    public InstrumentUniverse(List<? extends Series<?>> datasets) {
        this.instruments = new InstrumentState[datasets.size()];

        for (int i = 0; i < datasets.size(); i++) {
            Series<?> series = datasets.get(i);
            instruments[i] = symbols.computeIfAbsent(series.getResource().symbol(),
                    symb -> new InstrumentState(new SymbolIdentifier(symb)));

        }
    }

    public InstrumentState getInstrument(When when) {
        return getInstrument(when.getId());
    }

    public InstrumentState getInstrument(int datasetId) {
        return instruments[datasetId];
    }

    public int totalCount() {
        return symbols.size();
    }

    public int activeCountSince(long lastTradeTime) {
        int count = 0;
        for (InstrumentState instrument : symbols.values())
            if (instrument.isActiveSince(lastTradeTime))
                count++;

        return count;
    }
}
