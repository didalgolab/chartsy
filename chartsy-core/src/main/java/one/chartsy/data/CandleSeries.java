package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.packed.PackedDataset;

import java.util.Collection;

public interface CandleSeries extends Series<Candle> {

    static CandleSeries of(SymbolResource<Candle> resource, Collection<? extends Candle> values) {
        return new PackedCandleSeries(resource, PackedDataset.of(values));
    }

    static <T extends Candle> CandleSeries from(Series<T> series) {
        if (series instanceof CandleSeries)
            return (CandleSeries) series;

        return new PackedCandleSeries((SymbolResource<Candle>) series.getResource(), (Dataset<Candle>) series.getData());
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

}
