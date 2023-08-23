/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import org.netbeans.api.progress.ProgressHandle;

class NbProgressHandle implements one.chartsy.kernel.ProgressHandle {
    private final ProgressHandle handle;

    NbProgressHandle(ProgressHandle handle) {
        this.handle = handle;
    }

    @Override
    public void start(int workTotal) {
        handle.start(workTotal);
    }

    @Override
    public void progress(String message, int workDone) {
        handle.progress(message, workDone);
    }

    @Override
    public void finish() {
        handle.finish();
    }
}
