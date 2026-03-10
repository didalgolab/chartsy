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
@Repeatable(InsideFillPlotSpec.List.class)
public @interface InsideFillPlotSpec {
    String id();
    String label();
    String upperOutput();
    String lowerOutput();
    String colorParameter();
    String visibleParameter() default "";
    int order() default Integer.MAX_VALUE;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface List {
        InsideFillPlotSpec[] value();
    }
}
