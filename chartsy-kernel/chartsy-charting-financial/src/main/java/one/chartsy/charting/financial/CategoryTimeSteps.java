package one.chartsy.charting.financial;

import java.awt.Insets;
import java.awt.Rectangle;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.StepsDefinition;
import one.chartsy.charting.TimeUnit;
import one.chartsy.charting.util.DoubleArray;
import one.chartsy.charting.util.text.DateFormatFactoryExt;

/**
 * Implements a numbering of category time steps.
 */
public class CategoryTimeSteps extends StepsDefinition {
  private final Date[] dates;
  private final DoubleArray steps;
  private one.chartsy.charting.TimeUnit stepUnit;
  private final one.chartsy.charting.TimeUnit baseUnit;
  private final Locale locale;
  private DateFormat dateFormat;
  private final Calendar calendar;
  private int categoryUnit;
  private int firstDate;

  /**
   * Creates a new <code>CategoryTimeSteps</code>.
   * 
   * @param dates
   *          The dates corresponding to the categories.
   * @param unit
   *          The time unit of the category.
   */
  public CategoryTimeSteps(Date[] dates, one.chartsy.charting.TimeUnit unit) {
    this.dates = dates;
    this.steps = new DoubleArray();
    this.baseUnit = unit;
    this.stepUnit = unit;
    this.locale = Locale.getDefault();
    this.calendar = Calendar.getInstance(locale);
    this.dateFormat = computeDateFormat();
  }

  /** Returns a calendar object for the specified time. */
  private Calendar getCalendar(Date time) {
    calendar.setTime(time);
    return calendar;
  }

  /** Returns the base time unit. */
  public final one.chartsy.charting.TimeUnit getBaseUnit() {
    return baseUnit;
  }

  /** Updates the steps. */
  @Override
  public void update() {
    steps.clear();

    DataInterval range = getScale().getAxis().getVisibleRange();
    stepUnit = computeStepUnit(range);
    categoryUnit = (int) Math.ceil(stepUnit.getMillis() / baseUnit.getMillis());
    dateFormat = computeDateFormat();

    firstDate = getFirstDate(range);
    if (firstDate != -1) {
      int idx = firstDate;
      Calendar cal = stepBoundary(dates[idx]);
      while (idx < range.getMax() && idx < dates.length) {
        steps.add(idx);
        cal = stepUnit.incrementTime(cal);
        idx = Arrays.binarySearch(dates, cal.getTime());
        if (idx < 0)
          idx = -idx - 1;
      }
    }
    steps.trim();
    // System.out.println("Steps: " + steps);
  }

  /**
   * Invoked to compute the time unit.
   */
  protected one.chartsy.charting.TimeUnit computeStepUnit(DataInterval range) {
    double multDay = Math.round(baseUnit.getMillis() / TimeUnit.DAY.getMillis());
    int days = (int) (multDay * range.getLength());
    if (days > 4 * 365) {
      return TimeUnit.YEAR;
    } else if (days > 2 * 365) {
      return TimeUnit.QUARTER;
    } else if (days > 120) {
      return TimeUnit.MONTH;
    } else if (days > 20) {
      return new CategoryTimeSteps.TimeUnit(one.chartsy.charting.TimeUnit.WEEK) {
        @Override
        public String getFormatString(Locale locale) {
          return "MM/dd";
        }
      };
    } else {
      return new CategoryTimeSteps.TimeUnit(one.chartsy.charting.TimeUnit.DAY) {
        @Override
        public String getFormatString(Locale locale) {
          return "MM/dd";
        }
      };
    }
  }

  private int getFirstDate(DataInterval range) {
    if (range.getMax() < 0 || range.getMin() > dates.length - 1)
      return -1;
    int min = (int) Math.ceil(range.getMin());
    if (categoryUnit == 1)
      return Math.max(0, min);
    int idx = (min < 0) ? 0 : min; // index of the first date
    Date d = dates[idx];
    Calendar cal = getCalendar(d);
    cal = stepUnit.previousUnitTime(cal);
    idx = Arrays.binarySearch(dates, cal.getTime());
    return (idx < 0) ? -idx - 1 : idx;
  }

  private Calendar stepBoundary(Date date) {
    Calendar cal = getCalendar(date);
    return stepUnit.previousUnitTime(cal);
  }

  /** Returns the step value immediately before the specified value. */
  @Override
  public double previousStep(double v) {
    int idx = Arrays.binarySearch(steps.data(), v);
    if (idx < 0) {
      idx = -idx - 2;
      if (idx == -1) {
        return firstDate - categoryUnit;
      } else {
        int count = steps.size();
        if (idx >= count)
          return steps.get(count - 1) + categoryUnit * (idx - count + 1);
      }
    }
    return steps.get(idx);

  }

  /** Returns the next step value. */
  @Override
  public double incrementStep(double v) {
    int idx = Arrays.binarySearch(steps.data(), v);
    if (idx < 0) {
      return v + categoryUnit;
    } else {
      if (idx == steps.size() - 1)
        return steps.get(idx) + categoryUnit;
      else
        return steps.get(idx + 1);
    }

  }

  /** Computes the date format. */
  protected DateFormat computeDateFormat() {
    String pattern = stepUnit.getFormatString(locale);
    return DateFormatFactoryExt.getInstance(pattern, locale, null);
  }

  /** Computes the label associated with the specified value. */
  @Override
  public String computeLabel(double value) {
    int i = (int) Math.round(value);
    Calendar cal = null;
    if (i < 0) {
      long m = dates[0].getTime() + ((long) stepUnit.getMillis()) * i;
      cal = getCalendar(new Date(m));
    } else if (i >= dates.length) {
      long m = dates[dates.length - 1].getTime() + ((long) stepUnit.getMillis()) * (dates.length - i + 1);
      cal = getCalendar(new Date(m));
    } else
      cal = getCalendar(dates[i]);
    return stepUnit.format(dateFormat, cal, getLocale());
  }

  protected int resolvePlotWidth() {
    if (getScale() == null || getScale().getChart() == null || getScale().getChart().getChartArea() == null)
      return -1;

    var chartArea = getScale().getChart().getChartArea();
    Rectangle plotRect = chartArea.getPlotRect();
    if (plotRect != null && !plotRect.isEmpty())
      return plotRect.width;

    Rectangle drawRect = chartArea.getDrawRect();
    if (drawRect == null || drawRect.isEmpty())
      return -1;

    Insets margins = chartArea.getMargins();
    int left = (margins != null) ? margins.left : 0;
    int right = (margins != null) ? margins.right : 0;
    return Math.max(1, drawRect.width - left - right);
  }

  // --------------------------------------------------------------------------
  /**
   * A time unit class that delegates operations to an existing
   * <code>TimeUnit</code> instance. This class is designed to customize the
   * display format of predefined time units.
   */
  private static class TimeUnit extends one.chartsy.charting.TimeUnit {
    protected one.chartsy.charting.TimeUnit delegate;

    TimeUnit(one.chartsy.charting.TimeUnit delegate) {
      this.delegate = delegate;
    }

    @Override
    public Calendar previousUnitTime(Calendar cal) {
      return delegate.previousUnitTime(cal);
    }

    @Override
    public Calendar incrementTime(Calendar cal) {
      return delegate.incrementTime(cal);
    }

    @Override
    public double getMillis() {
      return delegate.getMillis();
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getFormatString() {
      return delegate.getFormatString(Locale.getDefault());
    }

    @Override
    public String getFormatString(Locale locale) {
      return delegate.getFormatString(locale);
    }
  }
}

