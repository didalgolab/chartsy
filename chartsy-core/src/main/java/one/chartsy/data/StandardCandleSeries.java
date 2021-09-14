package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;

import java.util.function.ToDoubleFunction;

public class StandardCandleSeries extends StandardIndexedSymbolResourceData<Candle> implements CandleSeries {

    public StandardCandleSeries(SymbolResource<Candle> resource, Dataset<Candle> dataset) {
        super(resource, dataset);
    }

    @Override
    public DoubleSeries mapToDouble(ToDoubleFunction<Candle> mapper) {
        return new StandardDoubleSeries(getTimeline(), getData().mapToDouble(mapper));
    }

    @Override
    public String toString() {
        return getResource() + ": " + getData();
    }
}
