package one.chartsy.ide.engine.actions;

import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class LastActivatedWindowLookup extends ProxyLookup implements PropertyChangeListener {

    public static final Lookup INSTANCE = new LastActivatedWindowLookup();

    private final TopComponent.Registry topComponents = TopComponent.getRegistry();

    LastActivatedWindowLookup() {
        topComponents.addPropertyChangeListener(this);
        updateLookups();
    }

    private void updateLookups() {
        Node[] nodes = topComponents.getActivatedNodes();
        Lookup[] delegates = new Lookup[nodes.length];
        for (int i = 0; i < nodes.length; i++)
            delegates[i] = nodes[i].getLookup();
        setLookups(delegates);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (TopComponent.Registry.PROP_ACTIVATED_NODES.equals(e.getPropertyName()))
            updateLookups();
    }
}
