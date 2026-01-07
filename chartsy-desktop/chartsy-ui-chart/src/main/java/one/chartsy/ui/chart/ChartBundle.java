/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.kernel.ImageResource;

public record ChartBundle(ImageResource image, ChartDescription desc) {

    public ChartBundle {
        if (image == null)
            throw new IllegalArgumentException("image is null");
        if (desc == null)
            throw new IllegalArgumentException("context is null");
    }

    public String toPrompt(String userPrompt) {
        if (userPrompt == null)
            throw new IllegalArgumentException("userPrompt is null");
        return userPrompt.stripTrailing() + "\n\n" + desc.toYamlString();
    }
}
