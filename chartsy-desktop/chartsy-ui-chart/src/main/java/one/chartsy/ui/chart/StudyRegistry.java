package one.chartsy.ui.chart;

import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyDescriptorProvider;
import one.chartsy.study.StudyKind;
import org.openide.util.Lookup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.ServiceLoader;

public final class StudyRegistry {
    private volatile Snapshot snapshot;

    public static StudyRegistry getDefault() {
        return Holder.INSTANCE;
    }

    private StudyRegistry() {
    }

    public StudyDescriptor getDescriptor(String descriptorId) {
        StudyDescriptor descriptor = snapshot().descriptorsById().get(descriptorId);
        if (descriptor == null)
            throw new IllegalArgumentException("Unknown study descriptor: " + descriptorId);
        return descriptor;
    }

    public List<StudyDescriptor> getStudyDescriptors() {
        return List.copyOf(snapshot().descriptorsById().values());
    }

    public Indicator getIndicator(String name) {
        return instantiate(resolveIndicatorPrototype(snapshot(), name));
    }

    public List<Indicator> getIndicatorsList() {
        return instantiate(snapshotIndicators(snapshot()).getPlugins());
    }

    public List<String> getIndicators() {
        return snapshotIndicators(snapshot()).getNames();
    }

    public Overlay getOverlay(String name) {
        return instantiate(resolveOverlayPrototype(snapshot(), name));
    }

    public List<Overlay> getOverlaysList() {
        return instantiate(snapshotOverlays(snapshot()).getPlugins());
    }

    public List<String> getOverlays() {
        return snapshotOverlays(snapshot()).getNames();
    }

    private static SequencedMap<String, StudyDescriptor> loadDescriptors(Iterable<? extends StudyDescriptorProvider> providers) {
        var descriptors = new LinkedHashMap<String, StudyDescriptor>();
        for (StudyDescriptorProvider provider : providers) {
            for (StudyDescriptor descriptor : provider.getStudyDescriptors()) {
                StudyDescriptor existing = descriptors.putIfAbsent(descriptor.id(), descriptor);
                if (existing != null)
                    throw new IllegalStateException("Duplicate study descriptor id: " + descriptor.id());
            }
        }
        return Collections.unmodifiableSequencedMap(descriptors);
    }

    private static List<StudyDescriptorProvider> loadDescriptorProviders() {
        var providersByType = new LinkedHashMap<String, StudyDescriptorProvider>();
        registerDescriptorProviders(providersByType, Lookup.getDefault().lookupAll(StudyDescriptorProvider.class));
        registerDescriptorProviders(providersByType, ServiceLoader.load(StudyDescriptorProvider.class, StudyRegistry.class.getClassLoader()));

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader != StudyRegistry.class.getClassLoader()) {
            registerDescriptorProviders(providersByType, ServiceLoader.load(StudyDescriptorProvider.class, contextClassLoader));
        }
        return List.copyOf(providersByType.values());
    }

    private static void registerDescriptorProviders(
            LinkedHashMap<String, StudyDescriptorProvider> providersByType,
            Iterable<? extends StudyDescriptorProvider> providers) {
        for (StudyDescriptorProvider provider : providers) {
            if (provider != null)
                providersByType.putIfAbsent(provider.getClass().getName(), provider);
        }
    }

    private static List<Indicator> createGeneratedIndicators(Iterable<StudyDescriptor> descriptors) {
        var pluginsByName = new LinkedHashMap<String, Indicator>();
        for (StudyDescriptor descriptor : descriptors) {
            if (descriptor.kind() == StudyKind.INDICATOR)
                pluginsByName.put(descriptor.name(), new DynamicStudyIndicator(descriptor));
        }
        return List.copyOf(pluginsByName.values());
    }

    private static List<Overlay> createGeneratedOverlays(Iterable<StudyDescriptor> descriptors) {
        var pluginsByName = new LinkedHashMap<String, Overlay>();
        for (StudyDescriptor descriptor : descriptors) {
            if (descriptor.kind() == StudyKind.OVERLAY)
                pluginsByName.put(descriptor.name(), new DynamicStudyOverlay(descriptor));
        }
        return List.copyOf(pluginsByName.values());
    }

    private Snapshot snapshot() {
        List<StudyDescriptorProvider> providers = loadDescriptorProviders();
        List<String> providerTypes = providers.stream()
                .map(provider -> provider.getClass().getName())
                .toList();

        Snapshot current = snapshot;
        if (current != null && current.providerTypes().equals(providerTypes))
            return current;

        synchronized (this) {
            current = snapshot;
            if (current != null && current.providerTypes().equals(providerTypes))
                return current;

            SequencedMap<String, StudyDescriptor> descriptorsById = loadDescriptors(providers);
            current = new Snapshot(
                    providerTypes,
                    descriptorsById,
                    new ChartPluginRegistry<>(createGeneratedIndicators(descriptorsById.values())),
                    new ChartPluginRegistry<>(createGeneratedOverlays(descriptorsById.values()))
            );
            snapshot = current;
            return current;
        }
    }

    private ChartPluginRegistry<Indicator> snapshotIndicators(Snapshot snapshot) {
        return mergeWithLegacyPlugins(snapshot.generatedIndicators(), Lookup.getDefault().lookupAll(Indicator.class));
    }

    private ChartPluginRegistry<Overlay> snapshotOverlays(Snapshot snapshot) {
        return mergeWithLegacyPlugins(snapshot.generatedOverlays(), Lookup.getDefault().lookupAll(Overlay.class));
    }

    private Indicator resolveIndicatorPrototype(Snapshot snapshot, String name) {
        Indicator indicator = snapshot.generatedIndicators().get(name);
        return (indicator != null) ? indicator : findLegacyPlugin(Indicator.class, name);
    }

    private Overlay resolveOverlayPrototype(Snapshot snapshot, String name) {
        Overlay overlay = snapshot.generatedOverlays().get(name);
        return (overlay != null) ? overlay : findLegacyPlugin(Overlay.class, name);
    }

    private static <T extends ChartPlugin<?>> ChartPluginRegistry<T> mergeWithLegacyPlugins(
            ChartPluginRegistry<T> generatedPlugins,
            Iterable<? extends T> legacyPlugins) {
        var pluginsByName = new LinkedHashMap<String, T>();
        for (T plugin : generatedPlugins.getPlugins())
            pluginsByName.put(plugin.getName(), plugin);
        for (T plugin : legacyPlugins) {
            if (plugin != null)
                pluginsByName.putIfAbsent(plugin.getName(), plugin);
        }
        return new ChartPluginRegistry<>(pluginsByName.values());
    }

    private static <T extends ChartPlugin<?>> T findLegacyPlugin(Class<T> pluginType, String name) {
        for (T plugin : Lookup.getDefault().lookupAll(pluginType)) {
            if (plugin != null && plugin.getName().equals(name))
                return plugin;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends ChartPlugin<?>> T instantiate(T prototype) {
        return (prototype == null) ? null : (T) prototype.newInstance();
    }

    private static <T extends ChartPlugin<?>> List<T> instantiate(List<? extends T> prototypes) {
        return prototypes.stream()
                .map(StudyRegistry::instantiate)
                .toList();
    }

    private static final class Holder {
        private static final StudyRegistry INSTANCE = new StudyRegistry();
    }

    private record Snapshot(
            List<String> providerTypes,
            SequencedMap<String, StudyDescriptor> descriptorsById,
            ChartPluginRegistry<Indicator> generatedIndicators,
            ChartPluginRegistry<Overlay> generatedOverlays) {
    }
}
