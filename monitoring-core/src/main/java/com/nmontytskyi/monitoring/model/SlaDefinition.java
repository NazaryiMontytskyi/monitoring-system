package com.nmontytskyi.monitoring.model;

import com.nmontytskyi.monitoring.annotation.Sla;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the Service Level Agreement (SLA) parameters for a microservice.
 *
 * <p>Can be created from an {@link Sla} annotation using {@link #from(Sla)},
 * or manually via the {@code Builder}. If a service does not define its own SLA,
 * the default values from {@link #defaults()} are used.
 *
 * <p>Stored together with {@link ServiceInfo} and used by
 * {@code AlertEvaluationService} to evaluate metric compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaDefinition {

    /**
     * Minimum required uptime percentage. Example: {@code 99.9}.
     */
    private double uptimePercent;

    /**
     * Maximum allowed average response time in milliseconds.
     */
    private long maxResponseTimeMs;

    /**
     * Maximum allowed error rate as a percentage of total requests.
     */
    private double maxErrorRatePercent;

    /**
     * Human-readable description of the agreement shown in reports.
     */
    private String description;

    /**
     * Creates a {@code SlaDefinition} from the values of an {@link Sla} annotation.
     *
     * @param sla annotation defined on {@code @MonitoredMicroservice}
     * @return an SLA parameters object
     */
    public static SlaDefinition from(Sla sla) {
        return SlaDefinition.builder()
                .uptimePercent(sla.uptimePercent())
                .maxResponseTimeMs(sla.maxResponseTimeMs())
                .maxErrorRatePercent(sla.maxErrorRatePercent())
                .description(sla.description())
                .build();
    }

    /**
     * Returns an SLA with standard default values for services
     * that have not defined their own agreement.
     *
     * <ul>
     *   <li>Uptime: 99.9%</li>
     *   <li>Max response time: 1000ms</li>
     *   <li>Max error rate: 5.0%</li>
     * </ul>
     */
    public static SlaDefinition defaults() {
        return SlaDefinition.builder()
                .uptimePercent(99.9)
                .maxResponseTimeMs(1000)
                .maxErrorRatePercent(5.0)
                .description("Default SLA")
                .build();
    }
}
