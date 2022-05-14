/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.components.ChartToolbar;

import javax.swing.*;

public final class ChartActions {

    public static Action toggleToolbarVisibility(ChartFrame chartFrame) {
        return ChartAction.find("ToolbarVisibilityToggle", chartFrame);
    }

    public static Action showComponentPopupMenuAction() {
        return ChartAction.find("ComponentPopupMenuShow");
    }

    public static Action zoomIn(ChartFrame chartFrame) {
        return ChartAction.find("ZoomIn", chartFrame);
    }

    public static Action zoomOut(ChartFrame chartFrame) {
        return ChartAction.find("ZoomOut", chartFrame);
    }

    public static Action intervalPopup(ChartFrame chartFrame) {
        return ChartAction.find("IntervalPopup", chartFrame);
    }

    public static Action chartPopup(ChartFrame chartFrame) {
        return ChartAction.find("ChartPopup", chartFrame);
    }

    public static Action openOverlays(ChartFrame chartFrame) {
        return ChartAction.find("OverlaysOpen", chartFrame);
    }

    public static Action openIndicators(ChartFrame chartFrame) {
        return ChartAction.find("IndicatorsOpen", chartFrame);
    }

    public static Action annotationPopup(ChartFrame chartFrame) {
        return ChartAction.find("AnnotationPopup", chartFrame);
    }

    public static Action toggleMarker(ChartFrame chartFrame) {
        return ChartAction.find("MarkerToggle", chartFrame);
    }

    public static Action exportImage(ChartFrame chartFrame) {
        return ChartAction.find("ImageExport", chartFrame);
    }

    public static Action printChart(ChartFrame chartFrame) {
        return ChartAction.find("ChartPrint", chartFrame);
    }

    public static Action chartProperties(ChartFrame chartFrame) {
        return ChartAction.find("ChartProperties", chartFrame);
    }

    public static Action addToFavorites(ChartFrame chartFrame) {
        return ChartAction.find("FavoritesAdd", chartFrame);
    }

    public static Action toggleToolbarSmallIcons(ChartFrame chartFrame, ChartToolbar chartToolbar) {
        return ChartAction.find("ToolbarSmallIconsToggle", chartFrame, chartToolbar);
    }

    public static Action toggleToolbarShowLabels(ChartFrame chartFrame, ChartToolbar chartToolbar) {
        return ChartAction.find("ToolbarShowLabelsToggle", chartFrame, chartToolbar);
    }

    public static Action annotationProperties(ChartContext chartFrame, Annotation graphic) {
        return ChartAction.find("AnnotationProperties", chartFrame, graphic);
    }
}
