package one.chartsy;

import one.chartsy.data.SimpleCandle;

public interface CandleBuilder<T extends Candle, E> extends Incomplete<T> {

    void put(E element);

    void merge(E element);

    abstract class From<E> implements CandleBuilder<Candle, E> {

        private boolean present;
        protected long time;
        protected double open;
        protected double high;
        protected double low;
        protected double close;
        protected double volume;
        protected int count;

        @Override
        public final boolean isPresent() {
            return present;
        }

        protected void setPresent() {
            this.present = true;
        }

        protected void unsetPresent() {
            this.present = false;
        }

        @Override
        public final SimpleCandle get() {
            if (isPresent())
                return SimpleCandle.of(time, open, high, low, close, volume, count);
            else
                return null;
        }
    }
}