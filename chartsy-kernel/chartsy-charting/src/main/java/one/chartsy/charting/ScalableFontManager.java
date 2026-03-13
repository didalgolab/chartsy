package one.chartsy.charting;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.swing.JComponent;

/// Scales component fonts from one reference component's current size.
///
/// A manager binds one reference component and any number of additional managed components into the
/// same scaling context. Each binding records the component's unscaled base font; derived fonts are
/// then produced from the current scale transform and reused from a cache keyed by the original
/// base font. This avoids compounding scale drift across repeated resize cycles and across
/// temporary unbind/rebind operations.
///
/// [Chart] installs one manager on itself, binds the chart area and later-added child components to
/// it, and lets [LabelRenderer] query the same manager when explicit fonts should follow chart font
/// scaling. The default transform uses one uniform ratio derived from the reference component's
/// width and height, so text never stretches anisotropically.
///
/// The class is Swing-oriented and mutable. Listener callbacks, binding changes, and cache refreshes
/// are intended to happen on the event thread.
public class ScalableFontManager implements Serializable {

    /// Resize listener attached only to the reference component.
    ///
    /// The manager recomputes its font transform only when the component's size actually changes,
    /// which avoids redundant font churn from duplicate resize notifications.
    final class FontComponentListener extends ComponentAdapter implements Serializable {
        private final Dimension currentSize;
        private final Dimension previousSize;

        /// Creates reusable dimensions used to suppress duplicate resize work.
        FontComponentListener() {
            currentSize = new Dimension();
            previousSize = new Dimension();
        }

        /// Rebuilds the scale transform when the reference component's size changes.
        @Override
        public void componentResized(ComponentEvent event) {
            Component component = event.getComponent();
            if (!component.getSize(currentSize).equals(previousSize)) {
                handleReferenceResize();
                previousSize.setSize(currentSize);
            }
        }
    }

    /// Font-property listener for every managed component.
    ///
    /// Direct user font changes replace the stored base font and are immediately reapplied through
    /// the current scale transform. Manager-originated `setFont(...)` calls are ignored through the
    /// [#applyingDerivedFont] reentrancy guard.
    final class FontPropertyListener implements PropertyChangeListener, Serializable {

        FontPropertyListener() {
        }

