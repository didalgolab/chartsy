/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.annotation;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.CoordinateSystem;
import one.chartsy.ui.chart.OrganizedViewInteractorContext;
import one.chartsy.ui.chart.Selection;

/**
 * The container class used to store and manipulate {@code Annotation} graphic objects displayed in one or more {@code AnnotationPanel}'s.
 * 
 * 
 * @author Mariusz Bernacki
 */
// TODO
public class GraphicLayer implements DrawingLayer {
    /** Provides the ID generator for newly created annotations. */
    protected static final AtomicLong keySequence = new AtomicLong();
    /** Holds the annotations contained in this model. */
    protected volatile Map<Annotation, Long> annotations;
    
    private final Set<Annotation> modifications = new HashSet<>();
    /** The model that contains this layer. */
    private GraphicModel model;
    
    
    
    public GraphicLayer() {
        this(new IdentityHashMap<>());
    }
    
    protected GraphicLayer(Map<Annotation, Long> annotations) {
        this.annotations = annotations;
    }
    
    /**
     * Generates a next unique key for an annotation.
     * 
     * @return the next unique key
     */
    protected final long nextKey() {
        return keySequence.incrementAndGet();
    }
    
    /**
     * Returns the graphic object located at the specified point.
     * <p>
     * Returns {@code null} if no graphic object exists at the specified
     * location. If multiple graphic objects occupy the same point, the method
     * returns the graphic with the smallest bounding rectangle.
     * 
     * @param p
     *            the point in the view coordinate system
     * @param coords
     *            the coordinate system used to display the graphic object
     * @return the graphic object at the point {@code p}
     */
    public Annotation getGraphic(Point2D p, CoordinateSystem coords) {
        Annotation candidate = null;
        double candidateArea = Double.POSITIVE_INFINITY;
        double area;
        for (Annotation obj : annotations.keySet())
            if (obj.contains(p, coords) && (area = areaOf(obj.getBoundingBox(coords))) < candidateArea) {
                candidate = obj;
                candidateArea = area;
            }
        
        return candidate;
    }
    
    /**
     * A helper method to calculate the area in pixels of the specified
     * rectangle.
     */
    private static double areaOf(Rectangle2D r) {
        return r.getWidth() * r.getHeight();
    }
    
    protected <A extends Annotation, P> void applyManipulatorInBulkUpdate(A graphic, P parameter, BiConsumer<A, P> manipulator) {
        try {
            // unwrap the annotation graphic if possible
            Annotation graphic0 = (graphic instanceof Selection)? ((Selection) graphic).getGraphic() : graphic;
            
            // check if bulk update for the graphic exists
            if (!bulkUpdates.containsKey(graphic0))
                bulkUpdates.put(graphic0, graphic.copy());
            
            // schedule repaint using annotation state before update
            bulkUpdateContext.repaint(graphic);
            
            // apply manipulator to the graphic
            manipulator.accept(graphic, parameter);
            
        } finally {
            // schedule repaint using annotation state after update
            bulkUpdateContext.repaint(graphic);
        }
    }
    
    protected <A extends Annotation, P> void applyManipulatorInBulkUpdate(List<A> graphics, List<P> parameters, BiConsumer<A, P> manipulator) {
        for (int i = 0; i < graphics.size(); i++) {
            A graphic = graphics.get(i);
            
            // unwrap the annotation graphic if possible
            Annotation graphic0 = (graphic instanceof Selection)? ((Selection) graphic).getGraphic() : graphic;
            
            // check if bulk update for the graphic exists
            if (!bulkUpdates.containsKey(graphic0))
                bulkUpdates.put(graphic0, graphic.copy());
            
            // schedule repaint using annotation state before update
            bulkUpdateContext.repaint(graphic);
            
            // apply manipulator to the graphic
            manipulator.accept(graphic, paramAt(i, parameters));
            
            // schedule repaint using annotation state after update
            bulkUpdateContext.repaint(graphic);
        }
    }
    
    public <A extends Annotation, P> void applyManipulator(A graphic, P parameter, BiConsumer<A, P> manipulator) {
        int listenerCount = getModel().getGraphicModelListeners().getListenerCount();
        
        // handle the simplest case, when no listeners are attached
        if (listenerCount == 0) {
            manipulator.accept(graphic, parameter);
            return;
        }
        
        // handle the bulk update, if it's in progress
        if (isInBulkUpdate()) {
            applyManipulatorInBulkUpdate(graphic, parameter, manipulator);
            return;
        }
        
        // handle general case
        getModelListenersProxy().graphicWillUpdate(graphic);
        manipulator.accept(graphic, parameter);
        modifyAnnotation(graphic);
    }
    
