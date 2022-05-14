/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.kernel.runner.LaunchPerformer;

import javax.persistence.*;

import java.util.Arrays;
import java.util.List;

import static javax.persistence.GenerationType.SEQUENCE;

@Getter
@Setter
@Entity
@Table(name = "ONE_RUNNERS")
public class RunnerAggregateData extends AbstractAggregateData implements LaunchPerformer.Descriptor {
    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = "ONE_RUNNER_IDS")
    @SequenceGenerator(name = "ONE_RUNNER_IDS", sequenceName = "ONE_RUNNER_IDS")
    private Long id;
    @Column(name = "\"key\"")
    private String key;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String name;
    private String type;
    private String supportedTypes;
    private String topComponent;

    public enum Status { ACTIVE, INACTIVE }

    public List<String> getSupportedTypesList() {
        List<String> resultList = Arrays.asList(supportedTypes.split(","));
        resultList.replaceAll(String::strip);
        return resultList;
    }

    public boolean isSupported(Class<?> type, ClassLoader classLoader) {
        for (String supportedType : getSupportedTypesList()) {
            try {
                if (Class.forName(supportedType, false, classLoader).isAssignableFrom(type))
                    return true;
            } catch (ClassNotFoundException | NoClassDefFoundError ignore) {
            }
        }
        return false;
    }
}
