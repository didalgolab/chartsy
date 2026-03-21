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
            normalizeBuiltInTemplate(entity);
            entity.setDefaultTemplate(true);
            return mapper.toSummary(repo.save(entity));
        });
    }

    private LoadedTemplate toLoadedTemplate(ChartTemplateAggregateData entity) {
        if (entity.getPayloadVersion() != PAYLOAD_VERSION) {
            throw new IllegalStateException("Unsupported chart template payload version "
                    + entity.getPayloadVersion() + " for `" + entity.getName() + '`');
        }
        ChartTemplateSummary summary = mapper.toSummary(entity);
        StoredChartTemplatePayload payload = deserialize(summary.name(), entity.getPayloadJson());
        ChartTemplate chartTemplate = mapper.toChartTemplate(summary.name(), payload);
        return new LoadedTemplate(summary, chartTemplate, payload);
    }

    private void ensureInitialized(ChartTemplateRepository repo) {
        ChartTemplateAggregateData builtIn = findOrCreateBuiltIn(repo);
        findDefaultEntity(repo, builtIn);
    }

    private ChartTemplateAggregateData findDefaultEntity(ChartTemplateRepository repo) {
        ChartTemplateAggregateData builtIn = findOrCreateBuiltIn(repo);
        return findDefaultEntity(repo, builtIn);
    }

    private ChartTemplateAggregateData findDefaultEntity(ChartTemplateRepository repo, ChartTemplateAggregateData builtIn) {
        ChartTemplateAggregateData effectiveDefault = null;
        boolean normalizedDuplicates = false;
        for (ChartTemplateAggregateData entity : repo.findAllByOrderByDefaultTemplateDescNameAsc()) {
            if (!entity.isDefaultTemplate())
                continue;
            if (effectiveDefault == null) {
                effectiveDefault = entity;
                continue;
            }

            entity.setDefaultTemplate(false);
            repo.save(entity);
            normalizedDuplicates = true;
        }
        if (effectiveDefault == null) {
            builtIn.setDefaultTemplate(true);
            effectiveDefault = repo.save(builtIn);
            log.info("Recovered missing default chart template by restoring the built-in template as default");
        } else if (normalizedDuplicates) {
            log.warn("Normalized duplicate default chart templates; keeping `{}` as the effective default",
                    effectiveDefault.getName());
        }
        return effectiveDefault;
    }

    private ChartTemplateAggregateData findOrCreateBuiltIn(ChartTemplateRepository repo) {
        ChartTemplateAggregateData builtIn = repo.findByTemplateKey(BUILT_IN_TEMPLATE_KEY).orElse(null);
        List<ChartTemplateAggregateData> systemTemplates = repo.findAllByOriginOrderByNameAsc(ChartTemplateAggregateData.Origin.SYSTEM);
        if (builtIn == null) {
            ChartTemplateAggregateData candidate = selectBuiltInCandidate(systemTemplates);
            if (candidate == null)
                return createBuiltInTemplate(repo);

            String recoveredFrom = candidate.getName();
            normalizeBuiltInTemplate(candidate);
            builtIn = repo.save(candidate);
            log.warn("Recovered missing canonical built-in chart template from `{}`", recoveredFrom);
        }
        if (normalizeBuiltInTemplate(builtIn)) {
            builtIn = repo.save(builtIn);
            log.warn("Normalized built-in chart template content for the built-in key");
        }

        int convertedCount = 0;
        for (ChartTemplateAggregateData entity : systemTemplates) {
            if (Objects.equals(entity.getTemplateKey(), builtIn.getTemplateKey()))
                continue;

            entity.setOrigin(ChartTemplateAggregateData.Origin.USER);
            repo.save(entity);
            convertedCount++;
        }
        if (convertedCount > 0) {
            log.warn("Converted {} unexpected SYSTEM chart template(s) to USER templates while normalizing the built-in template",
                    convertedCount);
        }
        return builtIn;
    }

    private ChartTemplateAggregateData selectBuiltInCandidate(List<ChartTemplateAggregateData> systemTemplates) {
        String builtInNameKey = normalizeNameKey(BUILT_IN_TEMPLATE_NAME);
        for (ChartTemplateAggregateData entity : systemTemplates) {
            if (builtInNameKey.equals(entity.getNameKey()))
                return entity;
        }
        return systemTemplates.isEmpty() ? null : systemTemplates.getFirst();
    }

    private ChartTemplateAggregateData createBuiltInTemplate(ChartTemplateRepository repo) {
        ChartTemplateAggregateData entity = new ChartTemplateAggregateData();
        normalizeBuiltInTemplate(entity);
        entity.setDefaultTemplate(false);
        return repo.save(entity);
    }

    private boolean normalizeBuiltInTemplate(ChartTemplateAggregateData entity) {
        boolean changed = false;
        if (!Objects.equals(entity.getTemplateKey(), BUILT_IN_TEMPLATE_KEY)) {
            entity.setTemplateKey(BUILT_IN_TEMPLATE_KEY);
            changed = true;
        }
        if (entity.getOrigin() != ChartTemplateAggregateData.Origin.SYSTEM) {
            entity.setOrigin(ChartTemplateAggregateData.Origin.SYSTEM);
            changed = true;
        }
        if (!Objects.equals(entity.getName(), BUILT_IN_TEMPLATE_NAME)) {
            entity.setName(BUILT_IN_TEMPLATE_NAME);
            changed = true;
        }
        String builtInNameKey = normalizeNameKey(BUILT_IN_TEMPLATE_NAME);
        if (!Objects.equals(entity.getNameKey(), builtInNameKey)) {
            entity.setNameKey(builtInNameKey);
            changed = true;
        }
        if (entity.getPayloadVersion() != PAYLOAD_VERSION) {
            entity.setPayloadVersion(PAYLOAD_VERSION);
            changed = true;
        }
        String builtInPayloadJson = serialize(mapper.builtInPayload());
        if (!Objects.equals(entity.getPayloadJson(), builtInPayloadJson)) {
            entity.setPayloadJson(builtInPayloadJson);
            changed = true;
        }
        return changed;
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

    private StoredChartTemplatePayload deserialize(String templateName, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank())
            return StoredChartTemplatePayload.EMPTY;
        try {
            StoredChartTemplatePayload payload = gson.fromJson(payloadJson, StoredChartTemplatePayload.class);
            return payload != null ? payload : StoredChartTemplatePayload.EMPTY;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid chart template payload for `" + templateName + '`', ex);
        }
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