    public <A extends Annotation, P> void applyManipulator(List<A> graphics, List<P> parameters, BiConsumer<A, P> manipulator) {
        int count = graphics.size();
        if (parameters != null && parameters.size() != count)
            throw new IllegalArgumentException(
                    "Parameter list size `" + parameters.size() + "` does not match graphic count `" + count + "`");
        
        // handle the simplest case, when no listeners are attached
        int listenerCount = getModel().getGraphicModelListeners().getListenerCount();
        if (listenerCount == 0) {
            for (int i = 0; i < count; i++)
                manipulator.accept(graphics.get(i), paramAt(i, parameters));
            return;
        }
        
        // handle the bulk update, if it's in progress
        if (isInBulkUpdate()) {
            applyManipulatorInBulkUpdate(graphics, parameters, manipulator);
            return;
        }
        
        // handle general case
        for (int i = 0; i < count; i++) {
            A graphic = graphics.get(i);
            
            getModelListenersProxy().graphicWillUpdate(graphic);
            manipulator.accept(graphic, paramAt(i, parameters));
            modifyAnnotation(graphic);
        }
    }
    
    private static <P> P paramAt(int index, List<P> parameters) {
        return (parameters == null)? null : parameters.get(index);
    }
    
    protected boolean isVisible(Long key) {
        return true;
    }
    
    public void endBulkUpdateContext() {
        this.bulkUpdateContext = null;
        
        // check if there are any uncommitted changes
        if (bulkUpdates.isEmpty())
            return;
        
        boolean smallBulk = (bulkUpdates.size() <= SMALL_BULK_THRESHOLD);
        Iterator<Annotation> iter = bulkUpdates.keySet().iterator();
        while (iter.hasNext()) {
            Annotation graphic = iter.next();
            
            // repaint change if the bulk is small enough
            if (smallBulk) {
                Annotation oldGraphic = bulkUpdates.get(graphic);
                if (oldGraphic != null)
                    getModelListenersProxy().graphicUpdated(graphic, oldGraphic);
                else
                    getModelListenersProxy().graphicCreated(graphic);
            }
            iter.remove();
        }
        if (!smallBulk)
            getModelListenersProxy().graphicContentChanged();
    }
    
    /**
     * The threshold below (or equal) which the bulk is small enough to repaint
     * each update separately, instead of repaint the entire view.
     */
    public static final int SMALL_BULK_THRESHOLD = 3;
    
    public boolean isInBulkUpdate() {
        return (bulkUpdateContext != null);
    }
    
    @Override
    public void addAnnotation(Annotation graphic) {
        // add the annotation to the model and list of unsaved changes
        annotations.merge(graphic, nextKey(), (oldId, newId) -> oldId);
        
        // attach annotation to the bag
        graphic.setGraphicBag(getModel());
        
        // notify listeners
        if (!isInBulkUpdate()) {
            getModel().getGraphicModelListeners().fire().graphicCreated(graphic);
        } else {
            if (!bulkUpdates.containsKey(graphic))
                bulkUpdates.put(graphic, graphic.copy());
            bulkUpdateContext.repaint(graphic);
        }
    }
    
    public void modifyAnnotation(Annotation graphic) {
        // add the annotation to the model and list of unsaved changes
        annotations.merge(graphic, nextKey(), (oldId, newId) -> oldId);
        
        // notify listeners
        if (!isInBulkUpdate()) {
            getModelListenersProxy().graphicUpdated(graphic, graphic);
        } else {
            if (!bulkUpdates.containsKey(graphic))
                bulkUpdates.put(graphic, graphic.copy());
            bulkUpdateContext.repaint(graphic);
        }
    }
    
    public void removeAnnotation(Annotation graphic) {
        // remove the annotation from the model and the store
        annotations.remove(graphic);
        
        // detach annotation from the bag
        if (graphic.getGraphicBag() == this)
            graphic.setGraphicBag(null);
        
        // notify listeners
        getModelListenersProxy().graphicRemoved(graphic);
    }
    
    public void removeAll() {
        modifications.clear();
        if (annotations != null) {
            List<Annotation> removed = new ArrayList<>(annotations.keySet());
            annotations.clear();
            for (Annotation graphic : removed)
                graphic.setGraphicBag(null);
        }
    }
    
