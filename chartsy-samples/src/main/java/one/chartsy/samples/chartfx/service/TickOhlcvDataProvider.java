/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service;

import java.io.IOException;

import one.chartsy.samples.chartfx.dos.OHLCVItem;

/**
 * Provides actual tick data 
 */
public interface TickOhlcvDataProvider {
    /**
   * Every get() returns tick ohlcv item. If it is replay mode - the boundary is reached the TickDataFinishedException is thrown. 
   * If the realtime mode is used - never-end loop is used. The thread waits to next data.
   * @return provides tick ohlcv data 
   * @throws TickDataFinishedException if the data are reached the boundary
   * @throws IOException - the data are not reachable
   */
    OHLCVItem get() throws TickDataFinishedException, IOException;
}
