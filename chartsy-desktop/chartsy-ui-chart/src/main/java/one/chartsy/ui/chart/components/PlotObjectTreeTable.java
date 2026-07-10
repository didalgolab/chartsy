/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.internal.ChartPluginParameter;
import one.chartsy.ui.chart.internal.ChartPlotRouting;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.netbeans.swing.outline.RenderDataProvider;
import org.netbeans.swing.outline.RowModel;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Tree-table view of selected studies and their visual plot objects.
 */
final class PlotObjectTreeTable extends Outline {
    private static final String VISIBILITY_PROPERTY = "plotObjectVisibility";
    private static final String PANEL_PROPERTY = "plotObjectPanel";
    private static final int TREE_COLUMN = 0;
    private static final int TREE_COLUMN_WIDTH = 330;
    private static final int ROW_HEIGHT = 22;

    private final PlotObjectTreeModel treeModel = new PlotObjectTreeModel();
    private List<PanelChoice> panelChoices = List.of(PanelChoice.mainChart(), PanelChoice.newPane(1));

    PlotObjectTreeTable() {
        setName("indicatorChooser.plotTable");
        setModel(DefaultOutlineModel.createOutlineModel(treeModel, treeModel, false, "Plot Object"));
        setRenderDataProvider(new PlotObjectRenderDataProvider());
        setRootVisible(false);
        setRowHeight(ROW_HEIGHT);
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        setColumnHidingAllowed(false);
        setShowVerticalLines(false);
        setGridColor(resolveTableGridColor());
        getTableHeader().setReorderingAllowed(false);
        configureColumns();
    }

    void setStudies(List<Study> studies) {
        treeModel.setStudies(studies);
        expandAllStudies();
    }

    void refreshStudies(List<Study> studies) {
        Set<ChartPlugin<?>> expandedPlugins = expandedPlugins();
        treeModel.setStudies(studies);
        restoreExpansion(expandedPlugins);
    }

    void setPanelChoices(List<PanelChoice> panelChoices) {
        this.panelChoices = List.copyOf(panelChoices);
        getColumnModel().getColumn(Column.PANEL.tableIndex()).setCellEditor(createPanelEditor());
        repaint();
    }

    int getPlotObjectCount() {
        return treeModel.getPlotObjectCount();
    }

    void addVisibilityChangeListener(PropertyChangeListener listener) {
        addPropertyChangeListener(VISIBILITY_PROPERTY, listener);
    }

    void addPanelChangeListener(PropertyChangeListener listener) {
        addPropertyChangeListener(PANEL_PROPERTY, listener);
    }

    ChartPlugin<?> getPluginAt(int viewRow) {
        PlotObjectTreeNode node = getNodeAt(viewRow);
        return node == null ? null : node.plugin();
    }

