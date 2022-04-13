package one.chartsy.kernel;

import one.chartsy.Symbol;
import one.chartsy.misc.StyledValue;

import java.text.Format;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ExplorationFragment(
        Symbol symbol,
        Map<String, StyledValue> columnValues) {

    public static Builder builder(Symbol symbol) {
        return new Builder(symbol);
    }

    public static class Builder {
        private final Symbol symbol;
        private final Map<String, StyledValue> columnValues = new TreeMap<>();

        protected Builder(Symbol symbol) {
            this.symbol = Objects.requireNonNull(symbol, "symbol");
        }

        public void addColumn(String name, Object value) {
            StyledValue styled = (value instanceof StyledValue)? (StyledValue)value : StyledValue.of(value);
            addColumn(name, styled);
        }

        public void addColumn(String name, Object value, Format format) {
            addColumn(name, StyledValue.of(value, format));
        }

        public void addColumn(String name, StyledValue value) {
            columnValues.put(name, value);
        }

        public ExplorationFragment build() {
            return new ExplorationFragment(symbol, columnValues);
        }
    }
}
