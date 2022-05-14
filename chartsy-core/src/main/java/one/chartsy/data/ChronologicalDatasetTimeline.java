/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.time.AbstractTimeline;
import one.chartsy.time.Chronological;

public class ChronologicalDatasetTimeline extends AbstractTimeline {

    private final ChronologicalDataset data;
    private final Chronological.Order order;

    public ChronologicalDatasetTimeline(ChronologicalDataset data, Chronological.Order order) {
        this.data = data;
        this.order = order;
    }

    @Override
    public final Chronological.Order getOrder() {
        return order;
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
