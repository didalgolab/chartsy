/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.util.ServiceLookup;

import javax.swing.*;

public class IconResource {

    private static IconResourceService service() {
        return ServiceLookup.getOrDefault(IconResourceService.class, DefaultIconResourceService::new);
    }

    public static Icon getIcon(String name) {
        return service().getIcon(name);
    }
}
