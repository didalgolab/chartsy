package one.chartsy.samples.frontend;

import one.chartsy.frontend.FrontEnd;
import org.openide.util.Lookup;

public class FrontEndExample {

    public static void main(String[] args) {
        FrontEnd frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
        System.out.println(frontEnd);
    }
}
