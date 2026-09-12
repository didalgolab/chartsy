/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration.ui;

import one.chartsy.TimeFrame;
import one.chartsy.kernel.ExplorationFragment;
import one.chartsy.kernel.ExplorationListener;
import one.chartsy.misc.StyleOption;
import one.chartsy.misc.StyledValue;
import one.chartsy.ui.ChartManager;
import org.netbeans.swing.etable.ETable;
import org.netbeans.swing.etable.ETableColumn;
import org.openide.util.Lookup;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.Serial;
import java.util.Map;

public class ExplorationResultTable extends ETable implements ExplorationListener {

    public static final String STATUS_PROPERTY = "explorationStatus";
    private final ExplorationResult result;
    private String explorationStatus = "Preparing exploration…";
    private boolean failed;
    private final PriceThumbnailPreview thumbnailPreview;
    private boolean previewSpaceDown;

    public ExplorationResultTable() {
        this(new ExplorationResult());
    }

    protected ExplorationResultTable(ExplorationResult result) {
        this(result, PopupFactory.getSharedInstance());
    }

    ExplorationResultTable(ExplorationResult result, PopupFactory popupFactory) {
        super(result);
        this.result = result;
        thumbnailPreview = new PriceThumbnailPreview(this, popupFactory);
        setFullyNonEditable(true);
        setColumnHidingAllowed(true);
        setBorder(null);
        setShowGrid(true);
        setGridColor(new Color(230, 230, 230));
        installEventListeners();
        getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override public void columnAdded(TableColumnModelEvent event) { updateRowHeight(); }
            @Override public void columnRemoved(TableColumnModelEvent event) { thumbnailPreview.dismiss(); updateRowHeight(); }
            @Override public void columnMoved(TableColumnModelEvent event) {
                if (event.getFromIndex() != event.getToIndex()) thumbnailPreview.dismiss();
            }
            @Override public void columnMarginChanged(ChangeEvent event) { thumbnailPreview.dismiss(); }
            @Override public void columnSelectionChanged(ListSelectionEvent event) { }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        thumbnailPreview.attach();
    }

    @Override
    public void removeNotify() {
        thumbnailPreview.detach();
        super.removeNotify();
    }

