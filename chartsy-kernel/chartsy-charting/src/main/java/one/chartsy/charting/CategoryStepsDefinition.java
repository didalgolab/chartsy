package one.chartsy.charting;

import java.text.DecimalFormatSymbols;

import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.data.DefaultDataSet;

/// Steps definition for categorical axes backed by dataset labels or numeric category indices.
///
/// Category axes can place their major step positions and their labels either on category centers
/// or on the boundaries between categories. The two choices are independent:
/// - [#isStepBetweenCategory()] controls where the major step sequence falls
/// - [#isLabelBetweenCategory()] controls whether labels are read from the category center just to
///   the right of the reported step value
///
/// Labels are resolved from one of three sources, in this order:
/// 1. an explicitly supplied label dataset
/// 2. a dataset selected by [#getLabelDataSetIndex()] from the chart's current [DataSource]
/// 3. locale-aware numeric formatting of the category coordinate when no label is available
public class CategoryStepsDefinition extends StepsDefinition {
    private final DataSet labelDataSet;
    private int labelDataSetIndex;
    private boolean stepBetweenCategory;
    private boolean labelBetweenCategory;

    /// Creates a category steps definition that reads labels from dataset index `0`.
    ///
    /// @param stepBetweenCategory whether major steps should fall between adjacent categories
    public CategoryStepsDefinition(boolean stepBetweenCategory) {
        this(stepBetweenCategory, (DataSet) null);
    }

    /// Creates a category steps definition backed by one explicit label dataset.
    ///
    /// When `labelDataSet` is `null`, label lookup falls back to dataset index `0` from the
    /// owning chart's current [DataSource].
    ///
    /// @param stepBetweenCategory whether major steps should fall between adjacent categories
    /// @param labelDataSet        the dataset used for label lookup, or `null` to defer to the chart
    public CategoryStepsDefinition(boolean stepBetweenCategory, DataSet labelDataSet) {
        this.labelBetweenCategory = false;
        this.labelDataSet = labelDataSet;
        this.labelDataSetIndex = labelDataSet == null ? 0 : -1;
        this.stepBetweenCategory = stepBetweenCategory;
    }

    /// Creates a category steps definition that resolves labels from one dataset index in the chart
    /// data source.
    ///
    /// @param stepBetweenCategory whether major steps should fall between adjacent categories
    /// @param labelDataSetIndex   the zero-based dataset index used for label lookup
    public CategoryStepsDefinition(boolean stepBetweenCategory, int labelDataSetIndex) {
        this.labelBetweenCategory = false;
        this.labelDataSet = null;
        this.labelDataSetIndex = labelDataSetIndex;
        this.stepBetweenCategory = stepBetweenCategory;
    }

    /// Computes the label for one category position.
    ///
    /// When labels are configured to sit between categories, the lookup position is shifted by
    /// `0.5` so the definition reads the category to the right of the step boundary. If no dataset
    /// label can be resolved, the coordinate itself is formatted with the current locale's decimal
    /// separator.
    @Override
    public String computeLabel(double value) {
        double labelPosition = labelBetweenCategory ? value + 0.5 : value;
        DataSet currentLabelDataSet = getLabelDataSet();
        String label = null;

        if (currentLabelDataSet != null) {
            if (currentLabelDataSet.getClass() == DefaultDataSet.class
                    && !((DefaultDataSet) currentLabelDataSet).isUsingXValues()) {
                int labelIndex = (int) labelPosition;
                if (labelIndex >= 0 && labelIndex < currentLabelDataSet.size()) {
                    label = currentLabelDataSet.getDataLabel(labelIndex);
                }
            } else {
                DataPoints labelPoints = currentLabelDataSet.getDataInside(
                        new DataWindow(labelPosition, labelPosition, -Double.MAX_VALUE,
                                Double.MAX_VALUE),
                        0, true);
                if (labelPoints != null) {
                    try {
                        if (labelPoints.size() > 0) {
                            label = currentLabelDataSet.getDataLabel(labelPoints.getIndex(0));
                        }
                    } finally {
                        labelPoints.dispose();
                    }
                }
            }
        }

        if (label == null) {
            if (labelPosition == (long) labelPosition) {
                return String.valueOf((int) labelPosition);
            }
            String numericLabel = String.valueOf(labelPosition);
            if (numericLabel.indexOf('.') >= 0) {
                DecimalFormatSymbols symbols =
                        DecimalFormatSymbolsFactory.getDecimalFormatSymbolsInstance(getLocale());
                numericLabel = numericLabel.replace('.', symbols.getDecimalSeparator());
            }
            return numericLabel;
        }
        return label;
    }

