/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviderLoader;
import org.springframework.expression.ExpressionParser;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DataProviderRegistry implements DataProviderLoader, Closeable {
    private final ConcurrentMap<String, DataProvider> registry = new ConcurrentHashMap<>();
    private final ExpressionParser parser;

    public DataProviderRegistry(ExpressionParser parser) {
        this.parser = parser;
    }

    @Override
    public DataProvider load(String descriptor) {
        var identifier = asIdentifier(descriptor);
        return registry.computeIfAbsent(identifier, __ -> createFromDescriptor(descriptor));
    }

    protected String asIdentifier(String descriptor) {
        return descriptor;
    }

    protected DataProvider createFromDescriptor(String descriptor) {
        return parser.parseExpression(descriptor).getValue(DataProvider.class);
    }

    @Override
    public void close() {
        for (String identifier : registry.keySet()) {
            DataProvider provider = registry.remove(identifier);
            if (provider instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) provider).close();
                } catch (Exception e) {
                }
            }
        }
    }
}
