/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.FrontEndInterface;
import one.chartsy.kernel.ProgressHandle;
import one.chartsy.ui.actions.BlockingAction;
import org.openide.util.Cancellable;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;

import javax.swing.*;
import java.awt.event.ActionEvent;

@ServiceProvider(service = FrontEndInterface.class)
public class NbFrontEnd implements FrontEndInterface {

    @Override
    public void foregroundAction(String title, Runnable work) {
        Action action = new BlockingAction(title, work);
        action.actionPerformed(new ActionEvent(WindowManager.getDefault().getMainWindow(), ActionEvent.ACTION_PERFORMED, ""));
    }

    @Override
    public ProgressHandle createProgressHandle(String displayName, Cancellable allowToCancel) {
        return new NbProgressHandle(org.netbeans.api.progress.ProgressHandle.createHandle(displayName, allowToCancel));
    }
}
