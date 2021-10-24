package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.market.Tick;
import one.chartsy.data.packed.PackedDataset;
import one.chartsy.data.packed.PackedTickSeries;

import java.util.Collection;

public interface TickSeries extends Series<Tick> {

    static TickSeries of(SymbolResource<Tick> resource, Collection<? extends Tick> values) {
        return new PackedTickSeries(resource, PackedDataset.of(values));
    }

    static <T extends Tick> TickSeries from(Series<T> series) {
        if (series instanceof TickSeries)
            return (TickSeries) series;

        return new PackedTickSeries((SymbolResource<Tick>) series.getResource(), (Dataset<Tick>) series.getData());
    }

    default TimeFrame getTimeFrame() {
        return getResource().timeFrame();
    }

    default DoubleSeries prices() {
        return mapToDouble(Tick::price);
    }

    default DoubleSeries volumes() {
        return mapToDouble(Tick::size);
    }

}
