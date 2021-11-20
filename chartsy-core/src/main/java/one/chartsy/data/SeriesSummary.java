package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.time.Chronological;

public class SeriesSummary<T extends Chronological> {
    private final SymbolResource<T> resource;
    private final int count;
    private final T first, last;


    public SeriesSummary(Series<T> series) {
        this.resource = series.getResource();
        this.count = series.length();
        this.first = (count == 0)? null : series.getFirst();
        this.last = (count == 0)? null : series.getLast();
    }

    public final SymbolResource<T> getResource() {
        return resource;
    }

    public final SymbolIdentity getSymbol() {
        return getResource().symbol();
    }

    public final int getCount() {
        return count;
    }

    public final T getFirst() {
        return first;
    }

    public final T getLast() {
        return last;
    }

    @Override
    public String toString() {
        return "\"SeriesSummary:" + resource.symbol().name() + "\": {" + count + ": [" + first + " .. " + last + "]}";
    }
}
