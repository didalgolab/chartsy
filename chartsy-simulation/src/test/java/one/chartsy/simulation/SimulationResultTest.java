/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import one.chartsy.core.json.GsonTypeAdapters;
import one.chartsy.simulation.services.HostSimulationResultBuilderFactory;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;

import java.util.HashMap;

public class SimulationResultTest {

    private Gson gson = GsonTypeAdapters.installOn(new GsonBuilder()).create();

    //@Test // TODO not stable
    public void can_be_converted_to_and_from_Json() {
        Object r = Lookup.getDefault().lookup(HostSimulationResultBuilderFactory.class)
                .create(new HashMap<>())
                .build();

        String json = gson.toJson(r);

        //Object r2 = gson.fromJson(json, SimulationResult.Builder.class);
        //System.out.println(gson.fromJson(json, SimulationResult.Builder.class).build());

    }
}
