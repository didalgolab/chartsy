package one.chartsy.charting.financial;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Represents a date range for stock queries.
 */
public abstract class StockQueryRange {
  /**
   * An array of predefined query ranges.
   */
  public static final StockQueryRange[] RANGES = { new Month(1), new Month(2), new Month(3), new Month(6), new Year(1),
      new Year(2), new Year(3), new Year(5), new Year(10) };

  /**
   * Returns the start and end dates of the query.
   */
  public abstract Date[] getRange();

  /** Month range class. */
  private static class Month extends StockQueryRange {
    private final int monthCount;

    Month(int monthCount) {
      this.monthCount = monthCount;
    }

    @Override
    public Date[] getRange() {
      Calendar cal = Calendar.getInstance(Locale.getDefault());
      Date endDate = new Date();
      cal.setTime(endDate);
      cal.add(Calendar.MONTH, -monthCount);
      cal.add(Calendar.DAY_OF_YEAR, -1);
      return new Date[] { cal.getTime(), endDate };
    }

    @Override
    public String toString() {
      return monthCount + " Month" + (monthCount > 1 ? "s" : "");
    }
  }

  /** Year range class. */
  private static class Year extends StockQueryRange {
    private final int yearCount;

    Year(int yearCount) {
      this.yearCount = yearCount;
    }

    @Override
    public Date[] getRange() {
      Calendar cal = Calendar.getInstance(Locale.getDefault());
      Date endDate = new Date();
      cal.setTime(endDate);
      cal.add(Calendar.YEAR, -yearCount);
      return new Date[] { cal.getTime(), endDate };
    }

    @Override
    public String toString() {
      return yearCount + " Year" + (yearCount > 1 ? "s" : "");
    }
  }

}

