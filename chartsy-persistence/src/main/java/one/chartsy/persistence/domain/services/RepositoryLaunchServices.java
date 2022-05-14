/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.services;

import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.runner.LaunchPerformer;
import one.chartsy.kernel.runner.LaunchServices;
import one.chartsy.persistence.domain.model.RunnerRepository;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

@ServiceProvider(service = LaunchServices.class)
public class RepositoryLaunchServices implements LaunchServices {

    private RunnerRepository repository;

    @Override
    public List<LaunchPerformer.Descriptor> findCompatibleRunners(Class<?> target) {
        if (repository == null)
            repository = Kernel.getDefault().getApplicationContext().getBean(RunnerRepository.class);

        var compatibleRunners = new ArrayList<LaunchPerformer.Descriptor>();
        var classLoader = Lookup.getDefault().lookup(ClassLoader.class);
        for (var runner : repository.findAll())
            if (runner.isSupported(target, classLoader))
                compatibleRunners.add(runner);

        return compatibleRunners;
    }

    @Override
    public LaunchPerformer createRunner(Class<?> target) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (repository == null)
            repository = Kernel.getDefault().getApplicationContext().getBean(RunnerRepository.class);

        var classLoader = Lookup.getDefault().lookup(ClassLoader.class);
        for (var runner : repository.findAll())
            if (runner.isSupported(target, classLoader))
                return (LaunchPerformer) Class.forName(runner.getType(), true, classLoader).getConstructor().newInstance();

        throw new RunnerNotFoundException(target.getName());
    }

    public static class RunnerNotFoundException extends RuntimeException {
        public RunnerNotFoundException(String message) {
            super(message);
        }
    }
}
