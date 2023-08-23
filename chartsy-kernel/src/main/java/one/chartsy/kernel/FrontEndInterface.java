/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import org.openide.util.Cancellable;
import org.openide.util.Lookup;

public interface FrontEndInterface {

    ProgressHandle createProgressHandle(String displayName, Cancellable allowToCancel);

    void foregroundAction(String title, Runnable work);

    static FrontEndInterface get() {
        return get(Lookup.getDefault());
    }

    static FrontEndInterface get(Lookup lookup) {
        FrontEndInterface frontEnd = lookup.lookup(FrontEndInterface.class);
        return (frontEnd != null)? frontEnd : new NonInteractiveFrontEnd();
    }
}
