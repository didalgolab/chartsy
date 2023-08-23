/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service;

import de.gsi.dataset.spi.financial.api.ohlcv.IOhlcvItem;

/**
 * OHLCV Listener about structure changes.
 */
public interface OhlcvChangeListener {
    /**
     * Notification event about new ohlcv item changed
     * @param ohlcvItem new or changed ohlcv item
     * @exception Exception if the processing failed
     */
    void tickEvent(IOhlcvItem ohlcvItem) throws Exception;
}
