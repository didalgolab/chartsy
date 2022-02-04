package one.chartsy.samples.backtest.reporting;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import one.chartsy.SymbolIdentity;
import one.chartsy.core.json.GsonTypeAdapters;
import one.chartsy.core.services.FeignClient;
import one.chartsy.frontend.FrontEnd;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.simulation.ImmutableSimulationResult;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.rest.feign.BacktestRestClient;
import one.chartsy.simulation.services.HostSimulationResultBuilderFactory;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PushSimulationResultExample {

    public static void main2(String[] args) throws IOException {
        var jacksonMapper = new ObjectMapper();
        jacksonMapper.setVisibility(jacksonMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        jacksonMapper.registerModule(new JavaTimeModule());
        jacksonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        var gson = GsonTypeAdapters.installOn(new GsonBuilder()).create();
        var simulationResult = Lookup.getDefault().lookup(HostSimulationResultBuilderFactory.class)
                .create(Collections.emptyMap())
                .build();
        var buf = new StringBuilder("[");

        //System.in.read();
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000_000; i++) {
            if (i == 10)
                startTime = System.nanoTime();
            if (buf.length() > 1)
                buf.append(',');
            //String json = jacksonMapper.writeValueAsString(simulationResult);
            String json = gson.toJson(simulationResult);
            buf.append(json);
        }
        buf.append(']');
        System.out.println("TIME INFO: " + (System.nanoTime() - startTime)/1000L + " us");

        SimpleModule module = new SimpleModule("JacksonModule", Version.unknownVersion());
        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(SimulationResult.class, ImmutableSimulationResult.class);

        module.setAbstractTypes(resolver);

        jacksonMapper.registerModule(module);

        jacksonMapper.registerSubtypes(ImmutableSimulationResult.class);
//        jacksonMapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
//            @Override
//            public Class<?> findPOJOBuilder(AnnotatedClass ac) {
//                if (SimulationResult.class.equals(ac.getRawType())) {
//                    return SimulationResult.Builder.class;
//                }
//                return super.findPOJOBuilder(ac);
//            }
//
//            @Override
//            public JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
//                if (ac.hasAnnotation(JsonPOJOBuilder.class)) {
//                    return super.findPOJOBuilderConfig(ac);
//                }
//                return new JsonPOJOBuilder.Value("build", "");
//            }
//        });

        for (int i = 0; i < 8; i++) {
            startTime = System.nanoTime();
            jacksonMapper.readValue(buf.toString(), new TypeReference<List<ImmutableSimulationResult>>() { });
            System.out.println("DESER TIME INFO: " + (System.nanoTime() - startTime) / 1000L + " us");
        }

//        for (int i = 0; i < 8; i++) {
//            startTime = System.nanoTime();
//            gson.fromJson(buf.toString(), new TypeToken<List<ImmutableSimulationResult>>() {}.getType());
//            System.out.println("DESER TIME INFO: " + (System.nanoTime() - startTime) / 1000L + " us");
//        }
    }

    public static void main(String[] args) throws JsonProcessingException {
        var restClient = Lookup.getDefault().lookup(FeignClient.class)
                .newInstance(BacktestRestClient.class, "http://localhost:8080");
        var simulationResult = Lookup.getDefault().lookup(HostSimulationResultBuilderFactory.class)
                .create(Collections.emptyMap())
                .build();

//        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
//        ObjectMapper mapper = frontEnd.getApplicationContext().getBean(ObjectMapper.class);
//        var gson = GsonTypeAdapters.installOn(new GsonBuilder()).create();
//        var json = gson.toJson(simulationResult);
//        System.out.println(json);
//        System.out.println(mapper.readValue(json, ImmutableSimulationResult.class));

        for (int i = 0; i < 100_000; i++)
            restClient.updateSimulationResult(simulationResult);
    }
}
