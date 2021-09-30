/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Painter;

import one.chartsy.ui.chart.ChartPlugin.Parameter;
import one.chartsy.ui.chart.annotation.GraphicBag;
import one.chartsy.ui.chart.graphic.GraphicInteractor;
import org.openide.nodes.AbstractNode;

import one.chartsy.ui.chart.plugin.ChartPlugin.Parameter;
import one.chartsy.ui.SerializableBasicStroke;

/**
 * The base class for the annotation objects being drawn over the chart.
 * <p>
 * <h3>Persistence Support</h3>
 * Each annotation graphic is a lightweight persistence object.
 * The persistent state of an annotation graphic is represented through either
 * persistent fields or persistent properties. These fields or properties are
 * written in a binary form to the underlying stream when the annotation
 * graphic object is serialized using standard Java Serialization mechanism
 * and are read back when the annotation graphic object is restored back from the stream.
 * <h3>Requirements for Persistence Support</h3>
 * The custom annotation graphic must follow some requirements listed below in order to be seemlessly
 * handled by persistent stores. These requirements are:
 * <ul>
 * <li>The class must have public no-argument constructor. The class may have other constructors as well besides the mandatory default one.
 * </ul>
 * 
 * @author Mariusz Bernacki
 */
public abstract class Annotation implements Painter<CoordinateSystem> {
    /** The serial version UID shared by all subclasses. */
    protected static final long serialVersionUID = -5207281061379501912L;
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface Key {
        
        String value();
        /** The constant used for key line annotations. */
        String LINE = "LINE";
        /** The constant used for key rect annotations. */
        String RECT = "RECT";
        /** The constant used for key ellipse annotations. */
        String ELLIPSE = "ELLIPSE";
        
    }
    
    public static final Key Key(String key) {
        return new Key() {
            @Override
            public String value() {
                return key;
            }
            
            @Override
            public Class<Key> annotationType() {
                return Key.class;
            }
        };
    }
    
    /**
     * The anchor location constants used by the annotation classes.
     */
    public enum AnchorLocation {
        /** Represents the top left corner. */
        TOP_LEFT,
        /** Represents the top side edge. */
        TOP,
        /** Represents the top right corner. */
        TOP_RIGHT,
        /** Represents the right side edge. */
        RIGHT,
        /** Represents the bottom right corner. */
        BOTTOM_RIGHT,
        /** Represents the bottom side edge. */
        BOTTOM,
        /** Represents the bottom left corner. */
        BOTTOM_LEFT,
        /** Represents the left side edge. */
        LEFT;
        
    }
    /** The name of this annotation graphic. */
    private @Parameter(name = "name") String name;
    /** The edge form status of the annotation. */
    private @Parameter(name = "edgeForm") boolean edgeForm = true;
    /** The face form status of the annotation. */
    private @Parameter(name = "faceForm") boolean faceForm = true;
    /** The graphic bag that contains this annotation graphic. */
    private transient GraphicBag graphicBag;
    /** The object interactor associated with this annotation graphic. */
    private transient GraphicInteractor graphicInteractor;
    
    
    /**
     * Constructs a new annotation with the specified {@code name}.
     * 
     * @param name
     *            the annotation name
     */
    protected Annotation(String name) {
        this.name = Objects.requireNonNull(name, "The `name` argument cannot be NULL");
    }
    
    /**
     * Returns the name of the annotation graphic (e.g. "Line", "Rectangle",
     * etc.). By default the name matches the type of annotation graphic (e.g.
     * "Line") but it may be changed any time to anything more meaningul for a
     * user (e.g. "January Effect Trendline").
     * 
     * @return the annotation name
     * @see #setName(String)
     */
    public final String getName() {
        return name;
    }
    
