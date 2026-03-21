package one.chartsy.charting.financial.indicator;

/**
 * Provides a set of methods to compute technical indicators of stock data.
 */
public class IndicatorUtil {
  /** The <i>simple moving average>/i> type. */
  public static final int SMA = 1;

  /** The <i>exponential moving average>/i> type. */
  public static final int EMA = 2;

  /**
   * The default undefined value, used to mark data when it cannot be computed.
   */
  public static final Double UNDEF_VALUE = Double.MIN_VALUE;

  /**
   * Computes the simple moving average of the specified data.
   * 
   * @param data
   *          The considered data.
   * @param period
   *          The period over which the average is computed.
   * @return The simple moving average. The size of returned array corresponds
   *         to the number of data (header not included). The array can contain
   *         undefined values if the header length is smaller than the specified
   *         period.
   */
  public static double[] computeSMA(IndicatorData data, int period) {
    int start = period - data.headerLength - 1;
    int count = data.dataCount;

    if (start >= count)
      return null;

    double[] vals = data.data;
    double[] res = new double[count];
    double sum = 0;

    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < -1) ? -start : 0; // j is the starting index in vals
    for (int c = j + period; j < c; ++j) {
      sum += vals[j];
    }
    res[i++] = sum / period;

    for (; i < count; ++i, ++j) {
      sum = sum - vals[j - period] + vals[j];
      res[i] = sum / period;
    }
    return res;
  }

  /**
   * Computes the exponential moving average of the specified data.
   * 
   * @param data
   *          The considered data.
   * @param period
   *          The period over which the average is computed.
   * @return The exponential moving average. The size of returned array
   *         corresponds to the number of data (header not included). The array
   *         can contain undefined values if the header length is smaller than
   *         the specified period.
   */
  public static double[] computeEMA(IndicatorData data, int period) {
    int start = period - data.headerLength - 1;
    int count = data.dataCount;

    if (start >= count)
      return null;

    double[] vals = data.data;
    double[] res = new double[count];
    final double k = 2. / (1 + period);
    double sum = 0;

    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < -1) ? -start : 0; // j is the starting index in vals
    for (int c = j + period; j < c; ++j) {
      sum += vals[j];
    }
    res[i++] = sum / period;
    for (; i < count; ++i, ++j) {
      res[i] = k * (vals[j] - res[i - 1]) + res[i - 1];
    }
    return res;
  }

  /**
   * Computes the <i>Relative Strength Index</i> of the specified data.
   */
  public static double[] computeRSI(IndicatorData data, int period) {

    int start = period - data.headerLength;
    int count = data.dataCount;

    if (start >= count)
      return null;

    double[] vals = data.data;
    double[] res = new double[count];

    double avgUp = 0, avgDown = 0;
    int pp = period - 1;

    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < 0) ? -start : 0; // j is the starting index in vals

    double close, prevClose = vals[j++];
    for (int c = j + period - 1; j < c; ++j) {
      close = vals[j];
      if (prevClose <= close)
        avgUp += close - prevClose;
      else
        avgDown += prevClose - close;
      prevClose = close;
    }
    avgUp /= period;
    avgDown /= period;

    res[i++] = (avgUp / (avgUp + avgDown)) * 100.;

    for (; i < count; ++i, ++j) {
      close = vals[j];
      if (prevClose <= close) {
        avgUp = (avgUp * pp + close - prevClose) / period;
        avgDown = (avgDown * pp) / period;
      } else {
        avgUp = (avgUp * pp) / period;
        avgDown = (avgDown * pp + prevClose - close) / period;
      }
      res[i] = (avgUp / (avgUp + avgDown)) * 100.;
      prevClose = close;
    }

    return res;
  }

  /**
   * Compute the <i>low</i> values of the specified data across the specified
   * periods.
   */
  public static double[] computeLow(IndicatorData data, int period, boolean backward) {
    int start = period - data.headerLength;
    if (!backward)
      --start;
    int count = data.dataCount;
    double[] res = new double[count];
    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }
    int j = (start < (backward ? 0 : -1)) ? -start : 0;
    for (; i < count; ++i, ++j) {
      res[i] = computeMin(data.data, j, period);
    }
    return res;
  }

  /**
   * Compute the <i>high</i> values of the specified data across the specified
   * periods.
   */
  public static double[] computeHigh(IndicatorData data, int period, boolean backward) {
    int start = period - data.headerLength;
    if (!backward)
      --start;
    int count = data.dataCount;
    double[] res = new double[count];
    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }
    int j = (start < (backward ? 0 : -1)) ? -start : 0;
    for (; i < count; ++i, ++j) {
      res[i] = computeMax(data.data, j, period);
    }
    return res;
  }

  /**
   * Compute the <i>Oscillator</i> of the specified data.
   */
  public static double[] computeOscillator(IndicatorData data, int fastPeriod, int slowPeriod, int avgType) {
    double[] slow = null;
    double[] fast = null;

    if (avgType == SMA) {
      slow = computeSMA(data, slowPeriod);
      fast = computeSMA(data, fastPeriod);
    } else {
      slow = computeEMA(data, slowPeriod);
      fast = computeEMA(data, fastPeriod);
    }

    if (slow == null || fast == null)
      return null;

    int count = data.dataCount;
    double uv = UNDEF_VALUE.doubleValue();
    for (int i = 0; i < count; ++i) {
      if (slow[i] == uv)
        fast[i] = uv;
      else
        fast[i] -= slow[i];
    }
    return fast;
  }

  /**
   * Computes the <i>Standard Devisation</i> of the specified data.
   */
  public static double[] computeStdDev(IndicatorData data, int period) {
    int start = period - data.headerLength - 1;
    int count = data.dataCount;
    double[] res = new double[count];
    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < -1) ? -start : 0;
    double[] vals = data.data;
    double avg, dev, sum;
    for (; i < count; ++i, ++j) {
      avg = computeAverage(vals, j, period);
      sum = 0;
      for (int k = j, c = k + period; k < c; ++k) {
        dev = avg - vals[k];
        sum += dev * dev;
      }
      res[i] = Math.sqrt(sum / period);
    }
    return res;
  }

  /**
   * Computes the <i>Stochastic oscillator</i> of the specified data.
   */
  public static double[] computeStochastic(IndicatorData hiData, IndicatorData loData, IndicatorData closeData,
      int period) {
    int start = period - closeData.headerLength - 1;
    int count = closeData.dataCount;
    double[] hi = hiData.data;
    double[] lo = loData.data;
    double[] close = closeData.data;

    double[] res = new double[count];
    double hh = -Double.MAX_VALUE;
    double ll = Double.MAX_VALUE;

    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < -1) ? -start : 0; // j is the starting index
    for (; i < count; ++i, ++j) {
      hh = computeMax(hi, j, period);
      ll = computeMin(lo, j, period);
      res[i] = 100. * (close[j + period - 1] - ll) / (hh - ll);
    }

    return res;
  }

  /**
   * Computes the <i>Stochastic oscillator</i> of the specified data.
   */
  public static double[] computeWilliamsR(IndicatorData hiData, IndicatorData loData, IndicatorData closeData,
      int period) {
    int start = period - closeData.headerLength - 1;
    int count = closeData.dataCount;
    double[] hi = hiData.data;
    double[] lo = loData.data;
    double[] close = closeData.data;

    double[] res = new double[count];
    double hh = -Double.MAX_VALUE;
    double ll = Double.MAX_VALUE;

    int i = 0;
    for (; i < start; ++i) {
      res[i] = UNDEF_VALUE.doubleValue();
    }

    int j = (start < -1) ? -start : 0; // j is the starting index
    for (; i < count; ++i, ++j) {
      hh = computeMax(hi, j, period);
      ll = computeMin(lo, j, period);
      res[i] = -100. * (hh - close[j + period - 1]) / (hh - ll);
    }
    return res;
  }

  public static double[][] computePVO(double[] volume, int count, int fastPeriod, int slowPeriod, int signalPeriod,
      boolean divergence) {
    if (fastPeriod > slowPeriod)
      throw new IllegalArgumentException();

    int resCount = 1;
    if (signalPeriod > 1) {
      ++resCount;
      if (divergence)
        ++resCount;
    }

    double[][] res = new double[resCount][];

    // == Compute PVO
    // double[] slow = null; // computeEMA(volume, count, slowPeriod);
    // double[] fast = null;// computeEMA(volume, count, fastPeriod);

    res[0] = new double[count];
    int i = 0;
    for (; i < slowPeriod; ++i) {
      res[0][i] = UNDEF_VALUE.doubleValue();
    }

    // for (; i < count; ++i) {
    // res[0][i] = 100. * ((fast[i] - slow[i]) / fast[i]);
    // }

    if (resCount == 1)
      return res;

    // == Compute PVO EMA
    // res[1] = computeEMA(res[0], slowPeriod, count, signalPeriod);

    // if (divergence) {
    //
    // }

    return res;

  }

  /**
   * Computes the minimum value in the specified period.
   */
  private static double computeMin(double[] vals, int start, int period) {
    double min = Double.MAX_VALUE;
    for (int i = start, c = start + period; i < c; ++i) {
      if (vals[i] < min)
        min = vals[i];
    }
    return min;
  }

  /**
   * Computes the maximum values in the specified period.
   */
  private static double computeMax(double[] vals, int start, int period) {
    double max = -Double.MAX_VALUE;
    for (int i = start, c = start + period; i < c; ++i) {
      if (vals[i] > max)
        max = vals[i];
    }
    return max;
  }

  /**
   * Computes the average values in the specified period.
   */
  private static double computeAverage(double[] vals, int start, int period) {
    double avg = 0;
    for (int i = start, c = start + period; i < c; ++i) {
      avg += vals[i];
    }
    return avg / period;
  }

  private IndicatorUtil() {
  }
}

