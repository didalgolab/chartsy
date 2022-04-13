package one.chartsy.kernel;

import one.chartsy.Symbol;
import org.openide.util.Lookup;

import java.util.List;

/**
 * The choice of symbols or symbol groups the user is interested in. Various
 * system actions can treat the symbol selection provided by user as a default
 * list of symbols to operate on.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface GlobalSymbolSelection {

    List<Symbol> selectedSymbols();

    static GlobalSymbolSelection get() {
        return Lookup.getDefault().lookup(GlobalSymbolSelection.class);
    }
}
