/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.charting.financial.AdaptiveCategoryTimeSteps;
import one.chartsy.charting.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveDateAxisLanePlanTest {

    @Test
    void sessionShapedIntradayDataUsesCalendarLanesInsteadOfTimeLanes() {
        var plan = AdaptiveCategoryTimeSteps.plan(2.0, TimeUnit.DAY.getMillis());

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.DAY);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.MONTH);
    }

    @Test
    void longerCoarseIntradaySpansPromoteToMonthAndYearLanes() {
        var plan = AdaptiveCategoryTimeSteps.plan(95.0, TimeUnit.DAY.getMillis());

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.MONTH);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.YEAR);
    }

    @Test
    void longerFineGrainedIntradaySpansAlsoPromoteToMonthAndYearLanes() {
        var plan = AdaptiveCategoryTimeSteps.plan(60.0, TimeUnit.HOUR.getMillis());

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.MONTH);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.YEAR);
    }
}
