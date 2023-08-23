/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.consolidate;

import one.chartsy.samples.chartfx.dos.DefaultOHLCV;
import one.chartsy.samples.chartfx.dos.OHLCVItem;

public interface OhlcvConsolidationAddon {
    /**
   * Base method for addon calculation process
   * @param ohlcv existed ohlcv structure
   * @param incrementItem incremental ohlc item
   * @return enhanced signal
   */
    DefaultOHLCV consolidationUpdateAddon(DefaultOHLCV ohlcv, OHLCVItem incrementItem);

    /**
   * Base method for addon calculation process
   * @param ohlcv existed ohlcv structure
   * @param incrementItem incremental ohlc item
   * @return enhanced signal
   */
    DefaultOHLCV consolidationAdditionAddon(DefaultOHLCV ohlcv, OHLCVItem incrementItem);

    /**
   * @return true = addon needs recalculation per tick in the consolidation process,
   * false = the computation is processing by new tick which create new bar. It means
   * in the end of previous closed bar - on close of bar.
   */
    boolean isDynamic();
}
