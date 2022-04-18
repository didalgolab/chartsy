package one.chartsy.samples.ui;

import one.chartsy.kernel.boot.FrontEnd;
import org.openide.util.Lookup;

public class FrontEndTest {

    public static void main(String[] args) {
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);

        var template = frontEnd.getApplicationContext().getBean("basicChartTemplate");
        System.out.println(template);
    }
}
