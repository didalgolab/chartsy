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
import org.openide.util.Lookup;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.Serial;
import java.util.Map;

public class ExplorationResultTable extends ETable implements ExplorationListener {

    private final ExplorationResult result;

    public ExplorationResultTable() {
        this(new ExplorationResult());
    }

    protected ExplorationResultTable(ExplorationResult result) {
        super(result);
        this.result = result;
        setFullyNonEditable(true);
        setColumnHidingAllowed(true);
        setBorder(null);
        setShowGrid(true);
        setGridColor(new Color(230, 230, 230));
        installEventListeners();
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
        FontMetrics fm = getFontMetrics(getFont());
        setRowHeight(fm.getHeight() + 1);
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
        return cellRenderer;
    }

    @Override
    public void explorationFragmentCreated(ExplorationFragment next) {
        result.addExplorationFragment(next);
    }

    @Override
    public void explorationFinished() {

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
                                .open(row.symbol(), TimeFrame.Period.DAILY, null);
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
