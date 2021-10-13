package one.chartsy.trade;

import one.chartsy.data.Series;
import one.chartsy.time.Chronological;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.LocalDate;
import java.util.Map;

public abstract class Strategy<E extends Chronological> implements TradingStrategy {

    public final Class<E> primaryDataType;

    protected /*final*/ Series<E> series ;


    protected Strategy() {
        this.primaryDataType = primaryDataType();
    }

    protected Strategy(Class<E> primaryDataType) {
        this.primaryDataType = primaryDataType;
    }

    @SuppressWarnings("unchecked")
    private Class<E> primaryDataType() {
        Map<TypeVariable<?>, Type> typeArguments = TypeUtils.getTypeArguments(getClass(), Strategy.class);
        Type type = typeArguments.get(Strategy.class.getTypeParameters()[0]);
        return (Class<E>) ((type instanceof Class)? type: Chronological.class);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
    }
}
