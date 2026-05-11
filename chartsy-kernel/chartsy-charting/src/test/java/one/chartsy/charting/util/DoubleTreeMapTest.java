package one.chartsy.charting.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DoubleTreeMapTest {

    @Test
    void rejects_nan_keys_even_when_empty() {
        var map = newMap();

        assertAll(
                () -> assertRejectsNaN(() -> map.containsKey(Double.NaN)),
                () -> assertRejectsNaN(() -> map.get(Double.NaN)),
                () -> assertRejectsNaN(() -> map.getEntry(Double.NaN)),
                () -> assertRejectsNaN(() -> map.put(Double.NaN, "nan")),
                () -> assertRejectsNaN(() -> map.remove(Double.NaN))
        );
    }

    @Test
    void failed_nan_put_leaves_existing_entries_untouched() {
        var map = newMap(
            entry(1.0, "one"),
            entry(2.0, "two"),
            entry(3.0, "three"));

        assertRejectsNaN(() -> map.put(Double.NaN, "nan"));

        assertAll(
                () -> assertThat(map.size()).isEqualTo(3),
                () -> assertThat(map.entrySet().stream().map(DoubleTreeMap.Entry::getKey).toList())
                        .containsExactly(1.0, 2.0, 3.0),
                () -> assertThat(map.entrySet().stream().map(DoubleTreeMap.Entry::getValue).toList())
                        .containsExactly("one", "two", "three")
        );
    }

    @Test
    void entry_set_contains_and_remove_distinguish_present_null_from_absent_null() {
        var map = newMap(entry(1.0, null));

        var entries = map.entrySet();
        var presentNull = entry(1.0, null);
        var absentNull = entry(2.0, null);

        assertAll(
                () -> assertThat(entries.contains(presentNull)).isTrue(),
                () -> assertThat(entries.contains(absentNull)).isFalse(),
                () -> assertThat(entries.remove(absentNull)).isFalse(),
                () -> assertThat(entries.contains(presentNull)).isTrue(),
                () -> assertThat(entries.remove(presentNull)).isTrue(),
                () -> assertThat(map.containsKey(1.0)).isFalse(),
                () -> assertThat(map.size()).isZero()
        );
    }

    @Test
    void entry_set_contains_and_remove_reject_nan_candidate_keys() {
        var map = newMap(entry(1.0, "one"));

        var nanCandidate = entry(Double.NaN, "nan");

        assertAll(
                () -> assertRejectsNaN(() -> map.entrySet().contains(nanCandidate)),
                () -> assertRejectsNaN(() -> map.entrySet().remove(nanCandidate))
        );
    }

    @Test
    void put_updates_existing_non_nan_key() {
        var map = newMap();

        var previous = map.put(1.0, "one");
        var replaced = map.put(1.0, "uno");

        assertAll(
                () -> assertThat(previous).isNull(),
                () -> assertThat(replaced).isEqualTo("one"),
                () -> assertThat(map.size()).isEqualTo(1),
                () -> assertThat(map.get(1.0)).isEqualTo("uno")
        );
    }

    private static void assertRejectsNaN(Executable executable) {
        var thrown = assertThrows(IllegalArgumentException.class, executable);

        assertThat(thrown).hasMessage("Key must not be NaN");
    }

    @SafeVarargs
    private static DoubleTreeMap<String> newMap(DoubleTreeMap.Entry<String>... entries) {
        var map = new DoubleTreeMap<String>(1);
        for (var entry : entries)
            map.put(entry.getKey(), entry.getValue());
        return map;
    }

    private static DoubleTreeMap.Entry<String> entry(double key, String value) {
        return new DoubleTreeMap.Entry<>(key, value);
    }
}
