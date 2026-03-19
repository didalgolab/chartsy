/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.persistence.domain.ChartTemplateAggregateData;
import one.chartsy.persistence.domain.model.ChartTemplateRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistentChartTemplateCatalogTest {

    @Test
    void toLoadedTemplateRejectsUnsupportedPayloadVersions() {
        ChartTemplateAggregateData entity = templateEntity();
        entity.setPayloadVersion(ChartTemplateCatalog.PAYLOAD_VERSION + 1);

        assertThatThrownBy(() -> invokeToLoadedTemplate(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported chart template payload version");
    }

    @Test
    void toLoadedTemplateRejectsMalformedPayloadJson() {
        ChartTemplateAggregateData entity = templateEntity();
        entity.setPayloadJson("{");

        assertThatThrownBy(() -> invokeToLoadedTemplate(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid chart template payload");
    }

    @Test
    void findOrCreateBuiltInCanonicalizesRecoveredBuiltInAndDemotesExtraSystemTemplates() throws Exception {
        ChartTemplateAggregateData recoveredBuiltIn = templateEntity();
        recoveredBuiltIn.setName("Alpha System");
        recoveredBuiltIn.setNameKey("alpha system");
        recoveredBuiltIn.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);
        recoveredBuiltIn.setPayloadVersion(ChartTemplateCatalog.PAYLOAD_VERSION + 7);
        recoveredBuiltIn.setPayloadJson("{\"overlays\":[{\"name\":\"Custom\"}],\"indicators\":[]}");

        ChartTemplateAggregateData extraSystemTemplate = templateEntity();
        extraSystemTemplate.setName("Zulu System");
        extraSystemTemplate.setNameKey("zulu system");
        extraSystemTemplate.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);

        var entities = new ArrayList<>(List.of(recoveredBuiltIn, extraSystemTemplate));
        ChartTemplateRepository repo = inMemoryRepository(entities);

        ChartTemplateAggregateData builtIn = invokeCatalogMethod("findOrCreateBuiltIn",
                new Class<?>[]{ChartTemplateRepository.class},
                repo);

        assertThat(builtIn.getTemplateKey()).isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        assertThat(builtIn.getOrigin()).isEqualTo(ChartTemplateAggregateData.Origin.SYSTEM);
        assertThat(builtIn.getName()).isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_NAME);
        assertThat(builtIn.getNameKey()).isEqualTo("built-in default");
        assertThat(builtIn.getPayloadVersion()).isEqualTo(ChartTemplateCatalog.PAYLOAD_VERSION);
        assertThat(builtIn.getPayloadJson()).isEqualTo(builtInPayloadJson());
        assertThat(entities)
                .filteredOn(entity -> entity.getOrigin() == ChartTemplateAggregateData.Origin.SYSTEM)
                .singleElement()
                .extracting(ChartTemplateAggregateData::getTemplateKey)
                .isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        assertThat(extraSystemTemplate.getOrigin()).isEqualTo(ChartTemplateAggregateData.Origin.USER);
    }

    @Test
    void findOrCreateBuiltInCanonicalizesExistingBuiltInContent() throws Exception {
        ChartTemplateAggregateData builtIn = templateEntity();
        builtIn.setTemplateKey(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        builtIn.setName("Customized Built-in");
        builtIn.setNameKey("customized built-in");
        builtIn.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);
        builtIn.setPayloadVersion(ChartTemplateCatalog.PAYLOAD_VERSION + 3);
        builtIn.setPayloadJson("{\"overlays\":[],\"indicators\":[{\"name\":\"Custom\"}]}");

        ChartTemplateRepository repo = inMemoryRepository(new ArrayList<>(List.of(builtIn)));

        ChartTemplateAggregateData normalized = invokeCatalogMethod("findOrCreateBuiltIn",
                new Class<?>[]{ChartTemplateRepository.class},
                repo);

        assertThat(normalized.getTemplateKey()).isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        assertThat(normalized.getName()).isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_NAME);
        assertThat(normalized.getNameKey()).isEqualTo("built-in default");
        assertThat(normalized.getOrigin()).isEqualTo(ChartTemplateAggregateData.Origin.SYSTEM);
        assertThat(normalized.getPayloadVersion()).isEqualTo(ChartTemplateCatalog.PAYLOAD_VERSION);
        assertThat(normalized.getPayloadJson()).isEqualTo(builtInPayloadJson());
    }

    @Test
    void findDefaultEntityKeepsOneDeterministicDefaultAndRepairsDuplicates() throws Exception {
        ChartTemplateAggregateData builtIn = templateEntity();
        builtIn.setTemplateKey(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        builtIn.setName(ChartTemplateCatalog.BUILT_IN_TEMPLATE_NAME);
        builtIn.setNameKey("built-in default");
        builtIn.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);
        builtIn.setDefaultTemplate(false);

        ChartTemplateAggregateData alpha = templateEntity();
        alpha.setName("Alpha");
        alpha.setNameKey("alpha");
        alpha.setDefaultTemplate(true);

        ChartTemplateAggregateData beta = templateEntity();
        beta.setName("Beta");
        beta.setNameKey("beta");
        beta.setDefaultTemplate(true);

        var entities = new ArrayList<>(List.of(beta, builtIn, alpha));
        ChartTemplateRepository repo = inMemoryRepository(entities);

        ChartTemplateAggregateData effectiveDefault = invokeCatalogMethod("findDefaultEntity",
                new Class<?>[]{ChartTemplateRepository.class},
                repo);

        assertThat(effectiveDefault.getName()).isEqualTo("Alpha");
        assertThat(alpha.isDefaultTemplate()).isTrue();
        assertThat(beta.isDefaultTemplate()).isFalse();
        assertThat(builtIn.isDefaultTemplate()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeCatalogMethod(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = PersistentChartTemplateCatalog.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return (T) method.invoke(new PersistentChartTemplateCatalog(), args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception)
                throw exception;
            if (cause instanceof Error error)
                throw error;
            throw ex;
        }
    }

    private static ChartTemplateRepository inMemoryRepository(List<ChartTemplateAggregateData> entities) {
        return (ChartTemplateRepository) Proxy.newProxyInstance(
                ChartTemplateRepository.class.getClassLoader(),
                new Class<?>[]{ChartTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTemplateKey" -> entities.stream()
                            .filter(entity -> entity.getTemplateKey().equals(args[0]))
                            .findFirst();
                    case "findAllByOriginOrderByNameAsc" -> entities.stream()
                            .filter(entity -> entity.getOrigin() == args[0])
                            .sorted(Comparator.comparing(ChartTemplateAggregateData::getName))
                            .toList();
                    case "findAllByOrderByDefaultTemplateDescNameAsc" -> entities.stream()
                            .sorted(Comparator
                                    .comparing(ChartTemplateAggregateData::isDefaultTemplate).reversed()
                                    .thenComparing(ChartTemplateAggregateData::getName))
                            .toList();
                    case "save" -> {
                        ChartTemplateAggregateData entity = (ChartTemplateAggregateData) args[0];
                        if (!entities.contains(entity))
                            entities.add(entity);
                        yield entity;
                    }
                    case "toString" -> "InMemoryChartTemplateRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ChartTemplateCatalog.LoadedTemplate invokeToLoadedTemplate(ChartTemplateAggregateData entity) throws Exception {
        return invokeCatalogMethod("toLoadedTemplate", new Class<?>[]{ChartTemplateAggregateData.class}, entity);
    }

    private static String builtInPayloadJson() {
        return new com.google.gson.Gson().toJson(ChartTemplatePayloadMapper.getDefault().builtInPayload());
    }

    private static ChartTemplateAggregateData templateEntity() {
        ChartTemplateAggregateData entity = new ChartTemplateAggregateData();
        entity.setTemplateKey(UUID.randomUUID());
        entity.setName("Workspace Default");
        entity.setNameKey("workspace default");
        entity.setOrigin(ChartTemplateAggregateData.Origin.USER);
        entity.setDefaultTemplate(false);
        entity.setPayloadVersion(ChartTemplateCatalog.PAYLOAD_VERSION);
        entity.setPayloadJson("{\"overlays\":[],\"indicators\":[]}");
        return entity;
    }
}
