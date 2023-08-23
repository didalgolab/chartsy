/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.openide.util.Lookup;

/**
 * A common base class for all financial-related services.
 *
 * @author Mariusz Bernacki
 */
public interface FinancialService extends Lookup.Provider {

    default String getName() {
        return getClass().getSimpleName();
    }
}
