package one.chartsy.data.provider;

import one.chartsy.Symbol;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface SymbolProposalProvider {

    /**
     * Returns the symbol completion proposals for the provided keyword
     *
     * @param keyword
     *            the text entered by user
     * @return the list of symbol proposals
     * @throws IOException
     *             if an IO error occurred while executing the method
     */
    List<Symbol> getProposals(String keyword) throws IOException;

}
