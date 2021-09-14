package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.collections.PackedDataset;

import java.util.Collection;
import java.util.function.ToDoubleFunction;

public interface CandleSeries extends IndexedSymbolResourceData<Candle> {

    static CandleSeries from(SymbolResource<Candle> resource, Collection<? extends Candle> values) {
        return new StandardCandleSeries(resource, PackedDataset.of(values));
    }

    DoubleSeries mapToDouble(ToDoubleFunction<Candle> mapper);
}
