package one.chartsy.data.packed;

import one.chartsy.HLC;
import one.chartsy.data.AbstractDataset;
import one.chartsy.data.ChronologicalDataset;
import one.chartsy.data.ChronologicalDatasetTimeline;
import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.nio.ByteBuffer;

public class ByteBufferMutableHLCDataset extends AbstractDataset<HLC> implements ChronologicalDataset {
    private static final int DEFAULT_CAPACITY = 512, RAW_BYTES = 32;

    private final long downsampleMicros;
    private long lastTime = Long.MIN_VALUE;
    private double lastHigh = Double.NEGATIVE_INFINITY;
    private double lastLow = Double.POSITIVE_INFINITY;

    private ByteBuffer buffer;
    private Timeline timeline;


    public ByteBufferMutableHLCDataset() {
        this(1L);
    }

    public ByteBufferMutableHLCDataset(long downsampleMicros) {
        this(ByteBuffer.wrap(new byte[RAW_BYTES * DEFAULT_CAPACITY]), downsampleMicros);
    }

    public ByteBufferMutableHLCDataset(ByteBuffer buffer) {
        this(buffer, 1L);
    }

    public ByteBufferMutableHLCDataset(ByteBuffer buffer, long downsampleMicros) {
        this.downsampleMicros = downsampleMicros;
        this.buffer = buffer;
    }

    public final long getDownsampleMicros() {
        return downsampleMicros;
    }

    public final int getDownsampleSeconds() {
        return (int) (getDownsampleMicros()/1000_000L);
    }

    @Override
    public int length() {
        return buffer.position()/RAW_BYTES;
    }

    @Override
    public HLC get(int index) {
        int offset   = indexToModel(index);
        long time    = buffer.getLong(offset);
        double high  = buffer.getDouble(offset += 8);
        double low   = buffer.getDouble(offset += 8);
        double close = buffer.getDouble(offset += 8);
        return new HLC(time, high, low, close);
    }

    public long getTimeAt(int index) {
        int offset = indexToModel(index);
        return buffer.getLong(offset);
    }

    protected int indexToModel(int index) {
        return (buffer.position()/RAW_BYTES - index - 1)*RAW_BYTES;
    }

    protected ByteBuffer expandBuffer(ByteBuffer buffer, int newProposedCapacity) {
        ByteBuffer newBuffer = buffer.isDirect()?
                ByteBuffer.allocateDirect(newProposedCapacity)
                : ByteBuffer.allocate(newProposedCapacity);
        buffer.position(0);
        newBuffer.put(buffer);
        return newBuffer;
//        byte[] oldArray = buffer.array();
//        byte[] newArray = new byte[newProposedCapacity];
//        System.arraycopy(oldArray, 0, newArray, 0, oldArray.length);
//        return ByteBuffer.wrap(newArray, buffer.position(), newArray.length - buffer.position());
    }

    public void add(long time, double value) {
        // check if buffer limit has been reached
        if (buffer.remaining() == 0)
            buffer = expandBuffer(buffer, buffer.capacity() * 2);

        // optional downsampling
        if (downsampleMicros != 1L)
            time = ((time - 1)/ downsampleMicros + 1)* downsampleMicros;

        // add current value and timestamp to the buffer
        if (time > lastTime) {
            buffer.putLong(lastTime = time);
            buffer.putDouble(value);
            buffer.putDouble(value);
            buffer.putDouble(value);

            lastHigh = lastLow = value;
        } else {
            if (value > lastHigh)
                buffer.putDouble(buffer.position() - 24, (lastHigh = value));
            if (value < lastLow)
                buffer.putDouble(buffer.position() - 16, (lastLow = value));

            buffer.putDouble(buffer.position() - 8, value);
        }
    }

    public Timeline getTimeline() {
        if (timeline == null)
            timeline = createTimeline();
        return timeline;
    }

    protected Timeline createTimeline() {
        return new ChronologicalDatasetTimeline(this, Chronological.Order.REVERSE_CHRONOLOGICAL);
    }
}
