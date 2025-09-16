/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.components;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.TimeFrameHelper;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartFrameListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public class TimeFrameSelector extends JComboBox<TimeFrame> implements ChartFrameListener {

    private static final long MONTH_TO_SECONDS = 30L * 24 * 60 * 60;

    private final ChartFrame chartFrame;
    private boolean listening;
    private boolean updating;

    TimeFrameSelector(ChartFrame chartFrame) {
        this.chartFrame = chartFrame;
        setModel(new DefaultComboBoxModel<>());
        setRenderer(new TimeFrameRenderer());
        setMaximumRowCount(16);
        setFocusable(false);
        setOpaque(false);
        setToolTipText("Change chart time frame");
        setPrototypeDisplayValue(TimeFrame.Period.QUARTERLY);

        addActionListener(event -> {
            if (!updating) {
                TimeFrame selected = (TimeFrame) getSelectedItem();
                ChartData data = chartFrame.getChartData();
                if (selected != null && data != null && !selected.equals(data.getTimeFrame()))
                    chartFrame.timeFrameChanged(selected);
            }
        });

        updateTimeFrames();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listening) {
            chartFrame.addChartFrameListener(this);
            listening = true;
            updateTimeFrames();
        }
    }

    @Override
    public void removeNotify() {
        if (listening) {
            chartFrame.removeChartFrameListener(this);
            listening = false;
        }
        super.removeNotify();
    }

    @Override
    public void symbolChanged(SymbolIdentity newSymbol) {
        SwingUtilities.invokeLater(this::updateTimeFrames);
    }

    @Override
    public void timeFrameChanged(TimeFrame newTimeFrame) {
        SwingUtilities.invokeLater(this::updateTimeFrames);
    }

    @Override
    public void datasetChanged(CandleSeries quotes) {
        SwingUtilities.invokeLater(this::updateTimeFrames);
    }

    private void updateTimeFrames() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateTimeFrames);
            return;
        }

        ChartData chartData = chartFrame.getChartData();
        if (chartData == null)
            return;

        DataProvider provider = chartData.getDataProvider();
        SymbolIdentity symbol = chartData.getSymbol();
        TimeFrame current = chartData.getTimeFrame();

        List<TimeFrame> frames = new ArrayList<>();
        if (provider != null && symbol != null)
            frames = provider.getAvailableTimeFrames(symbol);
        if (frames == null)
            frames = List.of();

        frames = normalize(frames, current);

        updating = true;
        DefaultComboBoxModel<TimeFrame> model = (DefaultComboBoxModel<TimeFrame>) getModel();
        model.removeAllElements();
        for (TimeFrame frame : frames)
            model.addElement(frame);
        if (current != null)
            setSelectedItem(current);

        setEnabled(model.getSize() > 0);
        updating = false;

        Dimension preferred = getPreferredSize();
        int width = Math.max(preferred.width, 90);
        Dimension adjusted = new Dimension(width, preferred.height);
        setPreferredSize(adjusted);
        setMaximumSize(adjusted);
    }

    private static List<TimeFrame> normalize(List<TimeFrame> frames, TimeFrame current) {
        Set<TimeFrame> unique = new LinkedHashSet<>();
        if (frames != null)
            unique.addAll(frames);
        if (current != null)
            unique.add(current);

        return unique.stream()
                .sorted(Comparator.comparingLong(TimeFrameSelector::sortKey))
                .toList();
    }

    private static long sortKey(TimeFrame frame) {
        OptionalInt seconds = TimeFrameHelper.toSeconds(frame);
        if (seconds.isPresent())
            return seconds.getAsInt();

        OptionalInt months = TimeFrameHelper.toMonths(frame);
        return months.isPresent()? months.getAsInt() * MONTH_TO_SECONDS : Long.MAX_VALUE;
    }

    private static class TimeFrameRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof TimeFrame tf)
                setText(TimeFrameHelper.getName(tf));

            return comp;
        }
    }
}
