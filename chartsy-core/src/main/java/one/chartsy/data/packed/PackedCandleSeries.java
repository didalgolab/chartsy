package one.chartsy.data.packed;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Dataset;

public class PackedCandleSeries extends PackedSeries<Candle> implements CandleSeries {

    public PackedCandleSeries(SymbolResource<Candle> resource, Dataset<Candle> dataset) {
        super(resource, dataset);
    }

    @Override
    public String toString() {
        return getResource() + ": " /*+ getData()*/;
    }
}
