package one.chartsy.charting.internal;

import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JComponent;

/// Base implementation of [PaintAction] for export helpers that only need conditional
/// temporary reparenting plus size-adjustment hooks.
///
/// The class selects an export root through [#getRootComponent(JComponent)]. If that root is
/// currently detached from Swing, it is mounted into the constructor-supplied temporary parent
/// with a `null` layout, validated once, and later removed by
/// [#disposeComponent(JComponent)]. When the selected root already belongs to a live hierarchy,
/// this base class leaves the hierarchy untouched and skips
/// [#resizeComponent(JComponent, Dimension)].
///
/// Instances keep mutable per-export state in [#temporarilyReparented] and are therefore meant
/// to serve a single off-screen paint pass at a time.
public abstract class AbstractPaintAction implements PaintAction {

    private final Container temporaryParent;

    /// Tracks whether [#checkHierarchy(JComponent)] attached the selected root during the current
    /// export pass.
    protected boolean temporarilyReparented;

    /// Creates the helper backed by a temporary Swing parent.
    ///
    /// The parent is consulted only when [#getRootComponent(JComponent)] returns a detached root
    /// that must be mounted into a hierarchy before painting.
    ///
    /// @param temporaryParent container used as the transient parent for a detached export root
    public AbstractPaintAction(Container temporaryParent) {
        assert temporaryParent != null;
        this.temporaryParent = temporaryParent;
    }

    @Override
    public void checkHierarchy(JComponent c) {
        temporarilyReparented = false;

        JComponent root = getRootComponent(c);
        if (root.getParent() == null) {
            temporaryParent.add(root);
            root.getParent().setLayout(null);
            root.getParent().validate();
            temporarilyReparented = true;
        }
    }

    /// Removes the export root when this instance attached it earlier in the same pass.
    ///
    /// Existing live hierarchies are left unchanged.
    @Override
    public void disposeComponent(JComponent c) {
        if (temporarilyReparented) {
            temporaryParent.remove(getRootComponent(c));
            temporarilyReparented = false;
        }
    }

    /// Returns the component whose parentage governs the export pass.
    ///
    /// Subclasses typically return either `c` itself or an owning chart when the exported
    /// component depends on ancestor-managed layout or bounds.
    ///
    /// @param c component requested for off-screen painting
    /// @return component that should be temporarily attached when the export target is detached
    protected abstract JComponent getRootComponent(JComponent c);

    /// Applies size-dependent state after a temporary attachment has been established.
    ///
    /// When the export target already belongs to a live hierarchy, the base implementation keeps
    /// the current size and skips the subclass resize hook.
    @Override
    public void prepareComponent(JComponent c, Dimension size) {
        if (temporarilyReparented) {
            resizeComponent(c, size);
        }
    }

    /// Resizes the exported component and any dependent owner state after temporary reparenting.
    ///
    /// This hook runs only after [#checkHierarchy(JComponent)] attached the selected root for the
    /// current pass.
    ///
    /// @param c component being painted, which may be a child of the temporarily attached root
    /// @param size destination image size in pixels for the current export pass
    protected abstract void resizeComponent(JComponent c, Dimension size);
}
