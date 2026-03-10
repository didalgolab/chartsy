package one.chartsy.study;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ChartStudy {
    String id() default "";
    String name();
    String label();
    String category();
    StudyKind kind();
    StudyPlacement placement();
    Class<?> implementation() default Void.class;
    Class<? extends StudyPresentationBuilder> builder() default StudyPresentationBuilder.class;
}
