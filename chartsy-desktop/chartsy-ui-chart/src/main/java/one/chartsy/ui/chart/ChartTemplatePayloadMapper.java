/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.persistence.domain.ChartTemplateAggregateData;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyKind;
import one.chartsy.ui.chart.components.ChartPluginSelection;
import one.chartsy.ui.chart.internal.ChartPluginParameter;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;
import one.chartsy.ui.chart.internal.StudyParameterSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ChartTemplatePayloadMapper {
    private static final Logger log = LogManager.getLogger(ChartTemplatePayloadMapper.class);
    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_COLOR = "COLOR";
    private static final String TYPE_DOUBLE = "DOUBLE";
    private static final String TYPE_ENUM = "ENUM";
    private static final String TYPE_INTEGER = "INTEGER";
    private static final String TYPE_STRING = "STRING";
    private static final String TYPE_STROKE = "STROKE";
    private static final String PANEL_ID = "panelId";

    private static final ChartTemplatePayloadMapper INSTANCE = new ChartTemplatePayloadMapper();

    static ChartTemplatePayloadMapper getDefault() {
        return INSTANCE;
    }

    private ChartTemplatePayloadMapper() {
    }

    StoredChartTemplatePayload fromSelection(ChartPluginSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return new StoredChartTemplatePayload(
                selection.overlays().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList(),
                selection.indicators().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList()
        );
    }

    StoredChartTemplatePayload fromChartTemplate(ChartTemplate template) {
        Objects.requireNonNull(template, "template");
        return new StoredChartTemplatePayload(
                template.getOverlays().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList(),
                template.getIndicators().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList()
        );
    }

    StoredChartTemplatePayload captureCurrentStudies(ChartFrame chartFrame) {
        Objects.requireNonNull(chartFrame, "chartFrame");
        var stackPanel = chartFrame.getMainStackPanel();
        if (stackPanel == null) {
            ChartTemplate template = chartFrame.getChartTemplate();
            return (template != null) ? fromChartTemplate(template) : StoredChartTemplatePayload.EMPTY;
        }
        return new StoredChartTemplatePayload(
                stackPanel.getChartPanel().getOverlays().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList(),
                stackPanel.getIndicatorsList().stream().map(this::toPluginSpec).filter(Objects::nonNull).toList()
        );
    }

    boolean equivalent(StoredChartTemplatePayload left, StoredChartTemplatePayload right) {
        return Objects.equals(left, right);
    }

    ChartTemplate toChartTemplate(String templateName, StoredChartTemplatePayload payload) {
        ChartTemplate template = ChartTemplateDefaults.baseChartTemplate(templateName);
        for (StoredPluginSpec overlaySpec : payload.overlays()) {
            Overlay overlay = instantiateOverlay(overlaySpec);
            if (overlay != null)
                template.addOverlay(overlay);
        }
        for (StoredPluginSpec indicatorSpec : payload.indicators()) {
            Indicator indicator = instantiateIndicator(indicatorSpec);
            if (indicator != null)
                template.addIndicator(indicator);
        }
        return template;
    }

    StoredChartTemplatePayload builtInPayload() {
        return fromChartTemplate(ChartTemplateDefaults.basicChartTemplate());
    }

    ChartTemplateSummary toSummary(ChartTemplateAggregateData entity) {
        return new ChartTemplateSummary(
                entity.getTemplateKey(),
                entity.getName(),
                entity.isDefaultTemplate(),
                entity.getOrigin() == ChartTemplateAggregateData.Origin.SYSTEM
        );
    }

    AppliedChartTemplateRef toAppliedReference(ChartTemplateSummary summary) {
        return new AppliedChartTemplateRef(summary.templateKey(), summary.name(), summary.builtIn(), summary.defaultTemplate());
    }

    private StoredPluginSpec toPluginSpec(ChartPlugin<?> plugin) {
        LinkedHashMap<String, StoredParameterValue> parameters = new LinkedHashMap<>();
        for (ChartPluginParameter parameter : ChartPluginParameterUtils.getParameters(plugin)) {
            if (!parameter.canRead() || PANEL_ID.equals(parameter.id()))
                continue;

            StoredParameterValue stored = encodeParameter(parameter);
            if (stored != null)
                parameters.put(parameter.id(), stored);
        }

        String descriptorId = (plugin instanceof StudyBackedChartPlugin studyBacked)
                ? studyBacked.getStudyDescriptorId()
                : null;
        return new StoredPluginSpec(descriptorId, plugin.getClass().getName(), plugin.getName(), parameters);
    }

    private Overlay instantiateOverlay(StoredPluginSpec spec) {
        try {
            Overlay overlay = instantiateStudyBackedOverlay(spec);
            if (overlay == null)
                overlay = resolveLegacyOverlay(spec);
            if (overlay == null) {
                log.warn("Skipping unresolved overlay template entry `{}` ({})", spec.name(), spec.className());
                return null;
            }
            applyParameters(overlay, spec.parametersView());
            return overlay;
        } catch (RuntimeException ex) {
            log.warn("Skipping overlay template entry `{}` due to load failure", spec.name(), ex);
            return null;
        }
    }

    private Indicator instantiateIndicator(StoredPluginSpec spec) {
        try {
            Indicator indicator = instantiateStudyBackedIndicator(spec);
            if (indicator == null)
                indicator = resolveLegacyIndicator(spec);
            if (indicator == null) {
                log.warn("Skipping unresolved indicator template entry `{}` ({})", spec.name(), spec.className());
                return null;
            }
            applyParameters(indicator, spec.parametersView());
            return indicator;
        } catch (RuntimeException ex) {
            log.warn("Skipping indicator template entry `{}` due to load failure", spec.name(), ex);
            return null;
        }
    }

    private Overlay instantiateStudyBackedOverlay(StoredPluginSpec spec) {
        if (spec.descriptorId() == null)
            return null;
        StudyDescriptor descriptor = StudyRegistry.getDefault().getDescriptor(spec.descriptorId());
        if (descriptor == null)
            return null;
        if (descriptor.kind() != StudyKind.OVERLAY)
            return null;
        return new DynamicStudyOverlay(descriptor);
    }

    private Indicator instantiateStudyBackedIndicator(StoredPluginSpec spec) {
        if (spec.descriptorId() == null)
            return null;
        StudyDescriptor descriptor = StudyRegistry.getDefault().getDescriptor(spec.descriptorId());
        if (descriptor == null)
            return null;
        if (descriptor.kind() != StudyKind.INDICATOR)
            return null;
        return new DynamicStudyIndicator(descriptor);
    }

    private Overlay resolveLegacyOverlay(StoredPluginSpec spec) {
        for (Overlay overlay : OverlayManager.getDefault().getOverlaysList()) {
            if (matchesPlugin(overlay, spec))
                return overlay.newInstance();
        }
        return null;
    }

    private Indicator resolveLegacyIndicator(StoredPluginSpec spec) {
        for (Indicator indicator : IndicatorManager.getDefault().getIndicatorsList()) {
            if (matchesPlugin(indicator, spec))
                return indicator.newInstance();
        }
        return null;
    }

    private boolean matchesPlugin(ChartPlugin<?> plugin, StoredPluginSpec spec) {
        if (plugin == null)
            return false;
        if (spec.className() != null && spec.className().equals(plugin.getClass().getName()))
            return true;
        return spec.name().equals(plugin.getName());
    }

    private void applyParameters(ChartPlugin<?> plugin, Map<String, StoredParameterValue> parameters) {
        List<ChartPluginParameter> writableParameters = new ArrayList<>(ChartPluginParameterUtils.getParameters(plugin));
        Map<String, ChartPluginParameter> byId = new LinkedHashMap<>();
        for (ChartPluginParameter parameter : writableParameters)
            byId.put(parameter.id(), parameter);

        for (var entry : parameters.entrySet()) {
            ChartPluginParameter parameter = byId.get(entry.getKey());
            if (parameter == null || !parameter.canWrite())
                continue;
            try {
                parameter.setValue(decodeParameter(parameter, entry.getValue()));
            } catch (RuntimeException ex) {
                log.warn("Skipping template parameter `{}` for plugin `{}`", entry.getKey(), plugin.getName(), ex);
            }
        }
    }

    private StoredParameterValue encodeParameter(ChartPluginParameter parameter) {
        Object value = parameter.getValue();
        Class<?> valueType = wrap(parameter.valueType());

        return switch (value) {
            case null -> switch (normalizeType(valueType)) {
                case TYPE_STRING -> new StoredParameterValue(TYPE_STRING, "");
                default -> null;
            };
            case Color color -> new StoredParameterValue(TYPE_COLOR, StudyParameterSupport.toStudyColor(color).toHex());
            case Stroke stroke -> new StoredParameterValue(TYPE_STROKE, StudyParameterSupport.toStrokeName(stroke));
            case Boolean bool -> new StoredParameterValue(TYPE_BOOLEAN, Boolean.toString(bool));
            case Number number when TYPE_INTEGER.equals(normalizeType(valueType)) ->
                    new StoredParameterValue(TYPE_INTEGER, Long.toString(number.longValue()));
            case Number number -> new StoredParameterValue(TYPE_DOUBLE, Double.toString(number.doubleValue()));
            case Enum<?> enumValue -> new StoredParameterValue(TYPE_ENUM, enumValue.name());
            default -> {
                if (CharSequence.class.isAssignableFrom(valueType))
                    yield new StoredParameterValue(TYPE_STRING, value.toString());
                log.warn("Skipping unsupported template parameter `{}` of type `{}`", parameter.id(), valueType.getName());
                yield null;
            }
        };
    }

    private Object decodeParameter(ChartPluginParameter parameter, StoredParameterValue stored) {
        Class<?> valueType = wrap(parameter.valueType());
        return switch (stored.type().toUpperCase(Locale.ROOT)) {
            case TYPE_BOOLEAN -> Boolean.parseBoolean(stored.value());
            case TYPE_COLOR -> StudyParameterSupport.toAwtColor(stored.value());
            case TYPE_DOUBLE -> coerceFloating(valueType, stored.value());
            case TYPE_ENUM -> parseEnum(valueType, stored.value());
            case TYPE_INTEGER -> coerceIntegral(valueType, stored.value());
            case TYPE_STROKE -> StudyParameterSupport.toStroke(stored.value());
            case TYPE_STRING -> stored.value();
            default -> throw new IllegalArgumentException("Unsupported stored parameter type: " + stored.type());
        };
    }

    private static String normalizeType(Class<?> valueType) {
        if (valueType == Boolean.class)
            return TYPE_BOOLEAN;
        if (valueType == Integer.class || valueType == Long.class || valueType == Short.class || valueType == Byte.class)
            return TYPE_INTEGER;
        if (valueType == Double.class || valueType == Float.class)
            return TYPE_DOUBLE;
        if (valueType.isEnum())
            return TYPE_ENUM;
        if (Color.class.isAssignableFrom(valueType))
            return TYPE_COLOR;
        if (Stroke.class.isAssignableFrom(valueType))
            return TYPE_STROKE;
        return TYPE_STRING;
    }

    private static Object coerceFloating(Class<?> valueType, String value) {
        double parsed = Double.parseDouble(value);
        if (valueType == Float.class)
            return (float) parsed;
        return parsed;
    }

    private static Object coerceIntegral(Class<?> valueType, String value) {
        long parsed = Long.parseLong(value);
        if (valueType == Long.class)
            return parsed;
        if (valueType == Short.class)
            return (short) parsed;
        if (valueType == Byte.class)
            return (byte) parsed;
        return (int) parsed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(Class<?> valueType, String value) {
        if (!valueType.isEnum())
            return value;
        return Enum.valueOf((Class<? extends Enum>) valueType, value);
    }

    private static Class<?> wrap(Class<?> valueType) {
        if (!valueType.isPrimitive())
            return valueType;
        return switch (valueType.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> valueType;
        };
    }
}
