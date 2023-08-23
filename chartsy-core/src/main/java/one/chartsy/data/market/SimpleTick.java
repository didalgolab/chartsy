/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import one.chartsy.core.json.GenericEnum;
import one.chartsy.core.json.GsonJsonFormatter;
import one.chartsy.time.Chronological;
import org.openide.util.lookup.ServiceProvider;

import java.time.LocalDateTime;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static one.chartsy.data.market.TradeConditionData.None.NONE;
import static org.openide.util.Lookup.getDefault;

public class SimpleTick implements Tick {
    private long time;
    @SerializedName("P") private double price;
    @SerializedName("V") private double size;
    @SerializedName("TC")
    @JsonAdapter(GenericEnum.class)
    private TradeConditionData tradeConditions;


    public SimpleTick(long time, double price, double size, TradeConditionData tradeConditions) {
        this.time = time;
        this.price = price;
        this.size = size;
        this.tradeConditions = (tradeConditions == NONE)? null: tradeConditions;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tradeConditions)
                + 11 * Long.hashCode(time)
                + 13 * Double.hashCode(price)
                + 17 * Double.hashCode(size);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            SimpleTick that = (SimpleTick) obj;
            return this.time == that.time
                    && this.price == that.price
                    && this.size == that.size
                    && Objects.equals(this.tradeConditions, that.tradeConditions);
        }
        return false;
    }

    @Override
    public final long getTime() {
        return time;
    }

    @Override
    public final double price() {
        return price;
    }

    @Override
    public final double size() {
        return size;
    }

    @Override
    public final TradeConditionData getTradeConditions() {
        return Objects.requireNonNullElse(tradeConditions, Tick.super.getTradeConditions());
    }

    @Override
    public String toString() {
        return JsonFormat.toJson(this);
    }

    @SuppressWarnings("unused") // for JSON deserialization
    protected SimpleTick() {}

    @ServiceProvider(service = JsonFormat.class)
    public static class JsonFormat {
        protected static final GsonJsonFormatter formatter = ofNullable(getDefault().lookup(GsonJsonFormatter.class))
                .orElseGet(GsonJsonFormatter::new);

        public static String toJson(Tick tick) {
            JsonObject obj = formatter.toJsonObject(tick);

            JsonElement time = obj.remove("time");
            if (time == null)
                return obj.toString();

            if (obj.has("V") && obj.getAsJsonPrimitive("V").getAsDouble() == 0.0)
                obj.remove("V");

            LocalDateTime dateTime = Chronological.toDateTime(time.getAsLong());
            JsonObject newObj = new JsonObject();
            newObj.add(dateTime.toString(), obj);
            return newObj.toString();
        }

        public static <T extends Tick> T fromJson(String json, Class<T> valueType) {
            JsonObject obj = formatter.fromJson(json).getAsJsonObject();

            String key;
            if (obj.size() == 1 && (key = obj.keySet().iterator().next()).length() > 11
                    && key.charAt(4) == '-'
                    && key.charAt(7) == '-'
                    && key.charAt(10) == 'T') {
                LocalDateTime dateTime = LocalDateTime.parse(key);
                obj = obj.getAsJsonObject(key);
                obj.addProperty("time", Chronological.toEpochMicros(dateTime));
            }
            return formatter.fromJson(obj, valueType);
        }
    }
}
