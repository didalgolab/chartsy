package one.chartsy.data;

import one.chartsy.Candle;

public class ExtendedCandle extends AbstractCandle {
    private final long openTime;
    private final int openInterest;
    private final double vwap;


    public ExtendedCandle(Candle base, long openTime, int openInterest, double vwap) {
        super(base);
        this.openTime = openTime;
        this.openInterest = openInterest;
        this.vwap = vwap;
    }

    public long getOpenTime() {
        return openTime;
    }

    public int openInterest() {
        return openInterest;
    }

    public double vwap() {
        return vwap;
    }

    public double turnover() {
        return vwap() * volume();
    }
}
