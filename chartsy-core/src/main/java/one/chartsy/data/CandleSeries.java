package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.collections.PackedDataset;

import java.util.Collection;
import java.util.function.ToDoubleFunction;

public interface CandleSeries extends IndexedSymbolResourceData<Candle> {

    static CandleSeries of(SymbolResource<Candle> resource, Collection<? extends Candle> values) {
        return new StandardCandleSeries(resource, PackedDataset.of(values));
    }

    default TimeFrame getTimeFrame() {
        return getResource().timeFrame();
    }

    default DoubleSeries closes() {
        return mapToDouble(Candle::close);
    }

    default DoubleSeries volumes() {
        return mapToDouble(Candle::volume);
    }

    DoubleSeries mapToDouble(ToDoubleFunction<Candle> mapper);

}
