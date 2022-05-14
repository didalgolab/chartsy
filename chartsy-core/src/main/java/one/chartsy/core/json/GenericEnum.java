/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.json;

import com.google.gson.JsonParseException;
import com.google.gson.*;
import org.openide.util.Lookup;

import java.lang.reflect.Type;

public class GenericEnum implements JsonSerializer<Enum<?>>, JsonDeserializer<Enum<?>>  {

    @Override
    public JsonElement serialize(Enum value, Type type, JsonSerializationContext context) {
        JsonObject jsonObj = new JsonObject();
        jsonObj.add(value.getClass().getName(), context.serialize(value));
        return jsonObj;
    }

    @Override
    public Enum<?> deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObj = (JsonObject) json;
        if (jsonObj.size() != 1)
            throw new JsonSyntaxException("Expected single-element JsonObject but found \"" + json + "\" instead");

        // obtain the class loader to be used for deserialization
        ClassLoader classLoader = Lookup.getDefault().lookup(ClassLoader.class);
        if (classLoader == null)
            classLoader = getClass().getClassLoader();

        try {
            String key = jsonObj.keySet().iterator().next();
            Class<?> clazz = classLoader.loadClass(key);
            if (!clazz.isEnum())
                throw new JsonSyntaxException(key + " is not Enum");

            return context.deserialize(jsonObj.get(key), clazz);

        } catch (ClassNotFoundException e) {
            throw new JsonSyntaxException(e);
        }
    }
}
