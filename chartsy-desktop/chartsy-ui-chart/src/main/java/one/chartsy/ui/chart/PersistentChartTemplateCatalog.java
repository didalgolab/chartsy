/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import one.chartsy.kernel.Kernel;
import one.chartsy.persistence.domain.ChartTemplateAggregateData;
import one.chartsy.persistence.domain.model.ChartTemplateRepository;
import one.chartsy.ui.chart.components.ChartPluginSelection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@ServiceProvider(service = ChartTemplateCatalog.class)
public class PersistentChartTemplateCatalog implements ChartTemplateCatalog {
    private static final Logger log = LogManager.getLogger(PersistentChartTemplateCatalog.class);
    private static final UUID BUILT_IN_TEMPLATE_KEY = UUID.fromString("8f0fdd06-0a53-4a32-a8d6-d136c4e1c1a6");

    private final Gson gson = new GsonBuilder().create();
    private final ChartTemplatePayloadMapper mapper = ChartTemplatePayloadMapper.getDefault();

    @Override
    public List<ChartTemplateSummary> listTemplates() {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            return repo.findAllByOrderByDefaultTemplateDescNameAsc().stream()
                    .map(mapper::toSummary)
                    .toList();
        });
    }

    @Override
    public LoadedTemplate getTemplate(UUID templateKey) {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = repo.findByTemplateKey(Objects.requireNonNull(templateKey, "templateKey"))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown chart template: " + templateKey));
            return toLoadedTemplate(entity);
        });
    }

    @Override
    public LoadedTemplate getDefaultTemplate() {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            return toLoadedTemplate(findDefaultEntity(repo));
        });
    }

    @Override
    public LoadedTemplate resolveTemplate(UUID templateKey) {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = null;
            if (templateKey != null)
                entity = repo.findByTemplateKey(templateKey).orElse(null);
            if (entity == null)
                entity = findDefaultEntity(repo);
            return toLoadedTemplate(entity);
        });
    }

    @Override
    public ChartTemplateSummary createTemplate(String name, ChartPluginSelection selection) {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            String normalizedName = normalizeName(name);
            String nameKey = normalizeNameKey(normalizedName);
            if (repo.existsByNameKey(nameKey))
                throw new IllegalArgumentException("Chart template already exists: " + normalizedName);

            ChartTemplateAggregateData entity = new ChartTemplateAggregateData();
            entity.setTemplateKey(UUID.randomUUID());
            entity.setName(normalizedName);
            entity.setNameKey(nameKey);
            entity.setOrigin(ChartTemplateAggregateData.Origin.USER);
            entity.setDefaultTemplate(false);
            entity.setPayloadVersion(PAYLOAD_VERSION);
            entity.setPayloadJson(serialize(mapper.fromSelection(selection)));
            return mapper.toSummary(repo.save(entity));
        });
    }

    @Override
    public ChartTemplateSummary updateTemplate(UUID templateKey, String name, ChartPluginSelection selection) {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = repo.findByTemplateKey(Objects.requireNonNull(templateKey, "templateKey"))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown chart template: " + templateKey));
            if (entity.getOrigin() == ChartTemplateAggregateData.Origin.SYSTEM)
                throw new IllegalStateException("The built-in chart template is immutable");

            String normalizedName = normalizeName(name);
            String nameKey = normalizeNameKey(normalizedName);
            if (repo.existsByNameKeyAndTemplateKeyNot(nameKey, entity.getTemplateKey()))
                throw new IllegalArgumentException("Chart template already exists: " + normalizedName);

            entity.setName(normalizedName);
            entity.setNameKey(nameKey);
            entity.setPayloadVersion(PAYLOAD_VERSION);
            entity.setPayloadJson(serialize(mapper.fromSelection(selection)));
            return mapper.toSummary(repo.save(entity));
        });
    }

    @Override
    public void deleteTemplate(UUID templateKey) {
        inTransactionVoid(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = repo.findByTemplateKey(Objects.requireNonNull(templateKey, "templateKey"))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown chart template: " + templateKey));
            if (entity.getOrigin() == ChartTemplateAggregateData.Origin.SYSTEM)
                throw new IllegalStateException("The built-in chart template cannot be deleted");

            boolean deletedDefault = entity.isDefaultTemplate();
            repo.delete(entity);
            if (deletedDefault)
                setBuiltInDefault(repo, false);
        });
    }

    @Override
    public ChartTemplateSummary setDefaultTemplate(UUID templateKey) {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = repo.findByTemplateKey(Objects.requireNonNull(templateKey, "templateKey"))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown chart template: " + templateKey));
            clearDefaultFlags(repo);
            entity.setDefaultTemplate(true);
            return mapper.toSummary(repo.save(entity));
        });
    }

    @Override
    public ChartTemplateSummary restoreBuiltIn() {
        return inTransaction(repo -> {
            ensureInitialized(repo);
            ChartTemplateAggregateData entity = findOrCreateBuiltIn(repo);
            clearDefaultFlags(repo);
            entity.setName(BUILT_IN_TEMPLATE_NAME);
            entity.setNameKey(normalizeNameKey(BUILT_IN_TEMPLATE_NAME));
            entity.setDefaultTemplate(true);
            entity.setPayloadVersion(PAYLOAD_VERSION);
            entity.setPayloadJson(serialize(mapper.builtInPayload()));
            return mapper.toSummary(repo.save(entity));
        });
    }

    private LoadedTemplate toLoadedTemplate(ChartTemplateAggregateData entity) {
        ChartTemplateSummary summary = mapper.toSummary(entity);
        StoredChartTemplatePayload payload = deserialize(entity.getPayloadJson());
        ChartTemplate chartTemplate = mapper.toChartTemplate(summary.name(), payload);
        return new LoadedTemplate(summary, chartTemplate, payload);
    }

    private void ensureInitialized(ChartTemplateRepository repo) {
        ChartTemplateAggregateData builtIn = findOrCreateBuiltIn(repo);
        ChartTemplateAggregateData currentDefault = repo.findByDefaultTemplateTrue().orElse(null);
        if (currentDefault == null) {
            clearDefaultFlags(repo);
            builtIn.setDefaultTemplate(true);
            repo.save(builtIn);
        }
    }

    private ChartTemplateAggregateData findDefaultEntity(ChartTemplateRepository repo) {
        return repo.findByDefaultTemplateTrue()
                .orElseGet(() -> {
                    ChartTemplateAggregateData builtIn = findOrCreateBuiltIn(repo);
                    clearDefaultFlags(repo);
                    builtIn.setDefaultTemplate(true);
                    return repo.save(builtIn);
                });
    }

    private ChartTemplateAggregateData findOrCreateBuiltIn(ChartTemplateRepository repo) {
        return repo.findByOrigin(ChartTemplateAggregateData.Origin.SYSTEM)
                .orElseGet(() -> createBuiltInTemplate(repo));
    }

    private ChartTemplateAggregateData createBuiltInTemplate(ChartTemplateRepository repo) {
        ChartTemplateAggregateData entity = new ChartTemplateAggregateData();
        entity.setTemplateKey(BUILT_IN_TEMPLATE_KEY);
        entity.setName(BUILT_IN_TEMPLATE_NAME);
        entity.setNameKey(normalizeNameKey(BUILT_IN_TEMPLATE_NAME));
        entity.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);
        entity.setDefaultTemplate(false);
        entity.setPayloadVersion(PAYLOAD_VERSION);
        entity.setPayloadJson(serialize(mapper.builtInPayload()));
        return repo.save(entity);
    }

    private void setBuiltInDefault(ChartTemplateRepository repo, boolean logFallback) {
        ChartTemplateAggregateData builtIn = findOrCreateBuiltIn(repo);
        clearDefaultFlags(repo);
        builtIn.setDefaultTemplate(true);
        repo.save(builtIn);
        if (logFallback)
            log.info("Reverted chart template default to the built-in template");
    }

    private void clearDefaultFlags(ChartTemplateRepository repo) {
        for (ChartTemplateAggregateData entity : repo.findAllByOrderByDefaultTemplateDescNameAsc()) {
            if (entity.isDefaultTemplate()) {
                entity.setDefaultTemplate(false);
                repo.save(entity);
            }
        }
    }

    private StoredChartTemplatePayload deserialize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank())
            return StoredChartTemplatePayload.EMPTY;
        StoredChartTemplatePayload payload = gson.fromJson(payloadJson, StoredChartTemplatePayload.class);
        return payload != null ? payload : StoredChartTemplatePayload.EMPTY;
    }

    private String serialize(StoredChartTemplatePayload payload) {
        return gson.toJson(payload);
    }

    private <T> T inTransaction(Function<ChartTemplateRepository, T> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager());
        return transactionTemplate.execute(status -> callback.apply(repository()));
    }

    private void inTransactionVoid(Consumer<ChartTemplateRepository> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager());
        transactionTemplate.executeWithoutResult(status -> callback.accept(repository()));
    }

    private ChartTemplateRepository repository() {
        return Kernel.getDefault().getApplicationContext().getBean(ChartTemplateRepository.class);
    }

    private PlatformTransactionManager transactionManager() {
        return Kernel.getDefault().getApplicationContext().getBean(PlatformTransactionManager.class);
    }

    private static String normalizeName(String name) {
        String normalized = Objects.requireNonNull(name, "name").strip();
        if (normalized.isEmpty())
            throw new IllegalArgumentException("Chart template name is blank");
        return normalized;
    }

    private static String normalizeNameKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
