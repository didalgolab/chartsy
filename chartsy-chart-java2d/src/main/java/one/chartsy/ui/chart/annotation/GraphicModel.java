package one.chartsy.ui.chart.annotation;

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import one.chartsy.commons.event.ListenerList;
import one.chartsy.ui.chart.*;
import one.chartsy.util.Pair;

public class GraphicModel extends Graphic implements GraphicBag {
    /** The list of layers managed by this model. */
    private final List<GraphicLayer> layers = new ArrayList<>();
    /** The list of attached {@code GraphicModelListener}'s */
    private final ListenerList<GraphicModelListener> listeners = ListenerList.of(GraphicModelListener.class);
    
    /**
     * Constructs a new, initially empty graphic model.
     */
    public GraphicModel() {
    }
    
    /**
     * Constructs a new graphic model with the specified layer as the content.
     * 
     * @param layer
     *            the layer to be inserted
     */
    public GraphicModel(GraphicLayer layer) {
        addLayer(0, layer);
    }
    
    /**
     * Inserts the given {@code layer} at the specified position in this model.
     * <p>
     * If a bad index is given, the layer will be inserted at the end.
     * 
     * @param index
     *            the position at which the layer is to be inserted
     * @param layer
     *            the layer to be inserted
     */
    public void addLayer(int index, GraphicLayer layer) {
        layer.setModel(this);
        try {
            layers.add(index, layer);
        } catch (IndexOutOfBoundsException e) {
            layers.add(layer);
        }
    }
    
    /**
     * Adds the specified layer to the end of this model layers list.
     * 
     * @param layer
     *            the layer to add
     */
    public void addLayer(GraphicLayer layer) {
        addLayer(layers.size(), layer);
    }
    
    /**
     * Returns number of layers in this graphic model.
     * 
     * @return the number of layers
     */
    public int getLayersCount() {
        return layers.size();
    }
    
    /**
     * Returns the graphic layer at the specified position.
     * 
     * @param index
     *            the index of the layer
     * @return the graphic layer at {@code index}
     * @throws IndexOutOfBoundsException
     *             when bad {@code index} is specified
     */
    public GraphicLayer getLayer(int index) {
        return layers.get(index);
    }
    
    /**
     * Gives the layer which contains the specified graphic object. The method
     * returns {@code null} if this model does not contain the given graphic at all.
     * 
     * @param graphic
     *            the annotation graphic to search for
     * @return the layer to which the {@code graphic} belongs, or {@code null}
     *         otherwise
     */
    public GraphicLayer getLayer(Annotation graphic) {
        for (GraphicLayer layer : layers)
            if (layer.contains(graphic))
                return layer;
        
        return null;
    }
    
    /**
     * Returns the number of annotation graphics stored in the model. The method
     * sums the number of graphics in all layers that belong to this model.
     * 
     * @return the total number of graphics in all layers
     */
    public int getAnnotationCount() {
        int total = 0;
        for (GraphicLayer layer : layers)
            total += layer.getAnnotationCount();
        
        return total;
    }
    
    /**
     * Attaches a new listener to this graphic model.
     * 
     * @param listener
     *            the model listener to add
     */
    public void addGraphicModelListener(GraphicModelListener listener) {
        listeners.addListener(listener);
    }
    
    /**
     * Detaches the specified listener from this model.
     * 
     * @param listener
     *            the model listener to remove
     */
    public void removeGraphicModelListener(GraphicModelListener listener) {
        listeners.removeListener(listener);
    }
    
    @Override
    public <A extends Annotation, P> void applyManipulator(A graphic, P parameter, BiConsumer<A, P> manipulator) {
        GraphicLayer layer = getLayer(graphic);
        if (layer == null && graphic instanceof Selection)
            layer = getLayer(((Selection) graphic).getGraphic());
        if (layer != null)
            layer.applyManipulator(graphic, parameter, manipulator);
    }
    
    @Override
    public <A extends Annotation, P> void applyManipulator(List<A> graphics, List<P> params, BiConsumer<A, P> manipulator) {
        int count = graphics.size();
        if (params != null && params.size() != count)
            throw new IllegalArgumentException(
                    "Parameter list size `" + params.size() + "` does not match graphic count `" + count + "`");
        
        // split graphics and parameters per layer
        GraphicLayer currLayer = null;
        Map<GraphicLayer, Pair<List<A>, List<P>>> allLayers = null;
        for (int i = 0; i < count; i++) {
            A graphic = graphics.get(i);
            GraphicLayer layer = getLayer(graphic);
            if (layer == null && graphic instanceof Selection)
                layer = getLayer(((Selection) graphic).getGraphic());
            if (layer != null) {
                if (currLayer == null)
                    currLayer = layer;
                else if (allLayers == null && currLayer != layer) {
                    allLayers = new IdentityHashMap<>();
                    allLayers.put(currLayer, Pair.of(new ArrayList<>(graphics.subList(0, i)),
                            (params == null) ? null : new ArrayList<>(params.subList(0, i))));
                }
                
                // add the current element to the map, conditionally
                if (allLayers != null) {
                    if (!allLayers.containsKey(layer))
                        allLayers.put(layer, Pair.of(new ArrayList<>(), new ArrayList<>()));
                    Pair<List<A>, List<P>> value = allLayers.get(layer);
                    value.getLeft().add(graphic);
                    if (params != null)
                        value.getRight().add(params.get(i));
                }
            }
        }
        
        // finally proceed with the apply method for each layer
        if (allLayers != null)
            allLayers.forEach((layer, value) -> layer.applyManipulator(value.getLeft(), value.getRight(), manipulator));
        else if (currLayer != null)
            currLayer.applyManipulator(graphics, params, manipulator);
    }
    
    @Override
    public void addAnnotation(Annotation graphic) {
        if (layers.isEmpty())
            layers.add(new GraphicLayer());
        
        layers.get(layers.size() - 1).addAnnotation(graphic);
    }
    
    @Override
    public void removeAnnotation(Annotation graphic) {
        GraphicLayer layer = getLayer(graphic);
        if (layer != null)
            layer.removeAnnotation(graphic);
    }
    
    /**
     * Removes the graphic layer at the specified position.
     * 
     * @param index
     *            the index of the layer
     * @throws IndexOutOfBoundsException
     *             when bad {@code index} is specified
     */
    public void removeLayer(int index) {
        layers.remove(index).setModel(null);
    }
    
    /**
     * Removes all layers from the model.
     */
    public void removeAllLayers() {
        int index = layers.size();
        while (--index >= 0)
            removeLayer(index);
    }
    
    public void paint(Graphics2D g2, OrganizedViewInteractorContext context, int width, int height) {
        for (GraphicLayer layer : layers)
            layer.paint(g2, context, width, height);
    }
    
    public void removeAll() {
        for (GraphicLayer layer : layers)
            layer.removeAll();
    }
    
    public Annotation getGraphic(Point p, CoordinateSystem coords) {
        int index = layers.size();
        while (--index >= 0) {
            Annotation graphic = layers.get(index).getGraphic(p, coords);
            if (graphic != null)
                return graphic;
        }
        return null;
    }
    
    ListenerList<GraphicModelListener> getGraphicModelListeners() {
        return listeners;
    }
}
