package one.chartsy.charting.internal;

import java.util.Comparator;

import one.chartsy.charting.ChartDrawable;
import one.chartsy.charting.Grid;

/// Orders [ChartDrawable] instances for storage inside [one.chartsy.charting.Chart]'s drawable
/// list.
///
/// The primary key is [ChartDrawable#getDrawOrder()] in ascending order. When multiple drawables
/// share that value, the comparator places non-[Grid] drawables before grids and then breaks the
/// remaining ties by runtime class and, for grids, by attached axis index. Those extra tie
/// breakers give the chart a stable insertion position for binary-search based list maintenance.
public final class ChartDrawableComparator implements Comparator<ChartDrawable> {
    private static final ChartDrawableComparator INSTANCE = new ChartDrawableComparator();

    /// Returns the shared comparator instance used by chart drawable insertion.
    public static ChartDrawableComparator getInstance() {
        return INSTANCE;
    }

    private ChartDrawableComparator() {
    }

    /// {@inheritDoc}
    ///
    /// Drawables with the same declared draw order are further ordered by grid status, runtime
    /// class, and grid axis slot.
    @Override
    public int compare(ChartDrawable left, ChartDrawable right) {
        int byDrawOrder = Integer.compare(left.getDrawOrder(), right.getDrawOrder());
        if (byDrawOrder != 0)
            return byDrawOrder;

        int byGridStatus = Integer.compare(gridSortKey(left), gridSortKey(right));
        if (byGridStatus != 0)
            return byGridStatus;

        int byClass = Integer.compare(left.getClass().hashCode(), right.getClass().hashCode());
        if (byClass != 0)
            return byClass;

        if (left instanceof Grid leftGrid && right instanceof Grid rightGrid)
            return Integer.compare(Grid.getAxisIndex(leftGrid), Grid.getAxisIndex(rightGrid));
        return 0;
    }

    private static int gridSortKey(ChartDrawable drawable) {
        return drawable instanceof Grid ? 1 : 0;
    }
}
