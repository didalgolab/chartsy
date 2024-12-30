/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Represents an item that is defined by a point in time and additionally
 * suggests having an entry behavior by an event (e.g. a transaction).
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface ChronologicalEntry {
    /**
     * Returns the timestamp of an event or object described by this
     * interface. The result reflects a number of microseconds since the
     * epoch measured with a UTC time-zoned clock.
     * 
     * @return the event opening/entering time
     */
    long getEntryTime();
    
    /**
     * Returns the opening date and time of an event or object described by
     * this interface (e.g. a transaction).
     * 
     * @return the event opening/entering date and time
     */
    default LocalDateTime getEntryDate() {
        return Chronological.toDateTime(getEntryTime());
    }
    
    /**
     * Returns the opening date and time of an event or object described by this
     * interface in the specified time zone.
     * 
     * @param zone
     *            the time zone
     * @return the event opening/entering date and time
     */
    default ZonedDateTime getEntryDate(ZoneId zone) {
        return Chronological.toDateTime(getEntryTime(), zone);
    }
}