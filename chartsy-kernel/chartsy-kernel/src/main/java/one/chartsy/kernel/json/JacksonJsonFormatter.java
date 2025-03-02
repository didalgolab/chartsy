/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import one.chartsy.core.json.JsonFormatter;

/**
 * A JSON formatter implementation that utilizes the Jackson library for serialization
 * and deserialization of Java objects to and from JSON.
 * <p>
 * This formatter leverages Jackson's ObjectMapper for JSON processing.
 * </p>
 *
 * @author Mariusz Bernacki
 */
public class JacksonJsonFormatter implements JsonFormatter {

    private final ObjectMapper objectMapper;

    public JacksonJsonFormatter() {
        this(new ObjectMapper());
    }

    public JacksonJsonFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public String toJson(Object src) {
        try {
            return objectMapper.writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> resultType) {
        try {
            return objectMapper.readValue(json, resultType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
