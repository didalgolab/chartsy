package one.chartsy.ui.navigator;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.openide.windows.TopComponent;

/**
 * The {@code GlobalSymbolsTab} is an instance of the symbols navigation
 * available globally in the Chartsy|One application as a singleton instance.
 * The {@code GlobalSymbolsTab} is visible in the GUI as a "Symbols" view tab
 * and is opened at the application startup. The "Symbols" tab allows user to
 * select list of symbols or symbol groups. This symbol selection can be anytime
 * accessed programmatically using the {@link GlobalSymbolSelection} instance
 * from the global lookup. The selection acts as a default choice of symbols to
 * be operated on by various system actions. For example the Exploration Action
 * can use this default selection as a choice of symbols over which the market
 * screener task is launched.
 * 
 * @author Mariusz Bernacki
 *
 */
@TopComponent.Description(preferredID = "GlobalSymbolsTab2", iconBase = "one/chartsy/desktop/resources/symbol-group (16 px).png", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@ServiceProviders({
    @ServiceProvider(service = GlobalSymbolsTab2.class)
})
public class GlobalSymbolsTab2 extends SymbolsTab {

    @Registration(mode = "explorer", position = 152, openAtStartup = true)
    @OpenActionRegistration(displayName = "Symbols 2", preferredID = "GlobalSymbolsTab2")
    public static GlobalSymbolsTab2 getDefault() {
        return Lookup.getDefault().lookup(GlobalSymbolsTab2.class);
    }

    @Override
    protected Object writeReplace() {
        return new SerializationProxy();
    }
    
    private static class SerializationProxy implements java.io.Serializable {
        private static final long serialVersionUID = 2L;
        
        private Object readResolve() {
            return getDefault();
        }
    }
}
