/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public interface LongSequence extends SequenceAlike<Long, LongSequence> {

    long get(int index);

    @Override
    default LongStream stream() {
        return IntStream.range(0, length()).mapToLong(this::get);
    }

    @Override
    default PrimitiveIterator.OfLong iterator() {
        return stream().iterator();
    }
}
