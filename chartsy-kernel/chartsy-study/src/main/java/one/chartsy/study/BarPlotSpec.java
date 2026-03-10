package one.chartsy.study;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(BarPlotSpec.List.class)
public @interface BarPlotSpec {
    String id();
    String label();
    String output();
    String colorParameter();
    String visibleParameter() default "";
    int order() default Integer.MAX_VALUE;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface List {
        BarPlotSpec[] value();
    }
}
