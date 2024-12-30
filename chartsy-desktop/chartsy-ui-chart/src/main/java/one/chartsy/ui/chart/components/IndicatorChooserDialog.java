/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.io.Serial;
import java.util.List;
import java.util.function.Consumer;

import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.Indicator;
import org.openide.util.NbBundle;

public class IndicatorChooserDialog extends ChartPluginChooser<Indicator> {
    @Serial
    private static final long serialVersionUID = -8778936066219858851L;
    
    /**
     * Creates a new indicator chooser dialog.
     * 
     * @param parent
     *            the parental chart frame that owns this chooser
     * @param selectionHandler
     *            the callback object which handles the chosen and accepted
     *            selection
     */
    public IndicatorChooserDialog(ChartFrame parent, Consumer<List<Indicator>> selectionHandler) {
        super(parent, selectionHandler);
        setTitle(NbBundle.getMessage(IndicatorChooserDialog.class, "IChooser.title")); // NOI18N
        setAvailablePluginsLabelText(NbBundle.getMessage(IndicatorChooserDialog.class, "IChooser.availablePluginsLabel.text")); // NOI18N
        setSelectedPluginsLabelText(NbBundle.getMessage(IndicatorChooserDialog.class, "IChooser.selectedPluginsLabel.text")); // NOI18N
    }
}
