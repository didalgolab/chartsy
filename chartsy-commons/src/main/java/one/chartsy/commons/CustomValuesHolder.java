/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.commons;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines a basic contract for objects being able to hold user-definable values
 * of any type.
 * <p>
 * Custom values are uniquely identified by their name.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface CustomValuesHolder {
    
    /**
     * Gets a {@code Map} of all currently available custom values.
     * <p>
     * The returned {@code Map} <i>may</i> or <i>may not</i> be modifiable. It
     * <i>may</i> also represent a <i>snapshot</i> of values that won't reflect
     * future changes. The primary purpose of the returned {@code Map} is for
     * fetch or scan operations, thus any modifications to custom values should
     * be passed by {@link #setCustomValue(String, Object)} or
     * {@link #removeCustomValue(String)} methods.
     * 
     * @return the currently available custom values, or an empty map if no
     *         custom values exist
     */
    Map<String, Object> getCustomValues();
    
    /**
     * Returns custom value for the given {@code name} or {@code null} if no
     * such value exists.
     * 
     * @param name
     *            the name of custom value to be returned
     * @return the value for the given {@code name}
     */
    @SuppressWarnings("unchecked")
    default <T> T getCustomValue(String name) {
        return (T)getCustomValues().get(name);
    }
    
    /**
     * Checks whether a custom value with the given {@code name} exists.
     * 
     * @param name
     *            the name of custom value to search for
     * @return {@code true} if custom value exists, and {@code false} otherwise
     */
    default boolean hasCustomValue(String name) {
        return (null != getCustomValue(name));
    }
    
    /**
     * Assigns custom value with the given {@code name}. If this object
     * previously contained a mapping for the {@code name}, the old value is
     * replaced by the specified {@code value}.
     * 
     * @param name
     *            the custom value name
     * @param value
     *            the custom value
     */
    void setCustomValue(String name, Object value);
    
    /**
     * Removes custom value with the given name if such value exists.
     * 
     * @param name
     *            the name of custom value to be removed
     */
    void removeCustomValue(String name);
    
    /**
     * Static helper method for implementors of this interface. Adds new custom
     * value to the given holder map.
     * 
     * @param holder
     *            the holder map, if {@code null} then new <i>unmodifiable
     *            singleton map</i> is instantiated and returned
     * @param name
     *            the custom value name
     * @param value
     *            the custom value to be added
     * @return the custom values map
     */
    static Map<String, Object> setCustomValue(Map<String, Object> holder, String name, Object value) {
        if (holder == null || holder.isEmpty())
            return Collections.singletonMap(name, value);
        
        if (holder.size() == 1)
            holder = new TreeMap<>(holder);
        holder.put(name, value);
        return holder;
    }
    
    /**
     * Static helper method for implementors of this interface. Removes new
     * custom value from the given holder map.
     * 
     * @param holder
     *            the custom values holder map
     * @param name
     *            the name of the custom value to be removed
     * @return the new holder map without specified custom value
     */
    static Map<String, Object> removeCustomValue(Map<String, Object> holder, String name) {
        if (holder != null && !holder.isEmpty()) {
            if (holder.size() == 1)
                return Collections.emptyMap();
            
            holder.remove(name);
        }
        return holder;
    }
}
