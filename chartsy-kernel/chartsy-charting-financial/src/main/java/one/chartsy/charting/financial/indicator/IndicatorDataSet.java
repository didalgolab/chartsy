package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.CombinedDataSet;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;

/**
 * Base class for techinal indicator data sets.
 */
public abstract class IndicatorDataSet extends CombinedDataSet {
  /**
   * Used to disable indicator computation when the data sets are initialized.
   */
  private boolean computeData = false;

  /**
   * Holds the indicator data.
   */
  private double[] indicatorData = new double[0];

  /**
   * Tells whether x-values are sorted.
   */
  private boolean xValuesSorted = false;

  /**
   * Initializes a <code>IndicatorDataSet</code>.
   */
  public IndicatorDataSet(DataSet[] dataSets) {
    this(dataSets, true);
  }

  /**
   * Initializes a <code>IndicatorDataSet</code>.
   */
  public IndicatorDataSet(DataSet[] dataSets, boolean computeData) {
    setUndefValue(IndicatorUtil.UNDEF_VALUE);
    this.computeData = computeData;
    setDataSets(dataSets);
  }

  @Override
  public void setUndefValue(Double undefValue) {
    if (undefValue != IndicatorUtil.UNDEF_VALUE)
      throw new IllegalArgumentException();
    super.setUndefValue(undefValue);
  }

  /**
   * Returns the name of this data set.
   * 
   * @return The name specified with the AbstractDataSet.setName
   *         method, or the indicator name.
   */
  @Override
  public String getName() {
    String name = super.getName();
    return (name != null) ? name : getIndicatorName();

  }

  /**
   * Returns the name of the indicator. The default implementation returns
   * <code>null</code>.
   */
  protected String getIndicatorName() {
    return null;
  }

  /**
   * Invoked when the referenced data sets change.
   */
  @Override
  protected void dataSetsChanged() {
    super.dataSetsChanged();
    int count = getDataSetCount();
    xValuesSorted = true;
    for (int i = 0; i < count; ++i) {
      if (!getDataSet(i).isXValuesSorted()) {
        xValuesSorted = false;
        break;
      }
    }
    updateIndicatorData();
  }

  /**
   * Returns <code>true</code> if the referenced data set provides sorted X
   * values.
   */
  @Override
  public boolean isXValuesSorted() {
    return xValuesSorted;
  }

  // --------------------------------------------------------------------------
  /**
   * Returns the Y-value of the data point at the specified index.
   */
  @Override
  public double getYData(int idx) {
    return indicatorData[idx];
  }

  /**
   * Returns the x-value of the data point at the specified index.
   */
  @Override
  public double getXData(int idx) {
    return getDataSet(0).getXData(idx);
  }

  // --------------------------------------------------------------------------
  /**
   * Called when one of the data sets referenced by this object has been
   * modified.
   */
  @Override
  protected void dataSetContentsChanged(DataSetContentsEvent evt) {
    super.dataSetContentsChanged(evt);
    switch (evt.getType()) {
    case DataSetContentsEvent.BATCH_BEGIN:
    case DataSetContentsEvent.BATCH_END:
    case DataSetContentsEvent.BEFORE_DATA_CHANGED:
    case DataSetContentsEvent.DATA_LABEL_CHANGED:
      break;
    default:
      updateIndicatorData();
      fireDataSetContentsEvent(new DataSetContentsEvent(this));
      break;
    }
  }

  /**
   * Called when a property of one of the data sets referenced by this object
   * has been modified. The method forwards the received event, as if this data
   * set was the source of the event.
   */
  @Override
  protected void dataSetPropertyChanged(DataSetPropertyEvent evt) {
    fireDataSetPropertyEvent(new DataSetPropertyEvent(this, evt));
  }

  // --------------------------------------------------------------------------
  protected void updateIndicatorData() {
    if (!computeData) {
      computeData = true;
      return;
    }
    indicatorData = computeIndicatorData();
    if (indicatorData == null)
      indicatorData = new double[0];

    invalidateLimits();
    updateDataCount();
  }

  /**
   * Computes the indicator data.
   * 
   * @return The indicator data.
   */
  protected abstract double[] computeIndicatorData();

  @Override
  protected int computeDataCount() {
    return indicatorData.length;
  }
}

