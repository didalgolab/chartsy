/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.text;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SplittedString {

    private final List<Fragment> fragments;


    public SplittedString(List<Fragment> fragments) {
        this.fragments = fragments;
    }

    public List<Fragment> getFragments() {
        return fragments;
    }

    public Optional<Fragment> getFragmentAt(int position) {
        for (Fragment fragment : getFragments())
            if (fragment.positionStart() <= position && position <= fragment.positionEnd())
                return Optional.of(fragment);

        return Optional.empty();
    }

    public int getFragmentCount() {
        return getFragments().size();
    }

    public Stream<Fragment> fragments() {
        return getFragments().stream();
    }

    public static SplittedString empty() {
        return new SplittedString(List.of());
    }

    public interface Fragment extends CharSequence {
        int positionStart();
        int positionEnd();

        default int length() {
            return positionEnd() - positionStart();
        }


        record Of(String source, int positionStart, int positionEnd) implements Fragment {

            @Override
            public char charAt(int index) {
                if (index < 0 || index >= length()) {
                    throw new StringIndexOutOfBoundsException(index);
                }
                return source.charAt(positionStart + index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                if (start < 0 || start > end || end > length()) {
                    throw new StringIndexOutOfBoundsException(
                            "start " + start + ", end " + end + ", length " + length());
                }
                return source.subSequence(positionStart + start, positionStart + end);
            }

            @Override
            public String toString() {
                return source.substring(positionStart, positionEnd);
            }
        }
    }
}
