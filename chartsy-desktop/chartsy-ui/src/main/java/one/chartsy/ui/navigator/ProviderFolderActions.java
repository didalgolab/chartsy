/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.navigator;

import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.Action;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

final class ProviderFolderActions {
    private ProviderFolderActions() { }

    static Action[] append(Lookup context, Action[] defaults) {
        var actions = new ArrayList<>(Arrays.asList(defaults));
        var extensions = Utilities.actionsForPath("Symbols/ProviderFolder/Actions");
        if (!extensions.isEmpty())
            actions.add(null);
        for (Action action : extensions)
            actions.add(action instanceof ContextAwareAction aware
                    ? aware.createContextAwareInstance(context) : action);
        return actions.toArray(Action[]::new);
    }
}