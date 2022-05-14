/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.services;

import com.google.gson.GsonBuilder;
import feign.Feign;
import feign.Logger;
import feign.RequestTemplate;
import feign.Target;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.http2client.Http2Client;
import one.chartsy.core.json.GsonTypeAdapters;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

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
                .encoder(new GsonEncoder(GsonTypeAdapters.installOn(new GsonBuilder()).create()) {
                    @Override
                    public void encode(Object object, Type bodyType, RequestTemplate template) {
                        if (bodyType instanceof Class bodyClass && (bodyClass.isInterface() || Modifier.isAbstract(bodyClass.getModifiers())))
                            bodyType = object.getClass();
                        super.encode(object, bodyType, template);
                    }
                })
                .decoder(new GsonDecoder())
                .client(new Http2Client())
                .logger(new Log4j2Logger(getClass()))
                .logLevel(Logger.Level.FULL);
    }
}
