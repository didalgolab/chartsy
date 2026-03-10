package one.chartsy.study;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PARAMETER})
@Repeatable(StudyParameter.List.class)
public @interface StudyParameter {
    String id();
    String name();
    String description() default "";
    StudyParameterScope scope();
    StudyParameterType type() default StudyParameterType.AUTO;
    Class<?> valueType() default Void.class;
    Class<?> enumType() default Void.class;
    String defaultValue();
    int order() default Integer.MAX_VALUE;
    StudyStereotype stereotype() default StudyStereotype.NONE;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.PARAMETER})
    @interface List {
        StudyParameter[] value();
    }
}
