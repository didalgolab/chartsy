package one.chartsy.study;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface StudyAxis {
    double min() default Double.NaN;
    double max() default Double.NaN;
    boolean logarithmic() default false;
    boolean includeInRange() default true;
    double[] steps() default {};
}
