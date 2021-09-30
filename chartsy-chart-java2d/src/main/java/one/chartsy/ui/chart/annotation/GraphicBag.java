package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;

import java.util.List;
import java.util.function.BiConsumer;

public interface GraphicBag {
    
    /**
     * Applies the specified method to the graphic object.
     * 
     * @param graphic
     * @param param the parameter supplied as the right argument of the manipulator method
     * @param manipulator the method manipulating the given {@code graphic} object
     */
    <A extends Annotation, P> void applyManipulator(A graphic, P param, BiConsumer<A, P> manipulator);
    
    <A extends Annotation, P> void applyManipulator(List<A> graphics, List<P> parames, BiConsumer<A, P> manipulator);
    
    /**
     * Adds the specified graphic object to the bag.
     * 
     * @param graphic
     *            the graphic object to add
     */
    void addAnnotation(Annotation graphic);
    
    /**
     * Removes the specified graphic object from the bag.
     * 
     * @param graphic
     *            the graphic object to remove
     */
    void removeAnnotation(Annotation graphic);
}
