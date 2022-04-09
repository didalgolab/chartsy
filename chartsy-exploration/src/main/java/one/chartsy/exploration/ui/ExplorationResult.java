package one.chartsy.exploration.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ExplorationResult extends AbstractTableModel {

    @Serial
    private static final long serialVersionUID = 3757051960318859399L;

    private final List<String> columnNames = new ArrayList<>();

    private final List<ExplorationFragment> rows = new ArrayList<>();


    public void addExplorationFragment(ExplorationFragment fragment) {
        rows.add(fragment);

        boolean newColumn = columnNames.addAll(fragment.columnValues().keySet());
        if (newColumn)
            EventQueue.invokeLater(this::fireTableStructureChanged);
        else if (!notifyTimer.isRunning() || nextRowToNotify == rows.size())
            notifyTimer.restart();
    }

    public Stream<ExplorationFragment> rows() {
        return rows.stream();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Comparable.class;
    }

    volatile int nextRowToNotify;
    Timer notifyTimer = new Timer(100, e -> {
        int lastRow = getRowCount() - 1;
        int firstRow = nextRowToNotify;
        nextRowToNotify = lastRow;

        fireTableRowsInserted(firstRow, lastRow);
    });
    { notifyTimer.setRepeats(false); }

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
