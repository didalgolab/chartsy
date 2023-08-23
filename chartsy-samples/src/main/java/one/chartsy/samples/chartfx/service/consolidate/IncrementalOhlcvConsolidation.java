/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.consolidate;

import one.chartsy.samples.chartfx.dos.DefaultOHLCV;
import one.chartsy.samples.chartfx.dos.OHLCVItem;
import one.chartsy.samples.chartfx.service.period.IntradayPeriod;

public interface IncrementalOhlcvConsolidation {
    /**
   * Base method for incremental consolidation process
   * @param ohlcv existed consolidated ohlcv structure
   * @param incrementItem tick actual ohlcv item
   * @return consolidated signal
   */
    DefaultOHLCV consolidate(DefaultOHLCV ohlcv, OHLCVItem incrementItem);

    /**
   * @return provides information about consolidation settings period
   */
    IntradayPeriod getPeriod();
}
