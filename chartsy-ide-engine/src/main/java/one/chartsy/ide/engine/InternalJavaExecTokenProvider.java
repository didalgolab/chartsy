/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ide.engine;

import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.spi.actions.BeforeBuildActionHook;
import org.netbeans.modules.gradle.spi.actions.ReplaceTokenProvider;
import org.netbeans.spi.project.LookupProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InternalJavaExecTokenProvider implements ReplaceTokenProvider {
    public static final String JAVA_EXEC_ENV = "javaExec.environment";

    @Override
    public Set<String> getSupportedTokens() {
        return Collections.singleton(JAVA_EXEC_ENV);
    }

    @Override
    public Map<String, String> createReplacements(String action, Lookup context) {
        var replacements = new HashMap<String, String>();
        replacements.put(JAVA_EXEC_ENV, "-PrunEnvironment=CHARTSY_LINK=");
        return replacements;
    }

    @ProjectServiceProvider(
            service = BeforeBuildActionHook.class,
            projectTypes = @LookupProvider.Registration.ProjectType(id = NbGradleProject.GRADLE_PROJECT_TYPE)
    )
    public static class AttachHook implements BeforeBuildActionHook {
        private final Lookup implLookup = Lookups.singleton(new InternalJavaExecTokenProvider());

        @Override
        public Lookup beforeAction(String action, Lookup context, PrintWriter out) {
            return new ProxyLookup(context, implLookup);
        }
    }
}
