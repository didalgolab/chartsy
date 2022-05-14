/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import one.chartsy.When;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StrategyMultiplexer {

    private final List<Plan> plans = new ArrayList<>();
    private When[] dispatchTable = new When[0];

    public void dispatch(When when) {
        int id = when.getId();
        if (dispatchTable.length <= id)
            dispatchTable = Arrays.copyOf(dispatchTable, id + 1);
        if (dispatchTable[id] == null) {
            List<Invoker> invokers = new ArrayList<>();
            for (Plan plan : plans)
                invokers.add(plan.createInvoker(when));

        }
    }

    public interface Plan {
        Invoker createInvoker(When when);
    }

    public interface Invoker {

    }

}
