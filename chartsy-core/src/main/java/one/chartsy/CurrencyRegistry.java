package one.chartsy;

import java.util.List;

public interface CurrencyRegistry {

    List<Currency> getCurrencies();

    Currency getCurrency(String currencyCode);
}
