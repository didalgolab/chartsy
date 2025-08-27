/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.runner;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class PolyAwareUpdaterTest {

    // ----- Model (no Jackson annotations) -----

    public static abstract class Widget { public String id; }

    public static final class Panel extends Widget {
        public Style style = new Style();
        public Widget child;
    }

    public static final class Label extends Widget { public String text; }

    public static final class Style { public String color; public int padding; }

    // ----- Polymorphic-aware generic updater -----

    public static final class PolyAwareUpdater {
        private final ObjectMapper mapper;
        private final String typeProp;

        public PolyAwareUpdater(ObjectMapper mapper, String typeProp) {
            this.mapper = mapper;
            this.typeProp = typeProp;
        }

        public void applyPatch(Object target, String patchJson) throws Exception {
            JsonNode patch = mapper.readTree(patchJson);
            if (!patch.isObject()) {
                throw new IllegalArgumentException("Patch root must be a JSON object");
            }
            applyObjectPatch(target, (ObjectNode) patch);
        }

        private void applyObjectPatch(Object target, ObjectNode patchObj) throws Exception {
            var fields = patchObj.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                String name = e.getKey();
                JsonNode pval = e.getValue();

                Field f = findField(target.getClass(), name);
                if (f == null) {
                    // Unknown property in target type: skip or throw based on policy
                    continue;
                }
                f.setAccessible(true);
                Class<?> declaredType = f.getType();
                Object current = f.get(target);

                if (pval.isNull()) {
                    f.set(target, null);
                    continue;
                }

                if (pval.isObject()) {
                    // Replacement if @class present
                    if (pval.has(typeProp)) {
                        Object newVal = mapper.readValue(pval.traverse(mapper), Object.class);
                        // Optional: validate assignability
                        if (newVal != null && !declaredType.isInstance(newVal) && declaredType != Object.class) {
                            throw new IllegalArgumentException("Type mismatch for field '" + name + "'");
                        }
                        f.set(target, newVal);
                    } else {
                        // Update-in-place when existing instance is available
                        if (current != null) {
                            mapper.readerForUpdating(current).readValue(pval.traverse(mapper));
                            // f already points to 'current', which we just mutated
                        } else {
                            // No existing instance to update. Either:
                            // - require @class, or
                            // - instantiate a default if declaredType is concrete.
                            if (declaredType.isInterface() || java.lang.reflect.Modifier.isAbstract(declaredType.getModifiers())) {
                                throw new IllegalArgumentException(
                                        "Field '" + name + "' is null and abstract; patch must include '" + typeProp + "'.");
                            }
                            Object newInstance = declaredType.getDeclaredConstructor().newInstance();
                            mapper.readerForUpdating(newInstance).readValue(pval.traverse(mapper));
                            f.set(target, newInstance);
                        }
                    }
                } else {
                    // Scalars/arrays: replace
                    Object newVal = mapper.treeToValue(pval, declaredType);
                    f.set(target, newVal);
                }
            }
        }

        private static Field findField(Class<?> type, String name) {
            Class<?> t = type;
            while (t != null && t != Object.class) {
                try {
                    return t.getDeclaredField(name);
                } catch (NoSuchFieldException ignore) {}
                t = t.getSuperclass();
            }
            return null;
        }
    }

    // ----- Test verifies: no @class -> update in-place; with @class -> replace -----

    @Test
    void update_without_typeid_updates_in_place_and_with_typeid_replaces() throws Exception {
        // Configure Jackson default typing with whitelist
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("one.chartsy.")
                .build();

        ObjectMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();

        // Initial object graph
        String json =
                "{"
                        + "\"@class\":\"" + Panel.class.getName() + "\","
                        + "\"id\":\"root\","
                        + "\"style\":{\"color\":\"#333\",\"padding\":8},"
                        + "\"child\":{"
                        +   "\"@class\":\"" + Label.class.getName() + "\","
                        +   "\"id\":\"lbl1\",\"text\":\"Hello\""
                        + "}"
                        + "}";
        Widget root = mapper.readValue(json, Widget.class);
        Panel panel = (Panel) root;
        Label originalChild = (Label) panel.child;

        PolyAwareUpdater updater = new PolyAwareUpdater(mapper, "@class");

        // 1) Patch WITHOUT @class on 'child' -> update in place
        String patch1 =
                "{"
                        + "\"style\":{\"color\":\"#999\"},"
                        + "\"child\":{\"text\":\"Hi!\"}"
                        + "}";
        updater.applyPatch(panel, patch1);

        assertSame(originalChild, panel.child);          // in-place, same instance
        assertEquals("lbl1", ((Label) panel.child).id);  // preserved
        assertEquals("Hi!", ((Label) panel.child).text); // updated
        assertEquals("#999", panel.style.color);         // updated
        assertEquals(8, panel.style.padding);            // preserved

        // 2) Patch WITH @class on 'child' -> replace object
        String patch2 =
                "{"
                        + "\"child\":{"
                        +   "\"@class\":\"" + Label.class.getName() + "\","
                        +   "\"text\":\"Bye!\""
                        + "}"
                        + "}";
        updater.applyPatch(panel, patch2);

        assertNotSame(originalChild, panel.child);       // replaced
        assertNull(((Label) panel.child).id);            // not carried over
        assertEquals("Bye!", ((Label) panel.child).text);
    }
}
