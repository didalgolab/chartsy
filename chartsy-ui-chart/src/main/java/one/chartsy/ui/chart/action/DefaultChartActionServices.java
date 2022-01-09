package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.IconResource;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.*;
import java.awt.event.ActionEvent;

@ServiceProvider(service = ChartActionServices.class)
public class DefaultChartActionServices implements ChartActionServices {

    @Override
    public Action find(String name, Object... args) {
        try {
            Class<?> actionClass = Class.forName(getClass().getName() + '$' + name);
            for (var constructor : actionClass.getDeclaredConstructors()) {
                if (constructor.isVarArgs() || constructor.getParameterCount() == args.length) {
                    try {
                        return (Action)constructor.newInstance(args);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                throw new UnsupportedOperationException(name);
            }
        };
    }

    @Override
    public void execute(String action, Object... args) {
        throw new UnsupportedOperationException();
    }

    protected abstract static class AbstractChartAction extends AbstractAction {
        protected AbstractChartAction() {
            Class<?> rbClass = getClass();
            String name = rbClass.getSimpleName();
            if (rbClass.getEnclosingClass() != null)
                rbClass = rbClass.getEnclosingClass();

            putValue(SMALL_ICON, IconResource.getIcon(name));
            putValue(NAME, NbBundle.getMessage(rbClass, name));
        }
    }

    protected abstract static class AbstractChartFrameAction extends AbstractChartAction {
        protected final ChartFrame chart;

        private AbstractChartFrameAction(ChartFrame chart) {
            this.chart = chart;
        }
    }

    public static class ZoomIn extends AbstractChartFrameAction {
        public ZoomIn(ChartFrame chart) {
            super(chart);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            chart.zoomIn();
        }
    }

    public static class ZoomOut extends AbstractChartFrameAction {
        public ZoomOut(ChartFrame chart) {
            super(chart);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            chart.zoomOut();
        }
    }

    public static class ComponentPopupMenuShow extends AbstractChartAction {
        public ComponentPopupMenuShow() {
            super();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof JComponent source) {
                JPopupMenu popup = source.getComponentPopupMenu();
                if (popup != null)
                    popup.show(source, 0, source.getHeight());
            }
        }
    }
}
