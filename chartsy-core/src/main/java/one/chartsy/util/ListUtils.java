/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    public static List<Object> appendTo(List<Object> list, Object item) {
        List<Object> result = new ArrayList<>(list.size() + 1);
        result.addAll(list);
        result.add(item);
        return List.copyOf(result);
    }
}
