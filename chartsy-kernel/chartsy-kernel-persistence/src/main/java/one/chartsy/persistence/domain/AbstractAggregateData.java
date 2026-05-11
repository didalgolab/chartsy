/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class AbstractAggregateData {

    private LocalDateTime created;
    private LocalDateTime lastModified;

    @Transient
    private LocalDateTime removed;

    public void markNewlyCreated() {
        created = LocalDateTime.now();
    }

    public void markModified() {
        lastModified = LocalDateTime.now();
    }

    public void markRemoved() {
        if (removed == null)
            removed = LocalDateTime.now();
    }
}
