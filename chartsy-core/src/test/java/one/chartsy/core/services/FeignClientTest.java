package one.chartsy.core.services;

import feign.Feign;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

class FeignClientTest {

    @Test
    void is_present_in_default_Lookup() {
        assertThat(Lookup.getDefault().lookup(Feign.class)).isNotNull();
        assertThat(Lookup.getDefault().lookup(FeignClient.class)).isNotNull();
    }

}