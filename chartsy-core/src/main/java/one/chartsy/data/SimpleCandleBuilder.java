package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;

public class SimpleCandleBuilder extends CandleBuilder.From<Candle> {

    @Override
    public void put(Candle c) {
        time = c.getTime();
        open = c.open();
        high = c.high();
        low = c.low();
        close = c.close();
        volume = c.volume();
        count = c.count();
        setPresent();
    }

    @Override
    public void merge(Candle c) {
        if (!isPresent()) {
            put(c);
        } else {
            time = c.getTime();
            high = Math.max(high, c.high());
            low = Math.min(low, c.low());
            close = c.close();
            volume += c.volume();
            count += c.count();
        }
    }
}