    /**
     * Changes the name of the annotation graphic.
     * 
     * @param name
     *            the new annotation graphic name
     * @see #getName()
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Returns a copy of this {@code Annotation} instance.
     * <p>
     * The copy must have the same class as this one and have the same
     * persistent properties as this object.
     * <p>
     * The {@link Annotation#copy()} method provides a reasonable default
     * implementation that first instantiates a new annotation graphic object
     * using a no-argument constructor and then copies all persistent properties
     * from this annotation to the newly created annotation.
     * 
     * @return the annotation graphic copy
     */
    public Annotation copy() {
        // instantiate a new annotation using a no-argument constructor
        Annotation result;
        try {
            result = getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Cannot instantiate " + getClass().getSimpleName(), e);
        }
        
        // copy properties from this to the newly created annotation
        for (NamedProperty<Object, Annotation> property : getNamedProperties().values()) {
            try {
                property.setValue(result, property.getValue(this));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING,
                        "Cannot copy `" + property.getName() + "` parameter", e);
            }
        }
        return result;
    }
    
    /**
     * Applies a transformation to the geometry of the annotation graphic.
     * <p>
     * A transformation is represented using the given {@code transform}
     * operator that converts a set of points provided by the annotation. The
     * number of points provided by the annotation is implementation specific.
     * It depends on type of the annotation, its implementation, visual
     * representation (appearance), and complexity. For performance reasons
     * however the annotation should provide as few points as possible and
     * needed to reflect the geometry of the annotation. For example,
     * <ul>
     * <li>A simple rectangle annotation should provide two points, representing
     * the starting and ending points of one of its diagonals.
     * <li>A triangle should provide three points, representing its each corner.
     * <li>A horizontal line which is displayed across the entire user screen
     * should provide one point, representing the line's y-coordinate.
     * </ul>
     * 
     * @param transform
     *            the transformation to apply
     * @param coords
     *            the coordinate system used to display the annotation graphic
     */
    public abstract void applyTransform(UnaryOperator<Point2D> transform, CoordinateSystem coords);
    
    /**
     * Returns the bounding rectangle of this graphic object in a given
     * coordinate system.
     * <p>
     * The bounding rectangle for a graphic object is an axis-oriented rectangle
     * that completely encloses the drawing area of the object. However there is
     * no guarantee that the minimal bounding rectangle is always returned.
     * <p>
     * Since the caller of this method may modify the returned rectangle, your
     * implementation should never provide cached or shared rectangle objects
     * but always return a newly allocated instance.
     * 
     * @param coords
     *            the coordinate system used to display the object on a screen
     * @return the object's bounding rectangle
     */
    public abstract Rectangle2D getBoundingBox(CoordinateSystem coords);
    
    /**
     * Checks if the given point lies within the outline of this graphic object
     * in the given coordinate system.
     * <p>
     * The method is effectively equivalent to, for {@code this} graphic object:
     * 
     * <pre>
     *  {@code this.contains(p.getX(), p.getY(), coords)}
     * </pre>
     * 
     * @param p
     *            the point to test for insideness
     * @param coords
     *            the coordinate system used to display the object on a screen
     * @return {@code true} if the point {@code p} lies inside this graphic
     *         object
     */
    public boolean contains(Point2D p, CoordinateSystem coords) {
        return contains(p.getX(), p.getY(), coords);
    }
    
    /**
     * Checks if the point specified by given {@code (x, y)} coordinates lies
     * within the outline of this graphic object in the given coordinate system.
     * <p>
     * The default implementation tests if the points lies inside the bounding
     * rectangle of the graphic object, determined by the
     * {@link #getBoundingBox(CoordinateSystem)} method.
     * <p>
     * For a customized graphic object you should provide your own
     * implementation of the method whenever your customized graphic differs
     * from a plain, opaque and rectangular shaped object.
     * 
     * @param x
     *            the x-coordinate of the point to test
     * @param y
     *            the y-coordinate of the point to test
     * @param coords
     *            the coordinate system used to display the object on a screen
     * @return {@code true} if the {@code (x, y)} point lies inside this graphic
     *         object
     */
    public boolean contains(double x, double y, CoordinateSystem coords) {
        return getBoundingBox(coords).contains(x, y);
    }
    
    /**
     * Paints this graphic object.
     * <p>
     * The method paints on the provided graphics surface using the specified
     * coordinate system to transform object logical coordinates into a display
     * coordinate space. The implementation is not allowed to paint outside the
     * bounding rectangle of this object determined by the
     * {@link #getBoundingBox(CoordinateSystem)} method. For efficiency reasons,
     * the provided {@code Graphics} context is not <i>clipped</i> by the
     * bounding rectangle.
     * 
     * @param g
     *            the graphics surface to draw onto
     * @param coords
     *            the coordinate system used to transform into a display
     *            coordinate space
     * @param width
     *            the width of the visible screen area on which the drawing is
     *            performed
     * @param height
     *            the height of the visible screen area on which the drawing is
     *            performed
     */
    @Override
    public abstract void paint(Graphics2D g, CoordinateSystem coords, int width, int height);
    
    /**
     * Returns the view interactor that lets user to interactively create a new
     * {@code Annotation}.
     * <p>
     * Usually an instance that handles some kind of drag gestures is returned,
     * allowing the user to "draw" the desired shape of the annotation on the
     * screen by dragging the mouse. In the most simple cases when the single
     * drag gesture is enough to specify the shape of this {@code Annotation},
     * an instance of the {@link DiagonalDragInteractor} can be returned.
     * 
     * @param context
     *            the context in with the drawing operation is started
     * @return the view interactor allowing to interactively create a new
     *         annotation graphic
     */
    public OrganizedViewInteractor getDrawingInteractor(OrganizedViewInteractorContext context) {
        throw new UnsupportedOperationException("Interactive drawing not supported");
    }
    
    /**
     * Returns the graphic bag that contains this annotation graphic. Returns
     * {@code null} if this annotation is not added to any bag.
     * 
     * @return the graphic bag containing this graphic or {@code null}
     */
    public final GraphicBag getGraphicBag() {
        return graphicBag;
    }
    
    /**
     * Changes the graphic bag that contains this annotation graphic. The method
     * is called by the framework when an annotation is added or removed from a
     * bag. This method should not be called directly by a user. When overriden
     * by a user make sure to call {@code super.getGraphicBag()} to correctly
     * update the graphic bag property in a parent class.
     * 
     * @param graphicBag
     *            the graphic bag to set
     */
    public void setGraphicBag(GraphicBag graphicBag) {
        this.graphicBag = graphicBag;
    }
    
    /**
     * Returns the default interactor for this annotation graphic. The returned
     * object will be used as a default interactor by {@link #getGraphicInteractor()}
     * method if no other interactor was assigned with a
     * {@link #setGraphicInteractor(GraphicInteractor)} method. The method is
     * encouraged to return a cached instance whenever possible instead of
     * creating a fresh object instance upon each method call.
     * <p>
     * The default implementation in the
     * {@link Annotation#getDefaultInteractorType()} returns {@code null}.
     * 
     * @return the default interactor for this annotation graphic, or
     *         {@code null} when no default interactor is specified
     */
    public Class<? extends GraphicInteractor> getDefaultInteractorType() {
        return null;
    }
    
    /**
     * Returns the graphic interactor associated with the annotation graphic. If
     * no interactor is currently associated, the method invokes
     * {@link #getDefaultInteractorType()} to obtain a default interactor and if
     * not null is given it is associated with this annotation graphic.
     * 
     * @return the associated graphic interactor, otherwise {@code null}
     */
    public final GraphicInteractor getGraphicInteractor() {
        GraphicInteractor interactor = this.graphicInteractor;
        if (interactor == null) {
            try {
                setGraphicInteractor(interactor = GraphicInteractors.get(getDefaultInteractorType()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return interactor;
    }
    
    /**
     * Sets the new graphic interactor for this annotation graphic.
     * 
     * @param interactor
     *            the graphic interactor to set
     */
    public void setGraphicInteractor(GraphicInteractor interactor) {
        this.graphicInteractor = interactor;
    }
    
    /**
     * Returns the current edge form status of this annotation graphic. The
     * exact meaning of an <i>edge form</i> depends on the actual implementation
     * however it usually means that the annotation graphic visual
     * representation is <i>drawn</i> using some predefined {@link Stroke}
     * object, following the outline of the annotation graphic shape.
     * 
     * @return the new edge form status to set
     */
    public boolean isEdgeForm() {
        return edgeForm;
    }
    
    /**
     * Changes the edge form status of this annotation graphic.
     * 
     * @param edgeForm
     *            the edge form status to set
     * @see #isEdgeForm()
     */
    public void setEdgeForm(boolean edgeForm) {
        this.edgeForm = edgeForm;
    }
    
    /**
     * Returns the current edge form status of this annotation graphic. The
     * exact meaning of a <i>face form</i> depends on the actual implementation
     * however it usually means that the annotation graphic visual
     * representation is constructed by <i>filling</i> an area occupied by the
     * annotation graphic shape with some predefined {@link Paint} object.
     * 
     * @return the faceForm
     */
    public boolean isFaceForm() {
        return faceForm;
    }
    
    /**
     * Changes the face form status of this annotation graphic.
     * 
     * @param faceForm
     *            the face form to set
     * @see #isFaceForm()
     */
    public void setFaceForm(boolean faceForm) {
        this.faceForm = faceForm;
    }
    
    /**
     * Called by the framework to create a selection object for this graphic
     * object.
     * <p>
     * The default implementation creates an instance of the
     * {@link RectangularSelection}. You may override this method in your
     * customized graphic to provide a different selection object.
     * 
     * @return the selection object for this graphic
     */
    public Selection makeSelection() {
        return new RectangularSelection(this);
    }
    
    protected Map<String, NamedProperty<Object, Annotation>> getNamedProperties() {
        Map<String, NamedProperty<Object, Annotation>> result = new HashMap<>();
        
        Class<?> clazz = getClass();
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Parameter.class))
                    continue;
                if (Modifier.isTransient(field.getModifiers()))
                    continue;
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);
                String name = field.getAnnotation(Parameter.class).name();
                if (name.isEmpty())
                    continue;
                result.put(name, NamedProperty.from(name, field));
            }
        } while ((clazz = clazz.getSuperclass()) != Object.class);
        
        return result;
    }
    
    public AbstractNode getNode() {
        return new AnnotationNode(this);
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        Map<String, NamedProperty<Object, Annotation>> properties = getNamedProperties();
        for (NamedProperty<?, Annotation> property : properties.values()) {
            try {
                String name = property.getName();
                if (name.length() == 0)
                    continue;
                Object value = writeReplace(property.getValue(this));
                out.writeUTF(name);
                out.writeObject(value);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        out.writeUTF("");
    }
    
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        Map<String, NamedProperty<Object, Annotation>> properties = getNamedProperties();
        
        while (true) {
            String name = in.readUTF();
            if (name.equals(""))
                break;
            
            Object obj = in.readObject();
            NamedProperty<Object, Annotation> property = properties.get(name);
            if (property != null) {
                try {
                    property.setValue(this, obj);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    protected Object writeReplace(Object value) throws IOException {
        if (value == null)
            return value;
        if (value instanceof Serializable || value instanceof Externalizable)
            return value;
        if (value.getClass() == BasicStroke.class)
            return new SerializableBasicStroke((BasicStroke) value);
        
        // TODO
        return null;
    }
}
