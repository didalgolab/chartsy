/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.navigator;

import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.ui.tree.TreeViewControl;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

public class SymbolsViewListener implements ApplicationListener<PayloadApplicationEvent<SymbolGroupAggregateData>> {

    private final TreeViewControl viewControl;

    public SymbolsViewListener(TreeViewControl viewControl) {
        this.viewControl = viewControl;
    }

    @Override
    public void onApplicationEvent(PayloadApplicationEvent<SymbolGroupAggregateData> event) {
        SymbolGroupAggregateData entity = event.getPayload();
        viewControl.invalidate(SymbolGroupAggregateData.class, entity.getParentGroupId());
    }
}
