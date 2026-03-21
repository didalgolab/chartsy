/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.kernel.runner.LaunchPerformer;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Table("ONE_RUNNERS")
public class RunnerAggregateData extends AbstractAggregateData implements LaunchPerformer.Descriptor {

    @Id
    private Long id;
    @Column("RUNNER_KEY")
    private String key;
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
