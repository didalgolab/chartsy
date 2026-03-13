/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.Kernel;
import org.openide.util.lookup.ServiceProvider;

public class Installer {

    @ServiceProvider(service = Kernel.class)
    public static class DefaultKernel extends Kernel {

        public DefaultKernel() { }

        public void close() {
            // do nothing
        }
    }
}
