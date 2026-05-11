/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@Table("ONE_CHART_TEMPLATES")
public class ChartTemplateAggregateData extends AbstractAggregateData {

    @Id
    private Long id;
    @Column("TEMPLATE_KEY")
    private UUID templateKey;
    private String name;
    @Column("NAME_KEY")
    private String nameKey;
    private Origin origin;
    @Column("DEFAULT_TEMPLATE")
    private boolean defaultTemplate;
    @Column("PAYLOAD_VERSION")
    private int payloadVersion;
    @Column("PAYLOAD_JSON")
    private String payloadJson;

    public enum Origin {
        SYSTEM,
        USER
    }
}