    void selectPlugin(ChartPlugin<?> plugin) {
        if (plugin == null) {
            clearSelection();
            return;
        }

        PlotObjectTreeNode node = treeModel.findStudyNode(plugin);
        if (node == null)
            return;

        TreePath path = new TreePath(node.getPath());
        int modelRow = getLayoutCache().getRowForPath(path);
        int viewRow = modelRow < 0 ? -1 : convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            getSelectionModel().setSelectionInterval(viewRow, viewRow);
            Rectangle rowBounds = getCellRect(viewRow, TREE_COLUMN, true);
            scrollRectToVisible(rowBounds);
        }
    }

    private void configureColumns() {
        getColumnModel().getColumn(TREE_COLUMN).setPreferredWidth(TREE_COLUMN_WIDTH);
        PlotObjectCellRenderer textRenderer = new PlotObjectCellRenderer();
        for (Column column : Column.values()) {
            TableColumn tableColumn = getColumnModel().getColumn(column.tableIndex());
            tableColumn.setPreferredWidth(column.preferredWidth());
            if (column != Column.VISIBLE && column != Column.COLOR)
                tableColumn.setCellRenderer(textRenderer);
        }

        TableColumn visibleColumn = getColumnModel().getColumn(Column.VISIBLE.tableIndex());
        visibleColumn.setCellRenderer(new VisibilityCellRenderer());
        visibleColumn.setCellEditor(createVisibilityEditor());

        getColumnModel().getColumn(Column.PANEL.tableIndex()).setCellEditor(createPanelEditor());

        TableColumn colorColumn = getColumnModel().getColumn(Column.COLOR.tableIndex());
        colorColumn.setMinWidth(68);
        colorColumn.setMaxWidth(84);
        colorColumn.setCellRenderer(new ColorSwatchRenderer());
    }

    private static DefaultCellEditor createVisibilityEditor() {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        checkBox.setOpaque(true);
        return new DefaultCellEditor(checkBox);
    }

    private DefaultCellEditor createPanelEditor() {
        JComboBox<PanelChoice> choices = new JComboBox<>(panelChoices.toArray(PanelChoice[]::new));
        return new DefaultCellEditor(choices);
    }

    private void expandAllStudies() {
        for (PlotObjectTreeNode node : treeModel.studyNodes())
            expandPath(new TreePath(node.getPath()));
    }

    private Set<ChartPlugin<?>> expandedPlugins() {
        Set<ChartPlugin<?>> expandedPlugins = new LinkedHashSet<>();
        for (PlotObjectTreeNode node : treeModel.studyNodes()) {
            if (isExpanded(new TreePath(node.getPath())))
                expandedPlugins.add(node.plugin());
        }
        return expandedPlugins;
    }

    private void restoreExpansion(Set<ChartPlugin<?>> expandedPlugins) {
        for (PlotObjectTreeNode node : treeModel.studyNodes()) {
            if (expandedPlugins.contains(node.plugin()))
                expandPath(new TreePath(node.getPath()));
        }
    }

    private PlotObjectTreeNode getNodeAt(int viewRow) {
        int modelRow = convertRowIndexToModel(viewRow);
        Object value = getModel().getValueAt(modelRow, TREE_COLUMN);
        return value instanceof PlotObjectTreeNode node ? node : null;
    }

    record Study(ChartPlugin<?> plugin, String type, List<Plot> plots) {
        Study {
            plots = List.copyOf(plots);
        }

        String label() {
            return plugin.getLabel();
        }
    }

    record Plot(
            String label,
            Stroke stroke,
            Color color,
            VisibilityBinding visibility,
            PanelBinding panel) {
        static Plot withBindings(
                String label,
                Stroke stroke,
                Color color,
                ChartPluginParameter visibility,
                ChartPluginParameter panel,
                IntSupplier inheritedPanelId) {
            return new Plot(
                    label,
                    stroke,
                    color,
                    VisibilityBinding.of(visibility),
                    PanelBinding.of(panel, inheritedPanelId));
        }
    }

    record PanelChoice(int panelId, String label, boolean createsPane) {
        static PanelChoice mainChart() {
            return new PanelChoice(ChartPlotRouting.MAIN_PANEL_ID, "Main chart", false);
        }

        static PanelChoice pane(int panelId, int paneNumber) {
            return new PanelChoice(panelId, "Pane " + paneNumber, false);
        }

        static PanelChoice newPane(int panelId) {
            return new PanelChoice(panelId, "New Pane", true);
        }

        static PanelChoice mixed() {
            return new PanelChoice(-1, "Mixed", false);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record VisibilityBinding(Boolean initialValue, ChartPluginParameter parameter) {
        private static final VisibilityBinding NONE = new VisibilityBinding(null, null);

        static VisibilityBinding none() {
            return NONE;
        }

        static VisibilityBinding of(ChartPluginParameter parameter) {
            return parameter == null ? none() : new VisibilityBinding(read(parameter), parameter);
        }

        Boolean value() {
            Boolean currentValue = read(parameter);
            return currentValue != null ? currentValue : initialValue;
        }

        boolean isEditable() {
            return parameter != null && parameter.canWrite();
        }

        void setValue(Boolean value) {
            if (isEditable())
                parameter.setValue(value);
        }

        private static Boolean read(ChartPluginParameter parameter) {
            if (parameter != null && parameter.canRead() && parameter.getValue() instanceof Boolean value)
                return value;
            return null;
        }
    }

    private record PanelBinding(Integer initialValue, ChartPluginParameter parameter, IntSupplier inheritedPanelId) {
        static PanelBinding of(ChartPluginParameter parameter, IntSupplier inheritedPanelId) {
            return new PanelBinding(read(parameter), parameter, inheritedPanelId);
        }

        int value() {
            Integer value = read(parameter);
            if (value == null)
                value = initialValue;
            return value != null && value >= 0 ? value : inheritedPanelId.getAsInt();
        }

        boolean isEditable() {
            return parameter != null && parameter.canWrite();
        }

        void setValue(int value) {
            if (isEditable())
                parameter.setValue(value);
        }

        private static Integer read(ChartPluginParameter parameter) {
            if (parameter != null && parameter.canRead() && parameter.getValue() instanceof Number value)
                return value.intValue();
            return null;
        }
    }

    private enum Column {
        TYPE("Type", Object.class, 90),
        PANEL("Panel", Object.class, 120),
        VISIBLE("Visible", Boolean.class, 70),
        STYLE("Style", Object.class, 140),
        WIDTH("Width", Object.class, 60),
        COLOR("Color", Color.class, 76);

        private static final Column[] VALUES = values();

        private final String label;
        private final Class<?> valueType;
        private final int preferredWidth;

        Column(String label, Class<?> valueType, int preferredWidth) {
            this.label = label;
            this.valueType = valueType;
            this.preferredWidth = preferredWidth;
        }

        static Column at(int modelIndex) {
            return VALUES[modelIndex];
        }

        int tableIndex() {
            return ordinal() + 1;
        }

        String label() {
            return label;
        }

        Class<?> valueType() {
            return valueType;
        }

        int preferredWidth() {
            return preferredWidth;
        }
    }

    private final class PlotObjectTreeModel extends DefaultTreeModel implements RowModel {
        private int plotObjectCount;

        private PlotObjectTreeModel() {
            super(PlotObjectTreeNode.root());
        }

        void setStudies(List<Study> studies) {
            PlotObjectTreeNode root = PlotObjectTreeNode.root();
            plotObjectCount = 0;
            for (Study study : studies) {
                PlotObjectTreeNode studyNode = PlotObjectTreeNode.study(study);
                for (Plot plot : study.plots()) {
                    studyNode.add(PlotObjectTreeNode.plot(study, plot));
                    plotObjectCount++;
                }
                root.add(studyNode);
            }
            setRoot(root);
        }

        int getPlotObjectCount() {
            return plotObjectCount;
        }

        List<PlotObjectTreeNode> studyNodes() {
            PlotObjectTreeNode root = (PlotObjectTreeNode) getRoot();
            List<PlotObjectTreeNode> nodes = new ArrayList<>(root.getChildCount());
            for (int i = 0; i < root.getChildCount(); i++)
                nodes.add((PlotObjectTreeNode) root.getChildAt(i));
            return nodes;
        }

        PlotObjectTreeNode findStudyNode(ChartPlugin<?> plugin) {
            for (PlotObjectTreeNode node : studyNodes()) {
                if (node.plugin() == plugin)
                    return node;
            }
            return null;
        }

        @Override
        public int getColumnCount() {
            return Column.VALUES.length;
        }

        @Override
        public String getColumnName(int column) {
            return Column.at(column).label();
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return Column.at(column).valueType();
        }

        @Override
        public Object getValueFor(Object value, int columnIndex) {
            if (!(value instanceof PlotObjectTreeNode node) || node.study() == null)
                return null;

            Column column = Column.at(columnIndex);
            Plot plot = node.plot();
            if (plot == null) {
                return switch (column) {
                    case TYPE -> node.study().type();
                    case PANEL -> studyPanelChoice(node.study());
                    default -> null;
                };
            }

            return switch (column) {
                case TYPE -> "";
                case PANEL -> panelChoice(plot.panel().value());
                case VISIBLE -> plot.visibility().value();
                case STYLE -> describeStroke(plot.stroke());
                case WIDTH -> describeWidth(plot.stroke());
                case COLOR -> plot.color();
            };
        }

        @Override
        public boolean isCellEditable(Object value, int columnIndex) {
            if (!(value instanceof PlotObjectTreeNode node))
                return false;
            return switch (Column.at(columnIndex)) {
                case VISIBLE -> node.plot() != null && node.plot().visibility().isEditable();
                case PANEL -> node.plot() != null
                        ? node.plot().panel().isEditable()
                        : node.study() != null && node.study().plots().stream().anyMatch(plot -> plot.panel().isEditable());
                default -> false;
            };
        }

        @Override
        public void setValueFor(Object value, int columnIndex, Object newValue) {
            if (!(value instanceof PlotObjectTreeNode node))
                return;

            if (Column.at(columnIndex) == Column.PANEL && newValue instanceof PanelChoice panel) {
                PanelChoice oldValue = node.plot() != null
                        ? panelChoice(node.plot().panel().value())
                        : studyPanelChoice(node.study());
                if (node.plot() != null)
                    node.plot().panel().setValue(panel.panelId());
                else if (node.study() != null)
                    node.study().plots().forEach(plot -> plot.panel().setValue(panel.panelId()));
                repaint();
                firePropertyChange(PANEL_PROPERTY, oldValue, panel);
                return;
            }

            if (Column.at(columnIndex) == Column.VISIBLE
                    && node.plot() != null
                    && newValue instanceof Boolean visible) {
                VisibilityBinding visibility = node.plot().visibility();
                Boolean oldValue = visibility.value();
                visibility.setValue(visible);
                repaint();
                firePropertyChange(VISIBILITY_PROPERTY, oldValue, visibility.value());
            }
        }

        private PanelChoice studyPanelChoice(Study study) {
            if (study == null || study.plots().isEmpty())
                return null;
            int panelId = study.plots().getFirst().panel().value();
            for (Plot plot : study.plots()) {
                if (plot.panel().value() != panelId)
                    return PanelChoice.mixed();
            }
            return panelChoice(panelId);
        }

        private PanelChoice panelChoice(int panelId) {
            return panelChoices.stream()
                    .filter(choice -> !choice.createsPane() && choice.panelId() == panelId)
                    .findFirst()
                    .orElseGet(() -> new PanelChoice(panelId, "Pane " + panelId, false));
        }
    }

    private static final class PlotObjectTreeNode extends DefaultMutableTreeNode {
        private final Study study;
        private final Plot plot;

        private PlotObjectTreeNode(Study study, Plot plot, boolean allowsChildren) {
            super(null, allowsChildren);
            this.study = study;
            this.plot = plot;
        }

        static PlotObjectTreeNode root() {
            return new PlotObjectTreeNode(null, null, true);
        }

        static PlotObjectTreeNode study(Study study) {
            return new PlotObjectTreeNode(study, null, true);
        }

        static PlotObjectTreeNode plot(Study study, Plot plot) {
            return new PlotObjectTreeNode(study, plot, false);
        }

        Study study() {
            return study;
        }

        Plot plot() {
            return plot;
        }

        ChartPlugin<?> plugin() {
            return study == null ? null : study.plugin();
        }

        boolean isStudy() {
            return study != null && plot == null;
        }

        String label() {
            if (plot != null)
                return plot.label();
            return study == null ? "" : study.label();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    private final class PlotObjectRenderDataProvider implements RenderDataProvider {
        private static final Icon EMPTY_ICON = new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 0;
            }

            @Override
            public int getIconHeight() {
                return 0;
            }
        };

        @Override
        public String getDisplayName(Object value) {
            if (!(value instanceof PlotObjectTreeNode node))
                return "";
            return node.isStudy() ? "<html><b>" + escapeHtml(node.label()) + "</b></html>" : node.label();
        }

        @Override
        public boolean isHtmlDisplayName(Object value) {
            return value instanceof PlotObjectTreeNode node && node.isStudy();
        }

        @Override
        public Color getBackground(Object value) {
            return value instanceof PlotObjectTreeNode node && node.isStudy()
                    ? resolveGroupBackground(PlotObjectTreeTable.this)
                    : null;
        }

        @Override
        public Color getForeground(Object value) {
            return null;
        }

        @Override
        public String getTooltipText(Object value) {
            if (!(value instanceof PlotObjectTreeNode node) || node.study() == null)
                return null;
            return node.isStudy()
                    ? node.study().type() + " - " + treeModel.studyPanelChoice(node.study())
                    : node.label();
        }

        @Override
        public Icon getIcon(Object value) {
            return EMPTY_ICON;
        }
    }

    private static final class PlotObjectCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(column == Column.WIDTH.tableIndex() ? SwingConstants.CENTER : SwingConstants.LEFT);
            if (isStudyRow(table, row)) {
                setFont(getFont().deriveFont(getFont().getStyle() | Font.BOLD));
                if (!isSelected)
                    setBackground(resolveGroupBackground(table));
            } else {
                setFont(table.getFont());
                if (!isSelected)
                    setBackground(table.getBackground());
            }
            return this;
        }
    }

    private static final class VisibilityCellRenderer implements TableCellRenderer {
        private final JCheckBox checkBox = new JCheckBox();
        private final JPanel emptyCell = new JPanel();

        private VisibilityCellRenderer() {
            checkBox.setOpaque(true);
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
            checkBox.setBorderPainted(false);
            checkBox.setFocusPainted(false);
            emptyCell.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Color background = cellBackground(table, row, isSelected);
            if (!(value instanceof Boolean)) {
                emptyCell.setBackground(background);
                return emptyCell;
            }
            checkBox.setBackground(background);
            checkBox.setSelected(Boolean.TRUE.equals(value));
            checkBox.setEnabled(table.isCellEditable(row, column));
            checkBox.setToolTipText("Toggle plot visibility");
            return checkBox;
        }
    }

    private static final class ColorSwatchRenderer extends JPanel implements TableCellRenderer {
        private Color color;
        private boolean selected;

        private ColorSwatchRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            color = value instanceof Color swatch ? swatch : null;
            selected = isSelected;
            setBackground(cellBackground(table, row, isSelected));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (color == null)
                return;

            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(24, getWidth() - 18);
                int height = 12;
                int x = 8;
                int y = (getHeight() - height) / 2;
                graphics2D.setColor(color);
                graphics2D.fillRoundRect(x, y, width, height, 8, 8);
                graphics2D.setColor(selected ? getForeground() : new Color(0x7A7A7A));
                graphics2D.drawRoundRect(x, y, width, height, 8, 8);
            } finally {
                graphics2D.dispose();
            }
        }
    }

    private static boolean isStudyRow(JTable table, int viewRow) {
        return table.getValueAt(viewRow, TREE_COLUMN) instanceof PlotObjectTreeNode node && node.isStudy();
    }

    private static Color cellBackground(JTable table, int viewRow, boolean selected) {
        if (selected)
            return table.getSelectionBackground();
        return isStudyRow(table, viewRow) ? resolveGroupBackground(table) : table.getBackground();
    }

    private static Color resolveGroupBackground(JTable table) {
        Color background = table.getBackground();
        Color accent = table.getSelectionBackground();
        if (background == null)
            background = Color.WHITE;
        if (accent == null)
            accent = new Color(0x5B8FD1);
        return blend(background, accent, 0.08f);
    }

    private static Color resolveTableGridColor() {
        Color color = UIManager.getColor("Table.gridColor");
        return color != null ? color : new Color(0xE2E5E9);
    }

    private static Color blend(Color background, Color foreground, float foregroundWeight) {
        float backgroundWeight = 1.0f - foregroundWeight;
        return new Color(
                Math.round(background.getRed() * backgroundWeight + foreground.getRed() * foregroundWeight),
                Math.round(background.getGreen() * backgroundWeight + foreground.getGreen() * foregroundWeight),
                Math.round(background.getBlue() * backgroundWeight + foreground.getBlue() * foregroundWeight)
        );
    }

    private static String describeStroke(Stroke stroke) {
        return BasicStrokes.getStrokeName(stroke)
                .map(name -> name.replace('_', ' ').toLowerCase(Locale.ROOT))
                .map(PlotObjectTreeTable::capitalizeWords)
                .orElse("");
    }

    private static String describeWidth(Stroke stroke) {
        if (stroke instanceof BasicStroke basicStroke) {
            float width = basicStroke.getLineWidth();
            if (Math.abs(width - Math.round(width)) < 0.001f)
                return Integer.toString(Math.round(width));
            return new DecimalFormat("0.#").format(width);
        }
        return "";
    }

    private static String capitalizeWords(String text) {
        String[] parts = text.split(" ");
        StringBuilder builder = new StringBuilder(text.length());
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (!builder.isEmpty())
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