    public Annotation copyAndReplace(Annotation oldGraphic) {
        // create an annotation copy
        Annotation newGraphic = oldGraphic.copy();
        
        // determine the original annotation key
        Long id = annotations.remove(oldGraphic);
        if (id == null)
            id = nextKey();
        
        // replace the annotations in the model and the store
        annotations.put(newGraphic, id);
        modifications.add(newGraphic);
        return newGraphic;
    }
    
    /**
     * Repaints all the views attached with this graphic model.
     * 
     */
    public void repaintAll() {
        getModelListenersProxy().graphicContentChanged();
    }
    
    /**
     * The set containing new to old graphic updates. The <b>NEW</b> graphic is
     * a graphic containing all recent changes applied to that object. It may
     * represent an object that was initially loaded from the persistent storage
     * to the graphic model and now is constantly updated to absorb changes.
     * <p>
     * On the other case the <b>OLD</b> graphic is a copy of the graphic being
     * updated when in bulk updates mode and before the
     * {@code applyToAnnotation()} method fired changes to the original graphic.
     * The old graphic may represent a copy of the selection graphic.
     */
    protected final Map<Annotation, Annotation> bulkUpdates = new HashMap<>();
    /** The context in which bulk updates are running. */
    protected OrganizedViewInteractorContext bulkUpdateContext;
    
    
    public void startBulkUpdateContext(OrganizedViewInteractorContext context) {
        // if previous bulk context exists, close it
        if (this.bulkUpdateContext != null)
            endBulkUpdateContext();
        
        // attach new context
        this.bulkUpdateContext = context;
    }
    
    public void paint(Graphics2D g, OrganizedViewInteractorContext context, int width, int height) {
        CoordinateSystem coords = context.getCoordinateSystem();
        if (bulkUpdateContext == null || bulkUpdateContext == context || bulkUpdates.isEmpty())
            for (Annotation graphic : annotations.keySet())
                graphic.paint(g, coords, width, height);
        else
            for (Annotation graphic : annotations.keySet())
                if (!bulkUpdates.containsKey(graphic) || (graphic = bulkUpdates.get(graphic)) != null)
                    graphic.paint(g, coords, width, height);
    }
    
    /**
     * Returns the number of annotation graphics stored in this layer.
     * 
     * @return the number of annotation graphics
     */
    public int getAnnotationCount() {
        return annotations.size();
    }
    
    /**
     * Gives the model to which this layer belongs.
     * 
     * @return the model to which this layer belongs, or {@code null} if the layer
     *         hasn't been added to a model yet or has already been removed from one
     */
    public final GraphicModel getModel() {
        return model;
    }
    
    /**
     * Returns the index of the layer in the model.
     * 
     * @return the index of the layer, or {@code -1} if the layer does not currently
     *         belong to any model
     */
    public int getIndex() {
        GraphicModel model = getModel();
        if (model != null)
            for (int i = 0; i < model.getLayersCount(); i++)
                if (this == model.getLayer(i))
                    return i;
        
        return -1;
    }
    
    /**
     * Sets the model to which this layer has been just added. This method normally
     * should not be called by a custom code. It is called automatically when a
     * layer is added to the model or removed from the one. Removal of the layer
     * from a model is indicated by passing {@code null} model argument to this
     * method.
     * 
     * @param model
     *            the model to which this layer belongs, might be {@code null} in
     *            case of layer removal
     */
    protected void setModel(GraphicModel model) {
        this.model = model;
        
        // process annotations contained in this layer
        for (Annotation graphic : annotations.keySet())
            graphic.setGraphicBag(model);
    }
    
    /**
     * Checks if this layer contains the specified graphic object.
     * 
     * @param graphic
     *            the annotation graphic to check
     * @return {@code true} if the layer contains the {@code graphic}, and
     *         {@code false} otherwise
     */
    public boolean contains(Annotation graphic) {
        return annotations.containsKey(graphic);
    }
    
    protected final GraphicModelListener getModelListenersProxy() {
        GraphicModel model = getModel();
        if (model != null)
            return getModel().getGraphicModelListeners().fire();
        else
            return emptyProxyInstance();
    }
    
    private static GraphicModelListener emptyProxyInstance() {
        return (GraphicModelListener) Proxy.newProxyInstance(GraphicLayer.class.getClassLoader(), new Class<?>[] { GraphicModelListener.class }, (_1,_2,_3) -> null);
    }
}
