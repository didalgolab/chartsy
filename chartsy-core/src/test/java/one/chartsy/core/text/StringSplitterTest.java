package one.chartsy.core.text;

import one.chartsy.core.text.SplittedString.Fragment;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;

class StringSplitterTest {

    StringSplitter splitter = new StringSplitter(" ,\r\n\t".toCharArray(), "'\"".toCharArray());

    @Test
    void split() {
        assertSplitEquals(List.of(), splitter.split(""));
        assertSplitEquals(List.of(), splitter.split(" "));
        assertSplitEquals(List.of("Abc"), splitter.split("Abc"));
        assertSplitEquals(List.of("Abc", "Def"), splitter.split("Abc Def"));
        assertSplitEquals(List.of("Abc", "Def", "Egg"), splitter.split("Abc Def   Egg "));
        assertSplitEquals(List.of("Abc", "Def", "Egg"), splitter.split("Abc,Def , , Egg,"));
        assertSplitEquals(List.of("Abc", " Def,  "), splitter.split("'Abc', \" Def,  \""));
        assertSplitEquals(List.of("Abc", " Def,  "), splitter.split("'Abc', \" Def,  \r\n\r\n"));
        assertSplitEquals(List.of(" "), splitter.split("' '"));
        assertSplitEquals(List.of(""), splitter.split("''"));
    }

    private static void assertSplitEquals(List<String> expected, SplittedString actual) {
        assertSplitEquals(expected, actual.getFragments());
    }

    private static void assertSplitEquals(List<String> expected, List<Fragment> actual) {
        if (expected.size() != actual.size())
            throw new AssertionFailedError("String split count mismatch", expected, actual);
        for (int i = 0; i < expected.size(); i++)
            if (!expected.get(i).equals(actual.get(i).toString()))
                throw new AssertionFailedError("", expected, actual);
    }
}