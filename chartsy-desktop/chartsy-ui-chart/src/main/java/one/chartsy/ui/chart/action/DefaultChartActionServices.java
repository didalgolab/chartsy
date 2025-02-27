/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.IconResource;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.IndicatorManager;
import one.chartsy.ui.chart.annotation.AnnotationLookup;
import one.chartsy.ui.chart.annotation.ChartAnnotator;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.AnnotationPropertyDialog;
import one.chartsy.ui.chart.components.IndicatorChooserDialog;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.util.Collection;
import java.util.Map;

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

    public static class IndicatorsOpen extends AbstractChartFrameAction {
        public IndicatorsOpen(ChartFrame chart) {
            super(chart);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Collection<Indicator> allPlugins = IndicatorManager.getDefault().getIndicatorsList();
            Collection<Indicator> selectedPlugins = chart.getMainStackPanel().getIndicatorsList();

            IndicatorChooserDialog dialog = new IndicatorChooserDialog(chart, chart::setIndicators);
            dialog.setLocationRelativeTo(chart);
            dialog.initForm(allPlugins, selectedPlugins);
            dialog.setVisible(true);
        }
    }

    public static class AnnotationPopup extends AbstractChartFrameAction {
        public AnnotationPopup(ChartFrame chart) {
            super(chart);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // generate drawing action for all currently installed annotations
            JPopupMenu popup = new JPopupMenu();
            for (Annotation annotation : AnnotationLookup.getAnnotations()) {
                JMenuItem menuItem = (annotation instanceof Presenter.Popup presenterPopup)
                        ? presenterPopup.getPopupPresenter()
                        : new JMenuItem(ChartActions.drawAnnotation(chart, annotation));
                popup.add(menuItem);
            }

            // add global annotation management actions
            popup.addSeparator();
            popup.add(new JCheckBoxMenuItem(new ToggleAnnotations(chart)));
            popup.add(ChartActions.removeAllAnnotations(chart));

            // add actions for currently selected graphic
            if (chart.hasCurrentAnnotation()) {
                Annotation current = chart.getCurrentAnnotation();
                if (current != null)
                    popup.add(ChartActions.annotationProperties(chart, current));
            }

            Component source = (Component) e.getSource();
            popup.show(source, 0, source.getHeight());
        }
    }

    public static class DrawAnnotation extends AbstractChartFrameAction {
        /** The chart frame associated with the action. */
        private final Annotation annotation;

        public DrawAnnotation(ChartFrame chart, Annotation annotation) {
            super(chart);
            putValue(NAME, annotation.getName());
            putValue(SHORT_DESCRIPTION, annotation.getName());
            this.annotation = annotation;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ChartAnnotator.getGlobal().beginDrawing(annotation);
        }
    }

    public static class RemoveAllAnnotations extends AbstractChartFrameAction {
        public RemoveAllAnnotations(ChartFrame chart) {
            super(chart);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            chart.getAnnotationPanels().forEach(AnnotationPanel::removeAllAnnotations);
        }
    }

    public static class ToggleAnnotations extends AbstractChartFrameAction {
        public ToggleAnnotations(ChartFrame chart) {
            super(chart);
            putValue(SELECTED_KEY, !chart.getChartProperties().isAnnotationLayerVisible());
        }

        private long countHiddenAnnotations(ChartFrame chartFrame) {
            long count = 0;
            for (AnnotationPanel panel : chartFrame.getAnnotationPanels())
                if (!panel.isVisible())
                    count += panel.getAnnotationCount();

            return count;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean visible = !Boolean.TRUE.equals(getValue(SELECTED_KEY));

            putValue(SELECTED_KEY, visible);
            for (AnnotationPanel panel : chart.getAnnotationPanels())
                panel.setVisible(visible);

            chart.getChartProperties().setAnnotationLayerVisible(visible);

            JButton button = chart.getChartToolbar().getAnnotationButton();
            if (button != null) {
                button.setFont(button.getFont().deriveFont(
                        Map.of(TextAttribute.STRIKETHROUGH, !visible)));
            }
        }
    }

    public static class AnnotationProperties extends AbstractChartFrameAction {

        private final Annotation annotation;

        public AnnotationProperties(ChartFrame chart, Annotation annotation) {
            super(chart);
            this.annotation = annotation;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            AnnotationPropertyDialog dialog = new AnnotationPropertyDialog();
            dialog.setAnnotation(annotation);
            dialog.setLocationRelativeTo(chart.getMainPanel());
            dialog.setVisible(true);

            // refresh UI when the control is back from modal
            chart.getMainPanel().repaint();
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
