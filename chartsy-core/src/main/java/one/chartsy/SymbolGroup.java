/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.Objects;

public record SymbolGroup(String name) {

    public static SymbolGroup BASE = new SymbolGroup("BASE");

    public SymbolGroup {
        Objects.requireNonNull(name, "name");
    }

    public boolean isBase() {
        return name().equals(BASE.name());
    }
}
