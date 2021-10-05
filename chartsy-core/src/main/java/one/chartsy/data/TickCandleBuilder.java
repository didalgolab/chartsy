package one.chartsy.data;

import one.chartsy.CandleBuilder;
import one.chartsy.data.market.Tick;

public class TickCandleBuilder extends CandleBuilder.From<Tick> {

    @Override
    public void put(Tick t) {
        time = t.getTime();
        open = high = low = close = t.price();
        volume = t.volume();
        count = 1;
        setPresent();
    }

    @Override
    public void merge(Tick t) {
        if (!isPresent()) {
            put(t);
        } else {
            time = t.getTime();
            var price = t.price();
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += t.volume();
            count++;
        }
    }
}
