/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.footprint;

import de.gsi.chart.renderer.spi.financial.service.footprint.NbColumnColorGroup;
import one.chartsy.samples.chartfx.dos.PriceVolumeContainer;

/**
 * Calculate color group settings for each bid/ask volume in each level price
 */
public interface NbColumnColorGroupService {
    /**
     * Calculate color group settings for each bid/ask volume in each level price
     * @param priceVolumeContainer which has to be painted
     * @return the result with column color group data result
     */
    NbColumnColorGroup calculate(PriceVolumeContainer priceVolumeContainer);
}
