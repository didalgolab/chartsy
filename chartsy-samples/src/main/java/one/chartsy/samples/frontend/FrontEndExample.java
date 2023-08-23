/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.frontend;

import one.chartsy.kernel.boot.FrontEnd;
import org.openide.util.Lookup;

public class FrontEndExample {

    public static void main(String[] args) {
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
        System.out.println(frontEnd);
    }
}
