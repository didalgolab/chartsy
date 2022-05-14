/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import one.chartsy.Symbol;

import java.awt.datatransfer.DataFlavor;

public class SymbolDataFlavor {

    public static final DataFlavor dataFlavor;
    static {
        try {
            dataFlavor = new DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Symbol.class.getName());
        } catch (ClassNotFoundException e) {
            throw new InternalError("Shouldn't happen");
        }
    }

}
