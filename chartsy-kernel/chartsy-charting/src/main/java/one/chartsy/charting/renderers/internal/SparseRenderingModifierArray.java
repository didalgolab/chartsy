package one.chartsy.charting.renderers.internal;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;

/// Lazily allocated sparse storage for point-specific rendering modifiers.
///
/// `SingleChartRenderer` uses this implementation when only selected logical points need explicit
/// annotations or rendering hints. Unassigned indices consume no storage and resolve to `null`.
///
/// The iterators returned by [#getAnnotations()] and [#getRenderingHints()] traverse only the
/// modifiers that were explicitly assigned.
public class SparseRenderingModifierArray implements RenderingModifierArray, Serializable {
    private Map<Integer, DataAnnotation> annotationsByIndex;
    private Map<Integer, DataRenderingHint> renderingHintsByIndex;

    @Override
    public DataAnnotation getAnnotation(int itemIndex) {
        return (annotationsByIndex == null) ? null : annotationsByIndex.get(itemIndex);
    }

    @Override
    public Iterator<DataAnnotation> getAnnotations() {
        return (annotationsByIndex == null)
                ? Collections.emptyIterator()
                : annotationsByIndex.values().iterator();
    }

    @Override
    public DataRenderingHint getRenderingHint(int itemIndex) {
        return (renderingHintsByIndex == null) ? null : renderingHintsByIndex.get(itemIndex);
    }

    @Override
    public Iterator<DataRenderingHint> getRenderingHints() {
        return (renderingHintsByIndex == null)
                ? Collections.emptyIterator()
                : renderingHintsByIndex.values().iterator();
    }

    @Override
    public boolean holdsAnnotation() {
        return annotationsByIndex != null && !annotationsByIndex.isEmpty();
    }

    @Override
    public boolean holdsRenderingHint() {
        return renderingHintsByIndex != null && !renderingHintsByIndex.isEmpty();
    }

    @Override
    public void setAnnotation(int itemIndex, DataAnnotation annotation) {
        if (annotation != null) {
            if (annotationsByIndex == null)
                annotationsByIndex = new HashMap<>();
            annotationsByIndex.put(itemIndex, annotation);
        } else if (annotationsByIndex != null) {
            annotationsByIndex.remove(itemIndex);
        }
    }

    @Override
    public void setRenderingHint(int itemIndex, DataRenderingHint renderingHint) {
        if (renderingHint != null) {
            if (renderingHintsByIndex == null)
                renderingHintsByIndex = new HashMap<>();
            renderingHintsByIndex.put(itemIndex, renderingHint);
        } else if (renderingHintsByIndex != null) {
            renderingHintsByIndex.remove(itemIndex);
        }
    }
}
