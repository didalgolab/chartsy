package one.chartsy.charting.util;

import java.util.Calendar;

/// Applies in-place floor operations to mutable [Calendar] instances.
///
/// These helpers are used by charting `TimeUnit` implementations to align a
/// timestamp to the start of a display unit (second, minute, hour, day, week, month, or year)
/// without allocating another calendar object.
public final class CalendarUtil {
    
    /// Floors `calendar` to the start of its current day in local calendar time.
    public static void dayFloor(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    /// Floors `calendar` to the start of its current hour.
    public static void hourFloor(Calendar calendar) {
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    /// Floors `calendar` to the start of its current minute.
    public static void minuteFloor(Calendar calendar) {
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    /// Floors `calendar` to the first day of its current month at `00:00:00.000`.
    public static void monthFloor(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    /// Floors `calendar` to the start of its current second.
    public static void secondFloor(Calendar calendar) {
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    /// Floors `calendar` to the first day of the current week at `00:00:00.000`.
    ///
    /// Week alignment follows [Calendar#getFirstDayOfWeek()], so the resulting date is locale and
    /// calendar-system dependent.
    public static void weekFloor(Calendar calendar) {
        int era = calendar.get(Calendar.ERA);
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int firstDayOfWeek = calendar.getFirstDayOfWeek();
        int offset = dayOfWeek - firstDayOfWeek;
        if (offset > 0)
            dayOfYear -= offset;
        else if (offset < 0)
            dayOfYear -= 7 + offset;
        
        boolean wasLenient = calendar.isLenient();
        calendar.clear();
        if (!wasLenient)
            calendar.setLenient(true);
        
        calendar.set(Calendar.ERA, era);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
        
        if (!wasLenient) {
            calendar.get(Calendar.YEAR);
            calendar.setLenient(false);
        }
    }
    
    /// Floors `calendar` to the first day of its current year at `00:00:00.000`.
    public static void yearFloor(Calendar calendar) {
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    private CalendarUtil() {
    }
}
