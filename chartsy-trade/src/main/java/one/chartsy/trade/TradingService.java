package one.chartsy.trade;

import one.chartsy.FinancialService;

import java.util.List;
import java.util.Optional;

public interface TradingService extends FinancialService {

    OrderBroker getOrderBroker();

    List<Account> getAccounts();

    default Optional<Account> getAccount(String id) {
        return getAccounts().stream()
                .filter(account -> id.equals(account.getId()))
                .findFirst();
    }
}
