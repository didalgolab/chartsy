/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.persistence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ONE_CHART_TEMPLATES")
public class ChartTemplateAggregateData extends AbstractAggregateData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ONE_CHART_TEMPLATE_IDS")
    @SequenceGenerator(name = "ONE_CHART_TEMPLATE_IDS", sequenceName = "ONE_CHART_TEMPLATE_IDS")
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true)
    private UUID templateKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    @Column(name = "default_template", nullable = false)
    private boolean defaultTemplate;

    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    public enum Origin {
        SYSTEM,
        USER
    }
}
