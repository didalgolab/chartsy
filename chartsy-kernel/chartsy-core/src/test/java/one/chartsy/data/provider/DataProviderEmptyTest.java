package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.data.SimpleCandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataProviderEmptyTest {

    @Test
    void is_singleton() {
        DataProvider first = DataProvider.EMPTY;
        DataProvider second = DataProvider.EMPTY;
        assertThat(first).isSameAs(second);
        assertThat(first.getName()).isEqualTo("EMPTY");
    }

    @Test
    void lists_no_symbols_or_groups() {
        assertThat(DataProvider.EMPTY.listSymbolGroups()).isEmpty();
        assertThat(DataProvider.EMPTY.listSymbols(SymbolGroup.BASE)).isEmpty();
        assertThat(DataProvider.EMPTY.listSymbols()).isEmpty();
    }

    @Test
    void query_returns_no_data() {
        var result = DataProvider.EMPTY.query(SimpleCandle.class, null).collectList().block();
        assertThat(result).isNotNull().isEmpty();
    }
}
