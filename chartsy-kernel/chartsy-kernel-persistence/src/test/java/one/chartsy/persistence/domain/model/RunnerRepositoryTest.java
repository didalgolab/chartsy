/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.SymbolGroupContent;
import one.chartsy.kernel.config.KernelConfiguration;
import one.chartsy.persistence.PersistenceAutoConfiguration;
import one.chartsy.persistence.domain.RunnerAggregateData;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerRepositoryTest {

    @TempDir
    static Path chartsyHome;

    @BeforeEach
    void resetWorkspace() throws IOException {
        System.setProperty("chartsy.home", chartsyHome.toString());

        var workspace = chartsyHome.resolve(".chartsy");
        if (Files.notExists(workspace))
            return;

        try (var stream = Files.walk(workspace)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Cannot delete " + path, e);
                        }
                    });
        }
    }

    @AfterAll
    static void clearChartsyHome() {
        System.clearProperty("chartsy.home");
    }

    @Test
    void generated_runner_repository_loads_seed_runner_from_h2() {
        try (var context = createContext()) {
            assertThat(context.getBean(RunnerRepository.class).findAll())
                    .extracting(RunnerAggregateData::getKey)
                    .contains("EXPLORATION");
        }
    }

    @Test
    void generated_symbol_group_repository_supports_parent_lookup() {
        try (var context = createContext()) {
            var repository = context.getBean(SymbolGroupRepository.class);

            var parent = new SymbolGroupAggregateData();
            parent.setName("Parent");
            parent.setContentType(SymbolGroupContent.Type.FOLDER);
            parent = repository.save(parent);

            var child = new SymbolGroupAggregateData();
            child.setParentGroupId(parent.getId());
            child.setName("Child");
            child.setContentType(SymbolGroupContent.Type.FOLDER);
            child = repository.save(child);

            assertThat(repository.findByParentGroupId(parent.getId()))
                    .extracting(SymbolGroupAggregateData::getId)
                    .contains(child.getId());
        }
    }

    private AnnotationConfigApplicationContext createContext() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(KernelConfiguration.class, KernelConfiguration::new);
        context.register(PersistenceAutoConfiguration.class);
        context.refresh();
        return context;
    }
}
