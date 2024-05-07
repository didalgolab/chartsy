/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.time.AbstractTimeline;
import one.chartsy.time.Chronological;

public class ChronologicalDatasetTimeline extends AbstractTimeline {

    private final ChronologicalDataset data;

    public ChronologicalDatasetTimeline(ChronologicalDataset data, Chronological.Order order) {
        super(order);
        this.data = data;
    }

    @Override
    public int length() {
        return data.length();
    }

    @Override
    public long getTimeAt(int index) {
        return data.getTimeAt(index);
    }
}
