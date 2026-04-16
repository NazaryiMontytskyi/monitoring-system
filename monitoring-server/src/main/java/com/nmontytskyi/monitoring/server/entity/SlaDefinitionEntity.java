package com.nmontytskyi.monitoring.server.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity holding the SLA (Service Level Agreement) parameters for a microservice.
 *
 * <p>Has a 1:1 relationship with {@link RegisteredServiceEntity}: every registered
 * service has exactly one SLA definition. If the client does not provide custom values,
 * the server creates a default SLA on registration.
 *
 * <p>Maps to the {@code sla_definitions} table where the primary key
 * ({@code service_id}) is also the foreign key to {@code registered_services}.
 */
@Entity
@Table(name = "sla_definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaDefinitionEntity {

    /**
     * Primary key that also acts as the foreign key to {@code registered_services}.
     * The value equals the {@link RegisteredServiceEntity#getId()} of the owning service.
     */
    @Id
    @Column(name = "service_id")
    private Long serviceId;

    /**
     * The owning service.
     * {@code @MapsId} ensures that {@code serviceId} is populated from the association.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "service_id")
    private RegisteredServiceEntity service;

    /**
     * Minimum required uptime percentage.
     * Example: {@code 99.9} means at most 0.1 % downtime is allowed.
     */
    @Column(name = "uptime_percent", nullable = false)
    @Builder.Default
    private double uptimePercent = 99.9;

    /**
     * Maximum allowed average response time in milliseconds.
     * Example: {@code 300} means requests must complete in under 300 ms on average.
     */
    @Column(name = "max_response_time_ms", nullable = false)
    @Builder.Default
    private long maxResponseTimeMs = 1000L;

    /**
     * Maximum allowed error rate as a percentage of total requests.
     * Example: {@code 1.0} means no more than 1 % of requests may fail.
     */
    @Column(name = "max_error_rate_percent", nullable = false)
    @Builder.Default
    private double maxErrorRatePercent = 5.0;

    /** Optional human-readable description shown in SLA reports. */
    @Column(length = 255)
    private String description;
}
