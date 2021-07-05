package one.chartsy;

import org.openide.util.Lookup;

/**
 * A common base class for all financial-related services.
 *
 * @author Mariusz Bernacki
 */
public interface FinancialService extends Lookup.Provider {

    default String getName() {
        return getClass().getSimpleName();
    }
}
