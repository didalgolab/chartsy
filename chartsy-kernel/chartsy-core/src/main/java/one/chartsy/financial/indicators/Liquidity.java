package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes liquidity as unique price levels divided by the total price points.
 *
 * @author Mariusz Bernacki
 *
 */
public class Liquidity extends AbstractCandleIndicator {

    private final int periods;
    private final RingBuffer<Candle> window;
    private final RingBuffer.OfInt pricesPerBar;
    private final Map<Double, Integer> priceLevelCounts = new HashMap<>();
    private int totalPrices = 0;
    private double last = Double.NaN;

    public Liquidity(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("Periods must be positive");

        this.periods = periods;
        this.window = new RingBuffer<>(periods);
        this.pricesPerBar = new RingBuffer.OfInt(periods);
    }

    @Override
    public void accept(Candle candle) {
        if (window.length() == periods) {
            removeOldestBar();
        }
        addNewBar(candle);
        last = (totalPrices == 0 ? 0 : (double) priceLevelCounts.size() / totalPrices);
    }

    private void addNewBar(Candle candle) {
        // Determine if we should include the open price.
        // The rule: if newCandle.open() equals previous candle's close, skip open.
        Candle previous = window.isEmpty() ? null : window.get(0);
        boolean addOpen = (previous == null || candle.open() != previous.close());
        int pricesAdded = addPriceLevels(candle, addOpen);

        totalPrices += pricesAdded;
        window.add(candle);
        pricesPerBar.add(pricesAdded);
    }

    private void removeOldestBar() {
        Candle oldCandle = window.get(periods - 1);
        int oldCount = pricesPerBar.get(periods - 1);
        boolean oldAddOpen = (oldCount == 4); // If oldCount is 4, then its open price was counted
        removePriceLevels(oldCandle, oldAddOpen);
        totalPrices -= oldCount;
    }

    /**
     * Add price levels from the given candle to the map.
     *
     * @param candle the new candle
     * @param addOpen whether to include the open price
     * @return the number of price levels included
     */
    private int addPriceLevels(Candle candle, boolean addOpen) {
        int pricesAdded = 3;
        if (addOpen) {
            pricesAdded++;
            priceLevelCounts.merge(candle.open(), 1, Integer::sum);
        }
        priceLevelCounts.merge(candle.high(), 1, Integer::sum);
        priceLevelCounts.merge(candle.low(), 1, Integer::sum);
        priceLevelCounts.merge(candle.close(), 1, Integer::sum);
        return pricesAdded;
    }

    /**
     * Remove price levels corresponding to the given candle.
     *
     * @param candle the candle being removed
     * @param removeOpen whether the open price was included originally
     * @return the number of price levels excluded
     */
    private int removePriceLevels(Candle candle, boolean removeOpen) {
        int pricesRemoved = 3;
        if (removeOpen) {
            pricesRemoved++;
            decrementOrRemove(candle.open());
        }
        decrementOrRemove(candle.high());
        decrementOrRemove(candle.low());
        decrementOrRemove(candle.close());
        return pricesRemoved;
    }

    /**
     * Decrement the count for the given price level or remove it if count reaches zero.
     *
     * @param price the price level
     */
    private void decrementOrRemove(Double price) {
        priceLevelCounts.compute(price, (k, v) -> (v == null || v <= 1) ? null : v - 1);
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return window.length() == periods;
    }
}
