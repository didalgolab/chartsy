/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.openide.util.Lookup;

/**
 *
 * @author Mariusz Bernacki
 */
public class IndicatorManager {
    
    private static IndicatorManager instance;
    private LinkedHashMap<String, Indicator> indicators;
    
    public static IndicatorManager getDefault() {
        if (instance == null) instance = new IndicatorManager();
        return instance;
    }
    
    private IndicatorManager() {
        indicators = new LinkedHashMap<>();
        Collection<? extends Indicator> list = Lookup.getDefault().lookupAll(Indicator.class);
        for (Indicator i : list) {
            indicators.put(i.getName(), i);
        }
        sort();
    }
    
    private void sort() {
        List<String> mapKeys = new ArrayList<>(indicators.keySet());
        Collections.sort(mapKeys);
        
        LinkedHashMap<String, Indicator> someMap = new LinkedHashMap<>();
        for (String mapKey : mapKeys) {
            someMap.put(mapKey, indicators.get(mapKey));
        }
        indicators = someMap;
    }
    
    public Indicator getIndicator(String key) {
        return indicators.get(key);
    }
    
    public List<Indicator> getIndicatorsList() {
        List<Indicator> list = new ArrayList<>();
        for (String s : indicators.keySet()) {
            list.add(indicators.get(s));
        }
        return list;
    }
    
    public List<String> getIndicators() {
        List<String> list = new ArrayList<>(indicators.keySet());
        Collections.sort(list);
        return list;
    }
    
}
