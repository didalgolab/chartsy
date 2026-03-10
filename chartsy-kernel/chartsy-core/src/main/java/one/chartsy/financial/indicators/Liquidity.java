package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes liquidity as unique price levels divided by the total price points.
 *
 * @author Mariusz Bernacki
 *
 */
@ChartStudy(
        name = "Liquidity",
        label = "Liquidity ({periods})",
        category = "Market Structure",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(min = 0.0, max = 1.0, steps = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0})
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#4285F4", order = 100)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@LinePlotSpec(id = "liquidity", label = "Liquidity", output = "value", colorParameter = "color", strokeParameter = "style", order = 10)
public class Liquidity extends AbstractCandleIndicator {

    private final int periods;
    private final RingBuffer<Candle> window;
    private final RingBuffer.OfInt pricesPerBar;
    private final Map<Double, Integer> priceLevelCounts = new HashMap<>();
    private int totalPrices = 0;
    private double last = Double.NaN;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static Liquidity study(
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "100", order = 10) int periods
    ) {
        return new Liquidity(periods);
    }

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
        boolean oldAddOpen = (oldCount == 4);
        removePriceLevels(oldCandle, oldAddOpen);
        totalPrices -= oldCount;
    }

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

    private void decrementOrRemove(Double price) {
        priceLevelCounts.compute(price, (k, v) -> (v == null || v <= 1) ? null : v - 1);
    }

    @Override
    @StudyOutput(id = "value", name = "Liquidity", order = 10)
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return window.length() == periods;
    }
}
