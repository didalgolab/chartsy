/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * A JSON formatter implementation that utilizes Google's Gson library for serialization
 * and deserialization of Java objects to and from JSON.
 * <p>
 * This formatter can be customized with specific Gson configurations through its constructors.
 * </p>
 *
 * @author Mariusz Bernacki
 */
@ServiceProviders({
        @ServiceProvider(service = JsonFormatter.class),
        @ServiceProvider(service = GsonJsonFormatter.class)})
public class GsonJsonFormatter implements JsonFormatter {

    private final Gson gson;

    public GsonJsonFormatter() {
        this(GsonTypeAdapters.installOn(new GsonBuilder()).create());
    }

    public GsonJsonFormatter(Gson gson) {
        this.gson = gson;
    }

    public final Gson getGson() {
        return gson;
    }

    @Override
    public String toJson(Object src) {
        return getGson().toJson(src);
    }

    public JsonObject toJsonObject(Object src) {
        return getGson().toJsonTree(src).getAsJsonObject();
    }

    @Override
    public <T> T fromJson(String json, Class<T> resultType) {
        return getGson().fromJson(json, resultType);
    }

    public JsonElement fromJson(String json) {
        return getGson().fromJson(json, JsonElement.class);
    }

    public <T> T fromJson(JsonElement json, Class<T> resultType) {
        return getGson().fromJson(json, resultType);
    }
}
