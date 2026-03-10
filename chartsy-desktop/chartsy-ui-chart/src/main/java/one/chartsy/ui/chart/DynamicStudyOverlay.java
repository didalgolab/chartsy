package one.chartsy.ui.chart;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyParameterDescriptor;
import one.chartsy.study.StudyPresentationPlan;
import one.chartsy.study.StudyStereotype;
import one.chartsy.ui.chart.internal.ChartPluginParameter;
import one.chartsy.ui.chart.internal.ChartPluginParameterSource;
import one.chartsy.ui.chart.internal.DefaultChartPluginParameter;
import one.chartsy.ui.chart.internal.StudyParameterSupport;
import one.chartsy.ui.chart.internal.StudyPresentationFactory;
import one.chartsy.ui.chart.internal.StudySeriesEvaluator;

import java.beans.PropertyEditor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

public class DynamicStudyOverlay extends Overlay implements StudyBackedChartPlugin, ChartPluginParameterSource {
    private final String descriptorId;
    private final LinkedHashMap<String, Object> parameterValues;
    private final LinkedHashMap<String, Object> defaultParameterValues;
    private boolean markerVisibility;
    private transient StudyPresentationPlan presentationPlan;
    private transient StudyDescriptor descriptor;

    public DynamicStudyOverlay(StudyDescriptor descriptor) {
        this(descriptor, StudyParameterSupport.createDefaultUiParameters(descriptor), false);
    }

    private DynamicStudyOverlay(StudyDescriptor descriptor,
                                Map<String, ?> parameterValues,
                                boolean markerVisibility) {
        super(Objects.requireNonNull(descriptor, "descriptor").name());
        this.descriptorId = descriptor.id();
        this.descriptor = descriptor;
        this.defaultParameterValues = StudyParameterSupport.createDefaultUiParameters(descriptor);
        this.parameterValues = StudyParameterSupport.copyUiParameters(descriptor, parameterValues);
        this.markerVisibility = markerVisibility;
        this.presentationPlan = StudyPresentationPlan.empty(descriptor.axis());
    }

    @Override
    public String getLabel() {
        return StudyParameterSupport.formatLabel(getStudyDescriptor().label(), getStudyDescriptor(), parameterValues);
    }

    @Override
    public Overlay newInstance() {
        return new DynamicStudyOverlay(getStudyDescriptor(), parameterValues, markerVisibility);
    }

    @Override
    public void calculate() {
        clearPlots();
        CandleSeries dataset = getDataset();
        StudyDescriptor descriptor = getStudyDescriptor();
        if (dataset == null) {
            presentationPlan = StudyPresentationPlan.empty(descriptor.axis());
            return;
        }

        var outputs = StudySeriesEvaluator.evaluate(descriptor, dataset, parameterValues).outputs();
        presentationPlan = StudyPresentationFactory.createPlan(descriptor, dataset, parameterValues, outputs);
        for (var entry : StudyPresentationFactory.createPlots(presentationPlan)) {
            addPlot(entry.key(), entry.plot());
        }
    }

    @Override
    public boolean getMarkerVisibility() {
        return markerVisibility;
    }

    @Override
    public Range getRange(ChartContext cf) {
        return StudyPresentationFactory.applyAxis(super.getRange(cf), resolveAxis());
    }

    @Override
    public boolean isIncludedInRange() {
        return resolveAxis().includeInRange();
    }

    @Override
    public String getStudyDescriptorId() {
        return descriptorId;
    }

    @Override
    public StudyDescriptor getStudyDescriptor() {
        if (descriptor == null)
            descriptor = StudyRegistry.getDefault().getDescriptor(descriptorId);
        return descriptor;
    }

    @Override
    public SequencedMap<String, Object> getStudyParameterValues() {
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(parameterValues));
    }

    @Override
    public StudyPresentationPlan getStudyPresentationPlan() {
        return presentationPlan != null ? presentationPlan : StudyPresentationPlan.empty(getStudyDescriptor().axis());
    }

    @Override
    public List<? extends ChartPluginParameter> getChartPluginParameters() {
        List<ChartPluginParameter> parameters = new ArrayList<>();
        parameters.add(new DefaultChartPluginParameter(
                "markerVisibility",
                "Marker Visibility",
                "Sets the Marker Visibility",
                Boolean.class,
                ChartPlugin.Stereotype.NONE,
                PropertyEditor.class,
                () -> markerVisibility,
                value -> markerVisibility = (Boolean) value,
                false
        ));
        for (StudyParameterDescriptor parameter : getStudyDescriptor().parameters().values()) {
            parameters.add(new DefaultChartPluginParameter(
                    parameter.id(),
                    parameter.name(),
                    parameter.description(),
                    StudyParameterSupport.uiValueType(parameter),
                    toChartStereotype(parameter.stereotype()),
                    PropertyEditor.class,
                    () -> parameterValues.get(parameter.id()),
                    value -> parameterValues.put(parameter.id(), StudyParameterSupport.coerceUiValue(parameter, value)),
                    defaultParameterValues.get(parameter.id())
            ));
        }
        return List.copyOf(parameters);
    }

    private StudyAxisDescriptor resolveAxis() {
        return getStudyPresentationPlan().axis();
    }

    private static ChartPlugin.Stereotype toChartStereotype(StudyStereotype stereotype) {
        return stereotype == StudyStereotype.TRANSPARENCY
                ? ChartPlugin.Stereotype.TRANSPARENCY
                : ChartPlugin.Stereotype.NONE;
    }
}
