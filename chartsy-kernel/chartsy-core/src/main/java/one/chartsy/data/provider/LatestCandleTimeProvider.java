/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;

import java.util.OptionalLong;

/**
 * Optional provider lookup capability for reading a history's endpoint without loading its candles.
 * Implementations must support concurrent calls for different resources.
 */
public interface LatestCandleTimeProvider {
    /**
     * Endpoint and opaque, immutable source revision. Equal revisions identify the same observed
     * source version; changes to prices or native period must change it even if the endpoint time
     * stays the same. File-backed providers may use file identity, size and modification time;
     * edits deliberately preserving all those attributes are outside that revision guarantee.
     */
    record Snapshot(OptionalLong time, Object revision) { }

    /** Whether endpoint metadata is available for this resource without loading its full history. */
    boolean supports(SymbolResource<Candle> resource);

    /**
     * Returns the last output candle's timestamp for the equivalent unbounded candle query.
     * Aggregated resources use the provider's actual last-candle timestamp, not a projected period end.
     * An empty result means a known empty history; unsupported resources and read or parse failures
     * must throw rather than being reported as empty. Metadata does not validate intermediate records.
     *
     * @throws UnsupportedOperationException if this resource is not supported
     */
    default OptionalLong getLatestCandleTime(SymbolResource<Candle> resource) {
        return getLatestCandleSnapshot(resource).time();
    }

    /** Reads endpoint and source revision together, failing if the source changes during that read. */
    Snapshot getLatestCandleSnapshot(SymbolResource<Candle> resource);
}
