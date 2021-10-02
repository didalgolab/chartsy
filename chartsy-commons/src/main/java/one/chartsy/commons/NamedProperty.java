package one.chartsy.commons;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface NamedProperty<V, O> {
    
    class FromField<V, O> implements NamedProperty<V, O> {
        
        private final String name;
        
        private final Field field;
        
        
        FromField(String name, Field field) {
            this.name = name;
            this.field = field;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public Class<V> getType() {
            return (Class<V>) field.getType();
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public V getValue(O from) throws IllegalArgumentException, IllegalAccessException {
            return (V) field.get(from);
        }
        
        @Override
        public void setValue(O obj, V value) throws IllegalArgumentException, IllegalAccessException {
            field.set(obj, value);
        }
    }
    
    class MessageFormatProperty<O> implements NamedProperty<Map<String, Object>, O> {
        
        private final String name;
        
        private final NamedProperty<MessageFormat, O> formatProperty;
        
        
        public MessageFormatProperty(String name, NamedProperty<MessageFormat, O> formatProperty) {
            this.name = name;
            this.formatProperty = formatProperty;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public Class<?> getType() {
            return formatProperty.getType();
        }
        
        @Override
        public Map<String, Object> getValue(O from) throws IllegalArgumentException, IllegalAccessException {
            MessageFormat format = formatProperty.getValue(from);
            return Collections.singletonMap("pattern", format.toPattern());
        }
        
        @Override
        public void setValue(O obj, Map<String, Object> value) throws IllegalArgumentException, IllegalAccessException {
            MessageFormat format = formatProperty.getValue(obj);
            if (format != null) {
                if (value.get("pattern") instanceof String)
                    format.applyPattern(value.get("pattern").toString());
            }
        }
    }
    
    String getName();
    
    Class<?> getType();
    
    V getValue(O from) throws IllegalArgumentException, IllegalAccessException;
    
    void setValue(O obj, V value) throws IllegalArgumentException, IllegalAccessException;
    
    
    static <V, O> NamedProperty<V, O> from(String name, Field field) {
        if (MessageFormat.class.isAssignableFrom(field.getType()))
            return new MessageFormatProperty(name, new FromField<>(name, field));
        
        return new FromField<>(name, field);
    }
    
    static <V, O> NamedProperty<V, O> from(String name, Class<V> type, Function<O, V> getter, BiConsumer<O, V> setter) {
        return new NamedProperty<V, O>() {
            @Override
            public String getName() {
                return name;
            }
            
            @Override
            public Class<V> getType() {
                return (Class<V>) type;
            }
            
            @Override
            public V getValue(O from) throws IllegalArgumentException {
                return getter.apply(from);
            }
            
            @Override
            public void setValue(O obj, V value) throws IllegalArgumentException {
                setter.accept(obj, value);
            }
        };
    }
}
