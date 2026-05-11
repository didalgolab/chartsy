package one.chartsy.charting.swing;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JLabel;

import one.chartsy.charting.util.GraphicUtil;

/// Extends [JLabel] with optional per-component antialiasing overrides and a paint path that can
/// render only the label's foreground content.
///
/// Both antialiasing properties are tri-state. `Boolean.TRUE` and `Boolean.FALSE` temporarily
/// force the corresponding [Graphics2D] rendering hint while this label paints, whereas `null`
/// preserves whatever hint the caller already configured.
///
/// [one.chartsy.charting.Chart] uses [#paintForegroundContent(Graphics, Rectangle)] when it has
/// already painted the label background and border itself and only needs Swing to render the icon
/// and text into a supplied rectangle.
public class JLabelExt extends JLabel {
    private Boolean antiAliasing;
    private Boolean textAntiAliasing;

    private record HintState(
            boolean antialiasingOverridden,
            Object previousAntialiasing,
            boolean textAntialiasingOverridden,
            Object previousTextAntialiasing) {
    }

    /// Creates an empty label with no rendering-hint overrides.
    public JLabelExt() {
    }

    /// Creates a label that displays the supplied icon.
    ///
    /// @param image icon to display
    public JLabelExt(Icon image) {
        super(image);
    }

    /// Creates a label that displays the supplied icon using the given horizontal alignment.
    ///
    /// @param image icon to display
    /// @param horizontalAlignment Swing horizontal alignment constant
    public JLabelExt(Icon image, int horizontalAlignment) {
        super(image, horizontalAlignment);
    }

    /// Creates a label that displays the supplied text.
    ///
    /// @param text initial label text
    public JLabelExt(String text) {
        super(text);
    }

    /// Creates a label that displays the supplied text and icon.
    ///
    /// @param text initial label text
    /// @param icon icon to display alongside the text
    /// @param horizontalAlignment Swing horizontal alignment constant
    public JLabelExt(String text, Icon icon, int horizontalAlignment) {
        super(text, icon, horizontalAlignment);
    }

    /// Creates a label that displays the supplied text using the given horizontal alignment.
    ///
    /// @param text initial label text
    /// @param horizontalAlignment Swing horizontal alignment constant
    public JLabelExt(String text, int horizontalAlignment) {
        super(text, horizontalAlignment);
    }

    private HintState applyConfiguredHints(Graphics2D g) {
        boolean antialiasingOverridden = false;
        Object previousAntialiasing = null;
        if (antiAliasing != null) {
            previousAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            antialiasingOverridden = true;
        }

        boolean textAntialiasingOverridden = false;
        Object previousTextAntialiasing = null;
        if (textAntiAliasing != null) {
            previousTextAntialiasing = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    textAntiAliasing
                            ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            textAntialiasingOverridden = true;
        }

        return new HintState(
                antialiasingOverridden,
                previousAntialiasing,
                textAntialiasingOverridden,
                previousTextAntialiasing);
    }

    private static void restoreConfiguredHints(Graphics2D g, HintState state) {
        if (state.textAntialiasingOverridden()) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, state.previousTextAntialiasing());
        }
        if (state.antialiasingOverridden()) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, state.previousAntialiasing());
        }
    }

    private Icon getEffectiveIcon() {
        return isEnabled() ? getIcon() : getDisabledIcon();
    }

    /// Returns the override for shape antialiasing.
    ///
    /// `null` means this label does not override the incoming
    /// `RenderingHints.KEY_ANTIALIASING` value.
    public Boolean getAntiAliasing() {
        return antiAliasing;
    }

    /// Returns the override for text antialiasing.
    ///
    /// `null` means this label does not override the incoming
    /// `RenderingHints.KEY_TEXT_ANTIALIASING` value.
    public Boolean getAntiAliasingText() {
        return textAntiAliasing;
    }

    /// Paints the full label while temporarily applying this label's configured rendering-hint
    /// overrides.
    ///
    /// @param g target graphics, expected to be a `Graphics2D` instance
    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        HintState hintState = applyConfiguredHints(g2);
        try {
            super.paintComponent(g2);
        } finally {
            restoreConfiguredHints(g2, hintState);
        }
    }

    /// Paints only the label's icon and text into the supplied bounds.
    ///
    /// This method intentionally skips the normal background fill and border painting. It is used
    /// when a chart has already drawn those parts itself and only wants Swing's foreground layout
    /// logic. If the label currently has neither text nor an effective icon, nothing is painted.
    ///
    /// @param g target graphics, expected to be a `Graphics2D` instance
    /// @param bounds label bounds to use for Swing text/icon layout
    public void paintForegroundContent(Graphics g, Rectangle bounds) {
        if (getText() == null && getEffectiveIcon() == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        HintState hintState = applyConfiguredHints(g2);
        try {
            GraphicUtil.paintJLabel(g, this, bounds);
        } finally {
            restoreConfiguredHints(g2, hintState);
        }
    }

    /// Sets the override for non-text antialiasing.
    ///
    /// @param antiAliasing override value, or `null` to preserve the caller's existing hint
    public void setAntiAliasing(Boolean antiAliasing) {
        if (Objects.equals(antiAliasing, this.antiAliasing)) {
            return;
        }
        this.antiAliasing = antiAliasing;
        repaint();
    }

    /// Sets the override for text antialiasing.
    ///
    /// @param textAntiAliasing override value, or `null` to preserve the caller's existing hint
    public void setAntiAliasingText(Boolean textAntiAliasing) {
        if (Objects.equals(textAntiAliasing, this.textAntiAliasing)) {
            return;
        }
        this.textAntiAliasing = textAntiAliasing;
        repaint();
    }
}