    @Override
    protected void processKeyEvent(KeyEvent event) {
        // ETable's quick-search KeyListener consumes printable keys before Swing actions run.
        // Reserve the whole plain-Space sequence only when this row can show a price preview.
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_SPACE
                && event.getModifiersEx() == 0
                && (previewSpaceDown || thumbnailPreview != null && thumbnailPreview.toggleSelected())) {
            previewSpaceDown = true;
            event.consume();
            return;
        }
        if (previewSpaceDown) {
            if (event.getID() == KeyEvent.KEY_TYPED && event.getKeyChar() == ' ') {
                event.consume();
                return;
            }
            if (event.getID() == KeyEvent.KEY_RELEASED && event.getKeyCode() == KeyEvent.VK_SPACE) {
                previewSpaceDown = false;
                event.consume();
                return;
            }
        }
        super.processKeyEvent(event);
    }

    @Override
    protected void processFocusEvent(FocusEvent event) {
        if (event.getID() == FocusEvent.FOCUS_LOST)
            previewSpaceDown = false;
        super.processFocusEvent(event);
    }

    @Override
    public final ExplorationResult getModel() {
        return (ExplorationResult) super.getModel();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        // adjust data grid's font to make it slightly bigger
        Font font = getFont();
        Map<TextAttribute, Object> attributes = Map.of(
                TextAttribute.TRACKING, -0.05,
                TextAttribute.SIZE, 2.0 + font.getSize2D()
        );
        setFont(font.deriveFont(attributes));
        updateRowHeight();
    }

    private void updateRowHeight() {
        int height = getFontMetrics(getFont()).getHeight() + 1;
        for (int i = 0; i < getColumnModel().getColumnCount(); i++)
            if (getColumnModel().getColumn(i).getCellRenderer() instanceof PriceThumbnailRenderer)
                height = Math.max(height, PriceThumbnailRenderer.HEIGHT);
        if (getRowHeight() != height)
            setRowHeight(height);
    }

    @Override
    protected TableColumn createColumn(int modelIndex) {
        var column = (ETableColumn) super.createColumn(modelIndex);
        for (int row = 0; row < getModel().getRowCount(); row++) {
            if (PriceThumbnailRenderer.thumbnail(getModel().getValueAt(row, modelIndex)) != null) {
                column.setCellRenderer(new PriceThumbnailRenderer());
                column.setMinWidth(100);
                column.setPreferredWidth(PriceThumbnailRenderer.WIDTH);
                column.setNestedComparator((left, right) -> {
                    var a = PriceThumbnailRenderer.thumbnail(left);
                    var b = PriceThumbnailRenderer.thumbnail(right);
                    return Double.compare(a == null ? Double.NaN : a.changePercent(),
                            b == null ? Double.NaN : b.changePercent());
                });
                break;
            }
        }
        return column;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return thumbnailTargetAt(event.getPoint()) == null ? super.getToolTipText(event) : null;
    }

    PriceThumbnailPreview.Target thumbnailTargetAt(Point point) {
        int row = rowAtPoint(point);
        int column = columnAtPoint(point);
        return thumbnailTarget(row, column);
    }

    PriceThumbnailPreview.Target selectedThumbnailTarget() {
        int row = getSelectedRow();
        for (int column = 0; column < getColumnCount(); column++) {
            var target = thumbnailTarget(row, column);
            if (target != null)
                return target;
        }
        return null;
    }

    private PriceThumbnailPreview.Target thumbnailTarget(int row, int column) {
        if (row < 0 || column < 0)
            return null;
        var prices = PriceThumbnailRenderer.thumbnail(getValueAt(row, column));
        if (prices == null)
            return null;
        int modelRow = convertRowIndexToModel(row);
        return new PriceThumbnailPreview.Target(prices, getModel().getRowAt(modelRow).symbol().name(),
                modelRow, convertColumnIndexToModel(column));
    }

    PriceThumbnailPreview thumbnailPreview() {
        return thumbnailPreview;
    }

    /** The custom cell renderer associated with the table component. */
    private final TableCellRenderer cellRenderer = new TableCellRenderer() {

        class CellBorder extends EmptyBorder {
            @Serial
            private static final long serialVersionUID = 1460784391992040907L;
            /** The current border color or {@code null} if none. */
            Color color;
            Border focus;

            CellBorder(int top, int left, int bottom, int right)   {
                super(top, left, bottom, right);
            }

            @Override
            public void paintBorder(Component c, java.awt.Graphics g, int x, int y, int width, int height) {
                if (color != null) {
                    g.setColor(color);
                    g.drawRect(0, 0, width - 1, height - 1);
                    g.drawRect(1, 1, width - 3, height - 3);
                }
                if (focus != null)
                    focus.paintBorder(c, g, x, y, width, height);
            }
        }
        private final CellBorder cellBorder = new CellBorder(2, 3, 2, 3);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            TableCellRenderer originCellRenderer = getOriginCellRenderer(row, column);
            Component renderer = originCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof StyledValue cell) {
                if (!isSelected) {
                    renderer.setBackground(cell.getStyle(StyleOption.BACKGROUND).orElse(null));
                    renderer.setForeground(cell.getStyle(StyleOption.FOREGROUND).orElse(null));
                }
                if (renderer instanceof JLabel labelRenderer) {
                    cellBorder.focus = hasFocus? labelRenderer.getBorder(): null;
                    labelRenderer.setHorizontalAlignment((cell.numberValue() != null)? SwingConstants.RIGHT : SwingConstants.LEFT);
                    labelRenderer.setBorder(cellBorder);
                } else if (renderer instanceof JComponent compRenderer) {
                    cellBorder.focus = hasFocus? compRenderer.getBorder(): null;
                    compRenderer.setBorder(cellBorder);
                }
                cellBorder.color = isSelected? cell.getStyle(StyleOption.BACKGROUND).orElse(null): null;
            } else if (!isSelected) {
                renderer.setBackground(null);
                renderer.setForeground(null);
            }
            return renderer;
        }
    };

    public final TableCellRenderer getOriginCellRenderer(int row, int column) {
        return super.getCellRenderer(row, column);
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        var renderer = getOriginCellRenderer(row, column);
        return renderer instanceof PriceThumbnailRenderer ? renderer : cellRenderer;
    }

    @Override
    public void explorationFragmentCreated(ExplorationFragment next) {
        result.addExplorationFragment(next);
    }

    @Override
    public void explorationFinished() {
        onEventThread(() -> {
            result.flushPendingRows();
            if (!failed)
                setExplorationStatus("Completed — " + result.getRowCount() + " results");
        });
    }

    @Override
    public void explorationResultsReset() {
        onEventThread(() -> {
            thumbnailPreview.dismiss();
            clearSelection();
            result.clearRows();
        });
    }

    @Override
    public void explorationStatusChanged(String status) {
        onEventThread(() -> setExplorationStatus(status));
    }

    @Override
    public void explorationFailed(Throwable failure) {
        onEventThread(() -> {
            failed = true;
            result.flushPendingRows();
            String message = failure.getMessage();
            setExplorationStatus("Incomplete — " + (message == null ? "exploration failed" : message));
        });
    }

    public String getExplorationStatus() {
        return explorationStatus;
    }

    private void setExplorationStatus(String status) {
        String old = explorationStatus;
        explorationStatus = status;
        firePropertyChange(STATUS_PROPERTY, old, status);
    }

    private static void onEventThread(Runnable action) {
        if (EventQueue.isDispatchThread())
            action.run();
        else
            EventQueue.invokeLater(action);
    }

    protected void installEventListeners() {
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int rowIndex = rowAtPoint(e.getPoint());
                    if (rowIndex >= 0) {
                        rowIndex = convertRowIndexToModel(rowIndex);

                        ExplorationFragment row = result.getRowAt(rowIndex);
                        Lookup.getDefault().lookup(ChartManager.class)
                                .open(row.symbol(), TimeFrame.Period.DAILY);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu popupMenu = getComponentPopupMenu();
                    if (popupMenu != null)
                        popupMenu.show(ExplorationResultTable.this, e.getX(), e.getY());
                }
            }
        });
    }
}
