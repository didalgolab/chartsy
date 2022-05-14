/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.data.packed.PackedLongDataset;

import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * A dataset specialized for long values.
 *
 * @author Mariusz Bernacki
 */
public interface LongDataset extends SequenceAlike<Long, LongDataset> {

    long get(int index);

    @Override
    default LongStream stream() {
        return IntStream.range(0, length()).mapToLong(this::get);
    }

    @Override
    default LongDataset take(int start, int count) {
        if (start < 0)
            throw new IllegalArgumentException("The `start` argument cannot be negative");
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (length() < count - start)
            throw new IllegalArgumentException("The take end index cannot exceed dataset length " + length());

        return new LongDataset() {
            @Override
            public int length() {
                return count;
            }

            @Override
            public long get(int index) {
                return LongDataset.this.get(start + Datasets.requireValidIndex(index, this));
            }
        };
    }

    default LongDataset toDirect() {
        return PackedLongDataset.from(this);
    }
}