        /// Captures one externally assigned base font and reapplies the current scaling policy.
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (!applyingDerivedFont) {
                handleManagedFontChange((Component) event.getSource(), (Font) event.getNewValue());
            }
        }
    }

    /// Per-component binding state stored next to each managed component.
    ///
    /// The stored `baseFont` is the unscaled font chosen by application code. Keeping that value
    /// separate from the currently installed derived font prevents resize updates and rebinding from
    /// scaling an already scaled font.
    private static final class BindingInfo {
        private final ScalableFontManager manager;
        private Font baseFont;

        /// Creates the binding metadata for one managed component.
        private BindingInfo(ScalableFontManager manager, Font baseFont) {
            this.manager = manager;
            this.baseFont = baseFont;
        }
    }

    /// Fallback binding store for components that do not support Swing client properties.
    private static final WeakHashMap<Component, BindingInfo> nonJComponentBindings = new WeakHashMap<>();

    /// Returns the binding metadata currently attached to `component`.
    private static BindingInfo getBindingInfo(Component component) {
        if (component instanceof JComponent jComponent) {
            return (BindingInfo) jComponent.getClientProperty(ScalableFontManager.class);
        }
        return nonJComponentBindings.get(component);
    }

    /// Attaches or removes binding metadata for `component`.
    private static void setBindingInfo(Component component, BindingInfo bindingInfo) {
        if (component instanceof JComponent jComponent) {
            jComponent.putClientProperty(ScalableFontManager.class, bindingInfo);
        } else if (bindingInfo == null) {
            nonJComponentBindings.remove(component);
        } else {
            nonJComponentBindings.put(component, bindingInfo);
        }
    }

    private final List<Component> managedComponents;
    private final FontPropertyListener fontPropertyListener;
    private final FontComponentListener fontComponentListener;
    private transient boolean applyingDerivedFont;
    private final HashMap<Font, Font> derivedFontCache;
    private float minRatio;
    private float maxRatio;
    private Dimension referenceSize;
    private boolean enabled;
    private AffineTransform scaleTransform;

    /// Creates a font manager with unclamped scaling.
    ///
    /// Passing `null` for `referenceSize` disables size-based scaling until
    /// [#setRefSize(Dimension)] or [#setScaleTransform(Dimension, float, float)] is called later.
    ///
    /// @param referenceComponent component whose size drives scaling
    /// @param referenceSize baseline size used to compute scale ratios, or `null`
    public ScalableFontManager(Component referenceComponent, Dimension referenceSize) {
        this(referenceComponent, referenceSize, 0.0f, 0.0f);
    }

    /// Creates a font manager with an optional baseline size and clamp range.
    ///
    /// The manager starts disabled. When enabled, derived fonts are scaled uniformly and then
    /// clamped to the inclusive `minRatio`/`maxRatio` interval whenever either bound is non-zero.
    ///
    /// @param referenceComponent component whose size drives scaling
    /// @param referenceSize baseline size used to compute scale ratios, or `null`
    /// @param minRatio minimum allowed scale ratio, or `0.0f` for no lower clamp
    /// @param maxRatio maximum allowed scale ratio, or `0.0f` for no upper clamp
    /// @throws IllegalArgumentException when `maxRatio < minRatio` or the component is already
    ///     bound to another manager
    public ScalableFontManager(Component referenceComponent, Dimension referenceSize, float minRatio, float maxRatio) {
        managedComponents = new ArrayList<>();
        fontPropertyListener = new FontPropertyListener();
        fontComponentListener = new FontComponentListener();
        derivedFontCache = new HashMap<>();

        Component checkedReferenceComponent = Objects.requireNonNull(referenceComponent, "referenceComponent");
        if (ScalableFontManager.getFontManager(checkedReferenceComponent) != null) {
            throw new IllegalArgumentException("Component already bound to a font manager.");
        }

        configureScaleTransform(referenceSize, minRatio, maxRatio);
        managedComponents.add(checkedReferenceComponent);
        setBindingInfo(checkedReferenceComponent, new BindingInfo(this, checkedReferenceComponent.getFont()));
        checkedReferenceComponent.addComponentListener(fontComponentListener);
        checkedReferenceComponent.addPropertyChangeListener("font", fontPropertyListener);
    }

    /// Returns the font manager currently bound to `component`.
    ///
    /// Managed [JComponent] instances store their binding in a client property. Other component
    /// types use a weak fallback map so the manager does not prolong their lifetime.
    ///
    /// @param component component to inspect
    /// @return bound manager, or `null` when `component` is unmanaged
    public static synchronized ScalableFontManager getFontManager(Component component) {
        BindingInfo bindingInfo = getBindingInfo(component);
        return (bindingInfo != null) ? bindingInfo.manager : null;
    }

    /// Binds or unbinds one component to one manager.
    ///
    /// The `baseFont` argument is the component's unscaled font snapshot. Bind callers should pass
    /// the font that should be restored on later unbind; `null` means the component should keep
    /// inheriting its font from its parent when scaling is disabled or removed. The reference
    /// component cannot be detached through this method because removing it would leave the manager
    /// without a scaling source.
    ///
    /// @param component component to bind or unbind
    /// @param fontManager target manager, or `null` to unbind
    /// @param baseFont unscaled font snapshot to associate with the binding
    /// @throws IllegalArgumentException when `component` is already bound, or when trying to unbind
    ///     the reference component without disposing the whole manager
    public static synchronized void setFontManager(Component component, ScalableFontManager fontManager, Font baseFont)
            throws IllegalArgumentException {
        Component checkedComponent = Objects.requireNonNull(component, "component");
        ScalableFontManager currentManager = ScalableFontManager.getFontManager(checkedComponent);
        if (fontManager != null && currentManager != null) {
            throw new IllegalArgumentException("Component already bound to a font manager.");
        }
        if (fontManager == null && currentManager != null && currentManager.getReferenceComponent() == checkedComponent) {
            throw new IllegalArgumentException(
                    "Cannot unbind the reference component of a font manager. Use the dispose() method instead.");
        }

        if (fontManager != null) {
            fontManager.bindComponent(checkedComponent, baseFont);
        } else if (currentManager != null) {
            currentManager.unbindComponent(checkedComponent);
        }
    }

    /// Returns the current reference component, or `null` after [#dispose()].
    private Component getReferenceComponent() {
        return managedComponents.isEmpty() ? null : managedComponents.getFirst();
    }

    /// Registers one additional managed component and applies the current scaling state to it.
    private void bindComponent(Component component, Font baseFont) {
        managedComponents.add(component);
        component.addPropertyChangeListener("font", fontPropertyListener);
        setBindingInfo(component, new BindingInfo(this, baseFont));
        applyDerivedFont(component);
    }

    /// Removes one managed component and restores its recorded base font.
    private void unbindComponent(Component component) {
        BindingInfo bindingInfo = getBindingInfo(component);
        managedComponents.remove(component);
        component.removePropertyChangeListener("font", fontPropertyListener);
        setBindingInfo(component, null);
        restoreBaseFont(component, bindingInfo);
    }

    /// Restores the unscaled font recorded in `bindingInfo`.
    private void restoreBaseFont(Component component, BindingInfo bindingInfo) {
        if (bindingInfo == null) {
            return;
        }
        try {
            applyingDerivedFont = true;
            component.setFont(bindingInfo.baseFont);
        } finally {
            applyingDerivedFont = false;
        }
    }

    /// Recomputes the current scale transform from [#computeScaleRatios()].
    private void updateScaleTransform() {
        float[] ratios = computeScaleRatios();
        scaleTransform = AffineTransform.getScaleInstance(ratios[0], ratios[1]);
    }

    /// Stores one externally assigned base font and refreshes the managed component immediately.
    private void handleManagedFontChange(Component component, Font baseFont) {
        BindingInfo bindingInfo = getBindingInfo(component);
        if (bindingInfo == null) {
            return;
        }

        bindingInfo.baseFont = baseFont;
        applyDerivedFont(component);

        Chart owningChart = findOwningChart(component);
        if (owningChart != null && !owningChart.isUsingEventThread()) {
            return;
        }

        if (component instanceof Chart.Area area) {
            area.revalidateLayout();
        } else if (component instanceof JComponent) {
            component.revalidate();
        } else {
            component.invalidate();
            component.validate();
        }
        component.repaint();
    }

    /// Returns the nearest owning chart for `component`, or `null` when it is not chart-owned.
    private Chart findOwningChart(Component component) {
        Component current = component;
        while (current != null && !(current instanceof Chart)) {
            current = current.getParent();
        }
        return (current instanceof Chart chart) ? chart : null;
    }

    /// Stores a cloned baseline size and clamp range, then rebuilds the scale transform.
    private void configureScaleTransform(Dimension referenceSize, float minRatio, float maxRatio) {
        if (maxRatio < minRatio) {
            throw new IllegalArgumentException("maximum ratio < min ratio !");
        }
        this.referenceSize = (referenceSize != null) ? new Dimension(referenceSize) : null;
        this.minRatio = minRatio;
        this.maxRatio = maxRatio;
        updateScaleTransform();
    }

    /// Produces one scaled font from one unscaled base font using [#scaleTransform].
    private Font deriveScaledFont(Font baseFont) {
        Component referenceComponent = getReferenceComponent();
        if (referenceSize != null && baseFont != null && referenceComponent != null) {
            return baseFont.deriveFont(scaleTransform);
        }
        return null;
    }

    /// Reapplies the current scaling policy to every managed component.
    private void updateManagedFonts() {
        for (int index = managedComponents.size() - 1; index >= 0; index--) {
            applyDerivedFont(managedComponents.get(index));
        }
    }

    /// Installs the currently derived font for one managed component.
    ///
    /// A `null` recorded base font intentionally propagates as `setFont(null)` so inherited-font
    /// components return to normal Swing font inheritance when scaling is disabled or removed.
    private void applyDerivedFont(Component component) {
        BindingInfo bindingInfo = getBindingInfo(component);
        if (bindingInfo == null) {
            return;
        }
        try {
            applyingDerivedFont = true;
            component.setFont(getDeriveFont(bindingInfo.baseFont));
        } finally {
            applyingDerivedFont = false;
        }
    }

    /// Invalidates cached derived fonts after the reference component changes size.
    private void handleReferenceResize() {
        derivedFontCache.clear();
        updateScaleTransform();
        updateManagedFonts();
    }

    /// Computes the x/y font scale ratios used to derive future fonts.
    ///
    /// The default implementation keeps x and y equal. When both dimensions shrink it uses the
    /// larger shrink ratio, so text still fits within the tighter dimension. When both dimensions
    /// grow it uses the smaller growth ratio, so text does not overflow the looser dimension. Mixed
    /// grow/shrink cases fall back to `1.0f`, which avoids stretching text in only one axis.
    /// Min/max clamps are then applied when configured.
    ///
    /// @return two-element array containing x and y font scale ratios
    protected float[] computeScaleRatios() {
        if (referenceSize == null) {
            return new float[] {1.0f, 1.0f};
        }

        Component referenceComponent = getReferenceComponent();
        if (referenceComponent == null) {
            return new float[] {1.0f, 1.0f};
        }

        float widthRatio = (referenceSize.width == 0)
                ? 1.0f
                : (float) referenceComponent.getWidth() / referenceSize.width;
        float heightRatio = (referenceSize.height == 0)
                ? 1.0f
                : (float) referenceComponent.getHeight() / referenceSize.height;

        float uniformRatio;
        if (widthRatio < 1.0f && heightRatio < 1.0f) {
            uniformRatio = Math.max(widthRatio, heightRatio);
        } else if (widthRatio > 1.0f && heightRatio > 1.0f) {
            uniformRatio = Math.min(widthRatio, heightRatio);
        } else {
            uniformRatio = 1.0f;
        }

        if (minRatio != 0.0f && uniformRatio < minRatio) {
            uniformRatio = minRatio;
        }
        if (maxRatio != 0.0f && uniformRatio > maxRatio) {
            uniformRatio = maxRatio;
        }
        return new float[] {uniformRatio, uniformRatio};
    }

    /// Unbinds every managed component and releases listener registrations.
    ///
    /// Recorded base fonts are restored before bindings are discarded so later reattachment to
    /// another manager starts from the original unscaled font rather than from a stale derived
    /// value.
    public final void dispose() {
        Component referenceComponent = getReferenceComponent();
        if (referenceComponent != null) {
            referenceComponent.removeComponentListener(fontComponentListener);
        }

        synchronized (ScalableFontManager.class) {
            while (!managedComponents.isEmpty()) {
                unbindComponent(managedComponents.getLast());
            }
        }
        derivedFontCache.clear();
    }

    /// Returns the reference component whose size drives scaling.
    ///
    /// @return reference component, or `null` after [#dispose()]
    public final Component getComponent() {
        return getReferenceComponent();
    }

    /// Returns the font that should currently be installed for `font`.
    ///
    /// When scaling is disabled, this method returns the original `font` unchanged. When scaling is
    /// enabled, derived fonts are cached by their unscaled source font until the scale transform or
    /// baseline size changes.
    ///
    /// @param font unscaled source font, or `null`
    /// @return scaled font, or the original `font` when scaling is disabled or unavailable
    public Font getDeriveFont(Font font) {
        if (!isEnabled() || font == null) {
            return font;
        }

        Font derivedFont = derivedFontCache.get(font);
        if (derivedFont == null) {
            derivedFont = deriveScaledFont(font);
            if (derivedFont == null) {
                derivedFont = font;
            } else {
                derivedFontCache.put(font, derivedFont);
            }
        }
        return derivedFont;
    }

    /// Returns the upper clamp applied to computed scale ratios.
    ///
    /// @return maximum scale ratio, or `0.0f` when no upper clamp is active
    public final float getMaxRatio() {
        return maxRatio;
    }

    /// Returns the lower clamp applied to computed scale ratios.
    ///
    /// @return minimum scale ratio, or `0.0f` when no lower clamp is active
    public final float getMinRatio() {
        return minRatio;
    }

    /// Returns a defensive copy of the baseline size used for ratio computation.
    ///
    /// @return cloned baseline size, or `null` when scaling is size-independent
    public Dimension getRefSize() {
        return (referenceSize != null) ? new Dimension(referenceSize) : null;
    }

    /// Returns whether derived fonts are currently applied to managed components.
    ///
    /// @return `true` when scaling is enabled
    public final boolean isEnabled() {
        return enabled;
    }

    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        referenceSize = (referenceSize != null) ? new Dimension(referenceSize) : null;
        List<Font> baseFonts = (List<Font>) in.readObject();
        synchronized (ScalableFontManager.class) {
            for (int index = 0; index < managedComponents.size(); index++) {
                setBindingInfo(managedComponents.get(index), new BindingInfo(this, baseFonts.get(index)));
            }
        }
        derivedFontCache.clear();
        updateScaleTransform();
    }

    /// Enables or disables font scaling for every managed component.
    ///
    /// Toggling this flag immediately reapplies either the derived fonts or the recorded base fonts
    /// to the full managed component set.
    ///
    /// @param enabled `true` to install derived fonts, `false` to restore recorded base fonts
    public void setEnabled(boolean enabled) {
        if (enabled != this.enabled) {
            this.enabled = enabled;
            updateManagedFonts();
        }
    }

    /// Replaces the baseline size used by [#computeScaleRatios()].
    ///
    /// Passing `null` disables size-based scaling and causes later derived-font requests to fall
    /// back to their original fonts.
    ///
    /// @param referenceSize new baseline size, or `null`
    public void setRefSize(Dimension referenceSize) {
        if (Objects.equals(referenceSize, this.referenceSize)) {
            return;
        }
        this.referenceSize = (referenceSize != null) ? new Dimension(referenceSize) : null;
        derivedFontCache.clear();
        updateScaleTransform();
        updateManagedFonts();
    }

    /// Replaces the baseline size and clamp range used to build future scale transforms.
    ///
    /// @param referenceSize new baseline size, or `null`
    /// @param minRatio minimum allowed scale ratio, or `0.0f` for no lower clamp
    /// @param maxRatio maximum allowed scale ratio, or `0.0f` for no upper clamp
    /// @throws IllegalArgumentException when `maxRatio < minRatio`
    public void setScaleTransform(Dimension referenceSize, float minRatio, float maxRatio) {
        if (Objects.equals(referenceSize, this.referenceSize)
                && minRatio == this.minRatio
                && maxRatio == this.maxRatio) {
            return;
        }

        configureScaleTransform(referenceSize, minRatio, maxRatio);
        derivedFontCache.clear();
        updateManagedFonts();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        ArrayList<Font> baseFonts = new ArrayList<>(managedComponents.size());
        for (Component component : managedComponents) {
            BindingInfo bindingInfo = getBindingInfo(component);
            baseFonts.add((bindingInfo != null) ? bindingInfo.baseFont : null);
        }
        out.defaultWriteObject();
        out.writeObject(baseFonts);
    }
}
