/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.provider.DataProviderLoader;

import java.util.List;
import java.util.Optional;

public interface SymbolGroupContent {

    Long getId();

    String getName();

    Type getContentType();

    Optional<Symbol> getAsSymbol();

    List<SymbolGroupContent> getContent(SymbolGroupContentRepository repo, DataProviderLoader loader);

    enum Type {
        DATA_PROVIDER,
        DATA_PROVIDER_FOLDER,
        FOLDER,
        SYMBOL
    }
}
