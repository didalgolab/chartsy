package one.chartsy.charting.renderers.internal;

import java.io.Serializable;

import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;

/// Rendering modifier storage with dataset-wide fallbacks plus sparse per-item overrides.
///
/// Indexed lookups consult the sparse storage inherited from [SparseRenderingModifierArray] first.
/// When no point-specific modifier exists, the lookup falls back to the dataset-wide default
/// annotation or rendering hint stored on this instance.
///
/// `SingleChartRenderer` uses this form so one dataset can carry a default modifier policy while
/// still overriding selected logical points.
public class DefaultedRenderingModifierArray extends SparseRenderingModifierArray
        implements RenderingModifierArray, Serializable {
    private DataAnnotation defaultAnnotation;
    private DataRenderingHint defaultRenderingHint;

    /// Returns the dataset-wide annotation fallback.
    ///
    /// @return annotation used when an indexed lookup has no explicit annotation
    public DataAnnotation getAnnotation() {
        return defaultAnnotation;
    }

    /// Returns the point-specific annotation for `itemIndex`, or the dataset-wide fallback when
    /// that slot has no explicit annotation.
    ///
    /// @param itemIndex logical item index in the current dataset view
    /// @return effective annotation for `itemIndex`, or `null` when neither an explicit nor a
    ///     default annotation is present
    @Override
    public DataAnnotation getAnnotation(int itemIndex) {
        DataAnnotation annotation = super.getAnnotation(itemIndex);
        return (annotation != null) ? annotation : defaultAnnotation;
    }

    /// Returns the dataset-wide rendering-hint fallback.
    ///
    /// @return rendering hint used when an indexed lookup has no explicit hint
    public DataRenderingHint getRenderingHint() {
        return defaultRenderingHint;
    }

    /// Returns the point-specific rendering hint for `itemIndex`, or the dataset-wide fallback
    /// when that slot has no explicit hint.
    ///
    /// @param itemIndex logical item index in the current dataset view
    /// @return effective rendering hint for `itemIndex`, or `null` when neither an explicit nor a
    ///     default hint is present
    @Override
    public DataRenderingHint getRenderingHint(int itemIndex) {
        DataRenderingHint renderingHint = super.getRenderingHint(itemIndex);
        return (renderingHint != null) ? renderingHint : defaultRenderingHint;
    }

    /// Returns whether either a dataset-wide default annotation or at least one point-specific
    /// annotation is currently present.
    ///
    /// @return `true` when annotation lookups can resolve at least one explicit or default value
    @Override
    public boolean holdsAnnotation() {
        return defaultAnnotation != null || super.holdsAnnotation();
    }

    /// Returns whether either a dataset-wide default rendering hint or at least one point-specific
    /// hint is currently present.
    ///
    /// @return `true` when rendering-hint lookups can resolve at least one explicit or default
    ///     value
    @Override
    public boolean holdsRenderingHint() {
        return defaultRenderingHint != null || super.holdsRenderingHint();
    }

    /// Sets the dataset-wide annotation fallback.
    ///
    /// Passing `null` clears the fallback so only point-specific annotations remain effective.
    ///
    /// @param annotation annotation to use as the dataset-wide fallback, or `null` to clear it
    public void setAnnotation(DataAnnotation annotation) {
        defaultAnnotation = annotation;
    }

    /// Sets the dataset-wide rendering-hint fallback.
    ///
    /// Passing `null` clears the fallback so only point-specific hints remain effective.
    ///
    /// @param renderingHint rendering hint to use as the dataset-wide fallback, or `null` to clear
    ///                          it
    public void setRenderingHint(DataRenderingHint renderingHint) {
        defaultRenderingHint = renderingHint;
    }
}
