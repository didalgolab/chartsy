package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;

/// Mode adapter that keeps superimposed variable-width children visually separable.
///
/// The parent renderer exposes one shared `widthPercent` budget. While this config is active, each
/// child keeps the same center but receives a progressively smaller width, producing a visible
/// nesting effect instead of letting later siblings completely cover earlier ones. Leaving the mode
/// restores every child to the parent's full shared width.
class SuperimposedRendererConfig extends CompositeRendererConfig {

    SuperimposedRendererConfig(CompositeChartRenderer renderer) {
        super(renderer);
    }

    @Override
    void updateChildren() {
        applyProgressiveWidths();
    }

    @Override
    void activate() {
        applyProgressiveWidths();
    }

    @Override
    void deactivate() {
        restoreSharedWidthPercent();
    }

    /// Restores the parent's shared width budget to every child renderer.
    private void restoreSharedWidthPercent() {
        CompositeChartRenderer renderer = getRenderer();
        double widthPercent = ((VariableWidthRenderer) renderer).getWidthPercent();
        for (ChartRenderer child : renderer.getChildren())
            ((VariableWidthRenderer) child).setWidthPercent(widthPercent);
    }

    /// Applies the current narrowing ladder used for superimposed children.
    ///
    /// The first child keeps the parent's full width budget. Each later child loses one equal
    /// `widthPercent / childCount` step so overlapping siblings remain distinguishable.
    private void applyProgressiveWidths() {
        CompositeChartRenderer renderer = getRenderer();
        int childCount = renderer.getChildCount();
        if (childCount == 0)
            return;

        double currentWidthPercent = ((VariableWidthRenderer) renderer).getWidthPercent();
        double widthStep = currentWidthPercent / childCount;
        for (ChartRenderer child : renderer.getChildren()) {
            ((VariableWidthRenderer) child).setWidthPercent(currentWidthPercent);
            currentWidthPercent -= widthStep;
        }
    }
}
