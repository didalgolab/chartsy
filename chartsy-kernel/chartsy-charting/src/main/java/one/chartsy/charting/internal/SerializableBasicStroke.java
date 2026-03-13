package one.chartsy.charting.internal;

import java.awt.BasicStroke;
import java.awt.Stroke;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

import one.chartsy.charting.PlotStyle;

/// Serializable wrapper for a [BasicStroke] stored inside serializable chart styles.
///
/// [PlotStyle] uses this helper because `BasicStroke` itself is not serializable. The wrapper also
/// remembers whether the captured stroke was exactly [PlotStyle#DEFAULT_STROKE] so deserialization
/// can recover that shared singleton instead of a value-equal copy.
public final class SerializableBasicStroke implements Serializable {
    private transient BasicStroke stroke;
    private final boolean defaultStroke;

    /// Captures `stroke` for later serialization.
    ///
    /// @param stroke the `BasicStroke` to wrap
    public SerializableBasicStroke(BasicStroke stroke) {
        this.stroke = stroke;
        defaultStroke = stroke == PlotStyle.DEFAULT_STROKE;
    }

    /// Returns the wrapped stroke.
    ///
    /// When the original stroke was [PlotStyle#DEFAULT_STROKE], this method restores that shared
    /// singleton rather than returning the deserialized reconstruction.
    public Stroke getStroke() {
        return defaultStroke ? PlotStyle.DEFAULT_STROKE : stroke;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        float lineWidth = in.readFloat();
        int endCap = in.readInt();
        int lineJoin = in.readInt();
        float miterLimit = in.readFloat();
        float[] dashArray = (float[]) in.readObject();
        float dashPhase = in.readFloat();
        stroke = new BasicStroke(lineWidth, endCap, lineJoin, miterLimit, dashArray, dashPhase);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeFloat(stroke.getLineWidth());
        out.writeInt(stroke.getEndCap());
        out.writeInt(stroke.getLineJoin());
        out.writeFloat(stroke.getMiterLimit());
        out.writeObject(stroke.getDashArray());
        out.writeFloat(stroke.getDashPhase());
    }
}
