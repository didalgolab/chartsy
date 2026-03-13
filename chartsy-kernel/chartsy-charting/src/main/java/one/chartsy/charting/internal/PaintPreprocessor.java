package one.chartsy.charting.internal;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;

import one.chartsy.charting.PlotStyle;

/// Transforms a [PlotStyle] paint at the last moment before it is installed on a
/// [Graphics2D].
///
/// [PlotStyle] checks the destination graphics context for a preprocessor under [#KEY] each time
/// it is about to install a stroke or fill [Paint]. When present, the style passes its selected
/// paint through [#preprocess(Paint)] and forwards only the returned value to
/// [Graphics2D#setPaint(Paint)].
///
/// This hook lets rendering code keep the owning [PlotStyle] immutable while still deriving a
/// context-sensitive paint from late-bound information such as image bounds, graphics
/// configuration, or caller-installed rendering hints. The current codebase declares the extension
/// point but does not register an implementation automatically; callers opt in by setting the hint
/// themselves on the target graphics context.
public interface PaintPreprocessor {

    /// Rendering-hint key through which [PlotStyle] discovers a preprocessor for the current
    /// graphics context.
    ///
    /// Compatible values are `null` and [PaintPreprocessor] instances. Set this hint with
    /// [Graphics2D#setRenderingHint(RenderingHints.Key, Object)] before delegating to [PlotStyle]
    /// when the final stroke or fill paint should be derived from graphics-local state rather than
    /// used verbatim.
    Key KEY = new Key(1000) {
        @Override
        public boolean isCompatibleValue(Object value) {
            return value == null || value instanceof PaintPreprocessor;
        }
    };

    /// Returns the paint that [PlotStyle] should install for the current draw step.
    ///
    /// Implementations may return the original instance unchanged, wrap it, or replace it with a
    /// different paint derived from the same caller-visible style choice. [PlotStyle] does not
    /// retain the result beyond the immediate [Graphics2D#setPaint(Paint)] call.
    ///
    /// ### API Note
    ///
    /// [PlotStyle] may consult this hook repeatedly during one render pass as it switches between
    /// fill and stroke paints. Implementations should therefore treat this method as a pure
    /// transformation of the supplied paint plus the current graphics state, not as a one-shot
    /// lifecycle callback.
    ///
    /// @param paint source paint selected by the owning [PlotStyle]
    /// @return paint to pass to `Graphics2D.setPaint(Paint)` for the current draw step
    Paint preprocess(Paint paint);
}
