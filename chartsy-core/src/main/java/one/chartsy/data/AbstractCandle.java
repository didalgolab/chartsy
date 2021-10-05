package one.chartsy.data;

import one.chartsy.Candle;

public abstract class AbstractCandle implements Candle {

    private final SimpleCandle baseCandle;

    protected AbstractCandle(Candle c) {
        this.baseCandle = SimpleCandle.from(c);
    }

    public final SimpleCandle baseCandle() {
        return baseCandle;
    }

    @Override
    public final long getTime() {
        return baseCandle().getTime();
    }

    @Override
    public final double open() {
        return baseCandle().open();
    }

    @Override
    public final double high() {
        return baseCandle().high();
    }

    @Override
    public final double low() {
        return baseCandle().low();
    }

    @Override
    public final double close() {
        return baseCandle().close();
    }

    @Override
    public final double volume() {
        return baseCandle().volume();
    }

    @Override
    public final int count() {
        return baseCandle().count();
    }
}
