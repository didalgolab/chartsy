/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.json;

/**
 * An interface defining methods for JSON serialization and deserialization.
 * <p>
 * Implementations of this interface provide functionality to convert objects to JSON strings
 * and reconstruct objects from JSON representations.
 * </p>
 *
 * @author Mariusz Bernacki
 */
public interface JsonFormatter {

    /**
     * Converts the given object to its equivalent JSON representation.
     *
     * @param src the object to be serialized to JSON
     * @return JSON string representation of the provided object
     */
    String toJson(Object src);

    /**
     * Deserializes the provided JSON string into an object of the specified type.
     *
     * @param json JSON string to deserialize
     * @param resultType class of the object to deserialize into
     * @param <T> type of the resulting object
     * @return an object of the specified type created from the JSON string
     */
    <T> T fromJson(String json, Class<T> resultType);
}
