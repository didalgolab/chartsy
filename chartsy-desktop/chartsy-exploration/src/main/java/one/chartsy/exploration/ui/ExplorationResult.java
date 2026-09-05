/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration.ui;

import one.chartsy.kernel.ExplorationFragment;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ExplorationResult extends AbstractTableModel {

    @Serial
    private static final long serialVersionUID = 3757051960318859399L;

    private final List<String> columnNames = Collections.synchronizedList(new ArrayList<>());

    private final List<ExplorationFragment> rows = Collections.synchronizedList(new ArrayList<>());


    public void addExplorationFragment(ExplorationFragment fragment) {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(() -> addExplorationFragment(fragment));
            return;
        }
        rows.add(fragment);

        boolean newColumn = false;
        for (String columnName : fragment.columnValues().keySet())
            if (!columnNames.contains(columnName))
                newColumn |= columnNames.add(columnName);

        if (newColumn) {
            notifyTimer.stop();
            nextRowToNotify = rows.size();
            fireTableStructureChanged();
        } else if (!notifyTimer.isRunning()) {
            notifyTimer.start();
        }
    }

    public Stream<ExplorationFragment> rows() {
        return rows.stream();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Comparable.class;
    }

    private int nextRowToNotify;
    private final Timer notifyTimer = new Timer(100, e -> flushPendingRows());
    { notifyTimer.setRepeats(false); }

    void flushPendingRows() {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(this::flushPendingRows);
            return;
        }
        notifyTimer.stop();
        int firstRow = nextRowToNotify;
        nextRowToNotify = rows.size();
        if (firstRow < nextRowToNotify)
            fireTableRowsInserted(firstRow, nextRowToNotify - 1);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    @Override
    public String getColumnName(int column) {
        return columnNames.get(column);
    }

    @Override
    public int findColumn(String columnName) {
        return columnNames.indexOf(columnName);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        try {
            return getRowAt(rowIndex).columnValues().get(getColumnName(columnIndex));
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    public ExplorationFragment getRowAt(int rowIndex) {
        return rows.get(rowIndex);
    }
}
