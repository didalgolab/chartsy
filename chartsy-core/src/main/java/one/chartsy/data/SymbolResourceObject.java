package one.chartsy.data;

import lombok.Getter;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;

@Getter
public class SymbolResourceObject<E> implements SymbolResource<E> {
    private final SymbolIdentity symbol;
    private final TimeFrame timeFrame;
    private final Class<? extends E> dataType;

    protected SymbolResourceObject(SymbolIdentity symbol, TimeFrame timeFrame, Class<? extends E> dataType) {
        this.symbol = symbol;
        this.timeFrame = timeFrame;
        this.dataType = dataType;
    }

    @Override
    public int hashCode() {
        return symbol.hashCode() ^ timeFrame.hashCode() ^ dataType.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (o instanceof SymbolResourceObject) {
            SymbolResourceObject<E> that = (SymbolResourceObject<E>) o;
            return symbol.equals(that.symbol) && timeFrame.equals(that.timeFrame) && dataType.equals(that.dataType);
        }
        return false;
    }
}
