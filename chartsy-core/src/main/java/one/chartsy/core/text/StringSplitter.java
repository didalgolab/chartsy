/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.text;

import one.chartsy.core.text.SplittedString.Fragment;

import java.util.ArrayList;
import java.util.List;

public class StringSplitter {

    private static final char UNQUOTED = 0;
    private final char[] splitCharacters;
    private final char[] quoteCharacters;

    public StringSplitter(char[] splitCharacters, char[] quoteCharacters) {
        this.splitCharacters = splitCharacters.clone();
        this.quoteCharacters = quoteCharacters.clone();
    }

    public char[] getSplitCharacters() {
        return splitCharacters.clone();
    }

    public char[] getQuoteCharacters() {
        return quoteCharacters.clone();
    }

    public SplittedString split(String s) {
        List<Fragment> fragments = new ArrayList<>(2);

        char quoteChar = UNQUOTED;
        int currFragmentStart = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (contains(ch, splitCharacters) && (ch == '\r' || ch == '\n' || quoteChar == UNQUOTED)) {
                if (currFragmentStart < i)
                    createFragment(fragments, s, currFragmentStart, i, quoteChar);
                quoteChar = UNQUOTED;
                currFragmentStart = i + 1;
            } else if (contains(ch, quoteCharacters)) {
                if (currFragmentStart < i)
                    createFragment(fragments, s, currFragmentStart, i, quoteChar);
                quoteChar = (quoteChar == UNQUOTED)? ch : UNQUOTED;
                currFragmentStart = (quoteChar == UNQUOTED)? (i + 1) : i;
            }
        }

        if (currFragmentStart < s.length())
            createFragment(fragments, s, currFragmentStart, s.length(), quoteChar);

        return new SplittedString(fragments);
    }

    protected void createFragment(List<Fragment> fragments, String source, int fragmentStart, int fragmentEnd, char quoteChar) {
        if (quoteChar != UNQUOTED) {
            fragmentStart++;
            if (fragmentEnd > fragmentStart && source.charAt(fragmentEnd - 1) == quoteChar)
                fragmentEnd--;
        }
        fragments.add(new Fragment.Of(source, fragmentStart, fragmentEnd));
    }

    private static boolean contains(char ch, char[] chars) {
        for (char aChar : chars)
            if (aChar == ch)
                return true;

        return false;
    }
}
