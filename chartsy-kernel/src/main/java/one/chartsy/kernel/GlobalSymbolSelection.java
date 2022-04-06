package one.chartsy.kernel;

import one.chartsy.Symbol;

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

    List<Symbol> getSelectedSymbols();
}
