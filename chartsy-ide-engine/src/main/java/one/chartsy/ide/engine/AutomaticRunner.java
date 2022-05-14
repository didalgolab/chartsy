/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ide.engine;

import one.chartsy.Attribute;
import one.chartsy.kernel.runner.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class AutomaticRunner implements LaunchPerformer {

    private final Logger log = LogManager.getLogger(getClass());
    private final Lookup lookup;

    public AutomaticRunner(Lookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public void performLaunch(LaunchContext context, Class<?> target) throws Exception {
        var desc = findRunner(context, target);
        var runner = createRunner(desc);

        var newAttributes = new HashMap<String, Object>(context.getAttributes());
        for (Attribute<?> attrDef : runner.getRequiredConfigurations()) {
            Supplier<?> attrDefault;
            Object attrValue = lookup.lookup(attrDef.key().type());
            if (attrValue == null && (attrDefault = attrDef.valueDefault()) != null)
                attrValue = attrDefault.get();

            newAttributes.putIfAbsent(attrDef.key().name(), attrValue);
        }
        var newContext = ImmutableLaunchContext.builder().from(context)
                .attributes(newAttributes)
                .build();

        var viewType = desc.getTopComponent();
        if (viewType != null) {
            @SuppressWarnings("unchecked")
            var tcFactory = (Constructor<TopComponent>)Class.forName(viewType, true, Lookup.getDefault().lookup(ClassLoader.class))
                    .getConstructor(String.class);

            SwingUtilities.invokeAndWait(() -> {
                try {
                    var tc = tcFactory.newInstance(target.getSimpleName());
                    attachListeners(runner, tc.getLookup());
                    tc.open();
                    tc.requestActive();
                } catch (Exception e) {
                    Exceptions.printStackTrace(e);
                }
            });
        }

        runner.performLaunch(newContext, target);

    }

    private void attachListeners(LaunchPerformer runner, Lookup lookup) {
        for (Method m : runner.getClass().getMethods()) {
            if (m.getReturnType() == Void.TYPE
                    && m.getParameterCount() == 1
                    && m.getName().startsWith("add")) {

                var listenerType = m.getParameterTypes()[0];
                var listener = lookup.lookup(listenerType);
                if (listener != null) {
                    try {
                        m.invoke(runner, listener);
                    } catch (Exception e) {
                        log.warn("Cannot bind {} to {} due to {}",
                                listenerType.getSimpleName(), runner.getClass().getSimpleName(), e.toString(), e);
                    }
                }
            }
        }
    }

    protected Descriptor findRunner(LaunchContext context, Class<?> target) throws LaunchException {
        List<Descriptor> compatibleRunners = LaunchServices.getDefault().findCompatibleRunners(target);
        if (compatibleRunners.isEmpty())
            throw new LaunchException(target.getName() + ": compatible runner not found");

        // TODO: ask for user selection
        //otherwise
        return compatibleRunners.get(0);
    }

    protected LaunchPerformer createRunner(Descriptor desc) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return (LaunchPerformer) Class.forName(desc.getType(), true, Lookup.getDefault().lookup(ClassLoader.class))
                .getConstructor().newInstance();
    }
}
