/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.study.StudyColor;
import one.chartsy.study.StudyPresentationContext;

final class StudyBuilderSupport {
    private StudyBuilderSupport() {
    }

    static int intParameter(StudyPresentationContext context, String id) {
        return ((Number) context.parameter(id)).intValue();
    }

    static double doubleParameter(StudyPresentationContext context, String id) {
        return ((Number) context.parameter(id)).doubleValue();
    }

    static boolean booleanParameter(StudyPresentationContext context, String id) {
        return (Boolean) context.parameter(id);
    }

    static StudyColor colorParameter(StudyPresentationContext context, String id) {
        return context.parameter(id, StudyColor.class);
    }

    static String strokeParameter(StudyPresentationContext context, String id) {
        return context.parameter(id, String.class);
    }
}
