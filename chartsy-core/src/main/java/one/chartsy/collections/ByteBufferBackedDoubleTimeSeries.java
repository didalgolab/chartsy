/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.collections;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class ByteBufferBackedDoubleTimeSeries {

    private final Head head = new Head();
    private ByteBuffer data;

    public ByteBufferBackedDoubleTimeSeries(ByteBuffer data) {
        this.data = data;
    }

    public int length() {
        return head.index();
    }

    public double get(int index) {
        return head.getDouble(data, index);
    }

    public void add(double val) {
        try {
            head.addDouble(data, val);

        } catch (BufferOverflowException e) {
            head.addDouble(data = growByteBuffer(
                    data.position(data.position() / 16 * 16)), val);
        }
        //head.shift(1);
        //data.position(data.position() + 8);
    }

    public void clear() {
        head.reset();
        data.position(0);
    }

    private static ByteBuffer growByteBuffer(ByteBuffer buf) {
        byte[] oldArray = buf.array();
        byte[] newArray = new byte[oldArray.length * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldArray.length);
        return ByteBuffer.wrap(newArray, buf.position(), newArray.length - buf.position());
    }

    static class Head {

        private int index;

        public int index() {
            return index;
        }
        
        public void shift(int n) {
            index += n;
        }

        public void reset() {
            index = 0;
        }

        public double getDouble(ByteBuffer buf, int i) {
            return buf.getDouble((index - i - 1)*8);
        }

        public void setDouble(ByteBuffer buf, int i, double val) {
            buf.putDouble((index - i - 1)*8, val);
        }

        public void addDouble(ByteBuffer buf, double val) {
            buf.putDouble(val);
            index++;
        }

        public double getLong(ByteBuffer buf, int i) {
            return buf.getDouble((index - i - 1)*8);
        }

        public void setLong(ByteBuffer buf, int i, double val) {
            buf.putDouble((index - i - 1)*8, val);
        }

        public ByteBuffer ensureRemainingCapacity(ByteBuffer buf, int remaining) {
            if (buf.isDirect())
                throw new UnsupportedOperationException();
            if (buf.remaining() < remaining)
                return growByteBuffer(buf);
            return buf;
        }
    }

    public static void main(String[] args) {
        ByteBuffer buff = ByteBuffer.allocate(16);
        buff.putInt(1);
        buff.putInt(2);
        buff.putInt(3);
        buff.position(0);
        buff.putInt(4);
        System.out.println(buff.getInt(0));
        System.out.println(buff.getInt(4));
        System.out.println(buff.getInt(8));

    }
}