    /// Returns the dataset currently used for label lookup.
    ///
    /// An explicitly supplied dataset wins. Otherwise this resolves the current chart data source
    /// lazily through [#getLabelDataSetIndex()].
    public DataSet getLabelDataSet() {
        DataSet resolvedLabelDataSet = labelDataSet;
        if (resolvedLabelDataSet == null && getScale() != null) {
            Chart chart = getScale().getChart();
            if (chart != null) {
                DataSource dataSource = chart.getDataSource();
                if (dataSource != null && dataSource.size() > labelDataSetIndex) {
                    resolvedLabelDataSet = dataSource.get(labelDataSetIndex);
                }
            }
        }
        return resolvedLabelDataSet;
    }

    /// Returns the zero-based chart data-source index used when labels are not backed by one
    /// explicit dataset.
    public final int getLabelDataSetIndex() {
        return labelDataSetIndex;
    }

    /// Returns whether this definition exposes both a major step sequence and a distinct sub-step
    /// sequence.
    ///
    /// That happens only when steps and labels are offset relative to each other.
    @Override
    public boolean hasSubStep() {
        return stepBetweenCategory != labelBetweenCategory;
    }

    @Override
    public double incrementStep(double value) {
        return value + 1.0;
    }

    @Override
    public double incrementSubStep(double value) {
        return value + 1.0;
    }

    /// Returns whether labels are positioned between categories instead of on category centers.
    public final boolean isLabelBetweenCategory() {
        return labelBetweenCategory;
    }

    /// Returns whether major steps fall between categories instead of on category centers.
    public final boolean isStepBetweenCategory() {
        return stepBetweenCategory;
    }

    /// Returns the greatest major step not greater than `value`.
    ///
    /// When steps sit between categories, the returned sequence is `..., -0.5, 0.5, 1.5, ...`.
    @Override
    public double previousStep(double value) {
        if (!stepBetweenCategory) {
            return Math.floor(value);
        }
        int doubledFloor = (int) Math.floor(value * 2.0);
        return doubledFloor % 2 != 0 ? doubledFloor * 0.5 : (doubledFloor - 1) * 0.5;
    }

    /// Returns the greatest sub-step not greater than `value`.
    ///
    /// Sub-steps are the complementary sequence to [#previousStep(double)] when labels and steps do
    /// not share the same positions.
    @Override
    public double previousSubStep(double value) {
        if (stepBetweenCategory) {
            return Math.floor(value);
        }
        int doubledFloor = (int) Math.floor(value * 2.0);
        return doubledFloor % 2 != 0 ? doubledFloor * 0.5 : (doubledFloor - 1) * 0.5;
    }

    /// Moves label lookups from category centers to the boundaries between categories, or back.
    ///
    /// Changing this setting invalidates cached labels and scale layout when the definition is
    /// already attached to a [Scale].
    public void setLabelBetweenCategory(boolean labelBetweenCategory) {
        if (this.labelBetweenCategory == labelBetweenCategory) {
            return;
        }
        this.labelBetweenCategory = labelBetweenCategory;
        refreshScale();
    }

    /// Changes which chart dataset supplies labels when no explicit label dataset was provided.
    ///
    /// The attached scale is refreshed only when this definition actually resolves labels through
    /// the chart data source.
    public void setLabelDataSetIndex(int labelDataSetIndex) {
        if (this.labelDataSetIndex == labelDataSetIndex) {
            return;
        }
        this.labelDataSetIndex = labelDataSetIndex;
        if (labelDataSet == null) {
            refreshScale();
        }
    }

    /// Moves major steps from category centers to the boundaries between categories, or back.
    ///
    /// Changing this setting invalidates cached labels and scale layout when the definition is
    /// already attached to a [Scale].
    public void setStepBetweenCategory(boolean stepBetweenCategory) {
        if (this.stepBetweenCategory == stepBetweenCategory) {
            return;
        }
        this.stepBetweenCategory = stepBetweenCategory;
        refreshScale();
    }

    void refreshScale() {
        if (getScale() != null) {
            getScale().getSteps().invalidateValues();
            getScale().invalidateLayout();
        }
    }
}
