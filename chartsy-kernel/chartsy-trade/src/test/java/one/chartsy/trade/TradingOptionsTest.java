/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.trade.services.DefaultTradingOptionsProvider;
import org.junit.jupiter.api.Test;
import org.netbeans.modules.openide.util.GlobalLookup;
import org.openide.util.lookup.Lookups;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TradingOptionsTest {

    @Test
    void getDefault_gives_sensible_default_options() {
        TradingOptions options = TradingOptions.getDefault();

        assertThat(options.globalVariables()).isEmpty();
    }

    @Test
    void getDefault_gives_same_instance_when_called_many_times() {
        TradingOptions first = TradingOptions.getDefault(), second = TradingOptions.getDefault();
        assertSame(first, second);
    }

    @Test
    void getDefault_is_extensible_with_service_provider() {
        var newGlobalVariables = Map.of("NewOption","aValue");

        class MyServiceProvider extends DefaultTradingOptionsProvider {
            @Override
            public ImmutableTradingOptions.Builder getTradingOptionsBuilder() {
                return super.getTradingOptionsBuilder().globalVariables(newGlobalVariables);
            }
        }
        GlobalLookup.execute(Lookups.singleton(new MyServiceProvider()),
                () -> assertThat(TradingOptions.getDefault().globalVariables())
                        .isEqualTo(newGlobalVariables));
    }
}