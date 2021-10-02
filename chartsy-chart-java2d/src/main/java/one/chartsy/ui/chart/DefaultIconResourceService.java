package one.chartsy.ui.chart;

import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.*;

@ServiceProvider(service = IconResourceService.class)
public class DefaultIconResourceService implements IconResourceService {

    @Override
    public Icon getIcon(String name) {
        var lastDot = name.lastIndexOf('.');
        if (lastDot < 0 || lastDot - name.length() < -4)
            name += ".png";

        return ImageUtilities.loadImageIcon(name,true);
    }

    public static void main(String[] args) {
        IconResourceService service = Lookup.getDefault().lookup(IconResourceService.class);
        System.out.println(service);
    }
}
