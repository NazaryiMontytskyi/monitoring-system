package com.nmontytskyi.monitoring.collector;

import com.nmontytskyi.monitoring.model.MetricSnapshot;
import com.nmontytskyi.monitoring.model.ServiceInfo;

/**
 * Contract for a component that collects metrics from a microservice.
 *
 * <p>Implemented in {@code monitoring-server} by {@code ActuatorMetricsCollector},
 * which performs HTTP requests to the target service's {@code /actuator/health}
 * and {@code /actuator/metrics} endpoints and converts the response into a
 * {@link MetricSnapshot}.
 *
 * <p>Separating the interface from the implementation allows:
 * <ul>
 *   <li>easy substitution in tests;</li>
 *   <li>adding alternative collection mechanisms (e.g. JMX or gRPC)
 *       without modifying consumer code.</li>
 * </ul>
 */
public interface MetricsCollector {

    /**
     * Collects the current metrics from the specified service.
     *
     * <p>If the service is unavailable, the method does not throw an exception.
     * Instead it returns a {@link MetricSnapshot} with status
     * {@link com.nmontytskyi.monitoring.model.HealthStatus#DOWN}
     * and a populated {@code errorMessage}.
     *
     * @param service description of the service to collect metrics from
     * @return a snapshot of the current service state
     */
    MetricSnapshot collect(ServiceInfo service);
}
