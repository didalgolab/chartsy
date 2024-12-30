/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

import one.chartsy.SymbolIdentity;
import one.chartsy.time.Chronological;

public interface MarketMessage extends Chronological {

    SymbolIdentity symbol();
}
