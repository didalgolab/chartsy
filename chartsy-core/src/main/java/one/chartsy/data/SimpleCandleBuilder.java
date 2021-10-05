package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;

public class SimpleCandleBuilder implements CandleBuilder<Candle> {

    private boolean present;
    protected long time;
    protected double open;
    protected double high;
    protected double low;
    protected double close;
    protected double volume;
    protected int count;


    @Override
    public boolean isPresent() {
        return present;
    }

    @Override
    public SimpleCandle get() {
        if (isPresent())
            return new SimpleCandle(time, open, high, low, close, volume, count);
        else
            return null;
    }

    @Override
    public void put(Candle c) {
        time = c.getTime();
        open = c.open();
        high = c.high();
        low = c.low();
        close = c.close();
        volume = c.volume();
        count = c.count();
        present = true;
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
