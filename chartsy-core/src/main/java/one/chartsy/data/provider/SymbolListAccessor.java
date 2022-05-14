/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;

import java.util.List;

@FunctionalInterface
public interface SymbolListAccessor {

    List<? extends SymbolIdentity> listSymbols(SymbolGroup group);
}
