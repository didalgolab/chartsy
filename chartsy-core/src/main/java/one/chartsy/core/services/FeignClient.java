package one.chartsy.core.services;

import feign.Feign;
import feign.Target;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.http2client.Http2Client;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

@ServiceProviders({
        @ServiceProvider(service = Feign.class),
        @ServiceProvider(service = FeignClient.class)
})
public class FeignClient extends Feign {

    private final Feign feign = toBuilder().build();

    @Override
    public <T> T newInstance(Target<T> target) {
        return feign.newInstance(target);
    }

    public <T> T newInstance(Class<T> type, String url) {
        return newInstance(new Target.HardCodedTarget<>(type, url));
    }

    public Feign.Builder toBuilder() {
        return Feign.builder()
                .encoder(new GsonEncoder())
                .decoder(new GsonDecoder())
                .client(new Http2Client());
    }
}
