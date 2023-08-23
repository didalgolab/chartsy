/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractAggregateData {
    private LocalDateTime created;
    private LocalDateTime lastModified;

    @Transient
    private LocalDateTime removed;

    @PrePersist
    public void markNewlyCreated() {
        created = LocalDateTime.now();
    }

    @PreUpdate
    public void markModified() {
        lastModified = LocalDateTime.now();
    }

    @PreRemove
    public void markRemoved() {
        if (removed == null)
            removed = LocalDateTime.now();
    }
}
