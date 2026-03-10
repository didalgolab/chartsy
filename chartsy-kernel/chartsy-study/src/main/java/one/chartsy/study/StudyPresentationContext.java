package one.chartsy.study;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

public interface StudyPresentationContext {
    StudyDescriptor descriptor();

    Object dataset();

    SequencedMap<String, Object> parameters();

    SequencedMap<String, Object> outputs();

    default <T> T dataset(Class<T> type) {
        return type.cast(dataset());
    }

    default <T> T parameter(String id, Class<T> type) {
        return type.cast(parameters().get(id));
    }

    default Object parameter(String id) {
        return parameters().get(id);
    }

    default <T> T output(String id, Class<T> type) {
        return type.cast(outputs().get(id));
    }

    default Object output(String id) {
        return outputs().get(id);
    }

    static StudyPresentationContext of(StudyDescriptor descriptor,
                                       Object dataset,
                                       Map<String, ?> parameters,
                                       Map<String, ?> outputs) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new DefaultStudyPresentationContext(descriptor, dataset, parameters, outputs);
    }

    record DefaultStudyPresentationContext(
            StudyDescriptor descriptor,
            Object dataset,
            SequencedMap<String, Object> parameters,
            SequencedMap<String, Object> outputs
    ) implements StudyPresentationContext {
        public DefaultStudyPresentationContext(StudyDescriptor descriptor,
                                               Object dataset,
                                               Map<String, ?> parameters,
                                               Map<String, ?> outputs) {
            this(
                    Objects.requireNonNull(descriptor, "descriptor"),
                    dataset,
                    immutableCopy(parameters),
                    immutableCopy(outputs)
            );
        }

        private static SequencedMap<String, Object> immutableCopy(Map<String, ?> source) {
            var map = new LinkedHashMap<String, Object>();
            if (source != null)
                source.forEach(map::put);
            return Collections.unmodifiableSequencedMap(map);
        }
    }
}
