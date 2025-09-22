/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;

/**
 * Represents the market tick event, either trade or quote based.
 * <p>
 * Ticks are timestamped to the nearest microsecond in Chartsy|One.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface Tick extends Chronological {

    /**
     * Returns the timestamp at which this tick was recorded.
     *
     * @return the tick timestamp
     */
    @Override
    long getTime();

    /**
     * Gives trade conditions associated with this market tick (e.g. the trade direction,
     *
     * @return the trade conditions
     */
    default TradeConditionData getTradeConditions() {
        return TradeConditionData.None.NONE;
    }

    /**
     * Returns the price of this tick.
     * 
     * @return the tick price
     */
    double price();
    
    /**
     * Returns the volume of the tick.
     * 
     * @return the tick volume
     */
    double size();

    default Candle toCandle() {
        return Candle.of(getTime(), price(), price(), price(), price(), size());
    }

    static <T extends Tick> T parse(String json, Class<T> type) {
        return SimpleTick.JsonFormat.fromJson(json, type);
    }

    static Tick of(long time, double price) {
        return Tick.of(time, price, 0, null);
    }

    static Tick of(long time, double price, double size) {
        return Tick.of(time, price, size, null);
    }

    static Tick of(long time, double price, double size, TradeConditionData tradeConditions) {
        return new SimpleTick(time, price, size, tradeConditions);
    }

    /**
     * The side of the tick, BID or ASK.
     * 
     * @author Mariusz Bernacki
     *
     */
    enum Side {
        ASK, BID;
    }
}
