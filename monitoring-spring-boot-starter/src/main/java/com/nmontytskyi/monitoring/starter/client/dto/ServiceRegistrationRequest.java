package com.nmontytskyi.monitoring.starter.client.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO carrying service metadata sent to the monitoring server during auto-registration.
 *
 * <p>Includes the logical service name, network coordinates (host, port, base URL,
 * actuator URL), and initial SLA thresholds derived from
 * {@link com.nmontytskyi.monitoring.starter.config.MonitoringProperties}.
 */
@Data
@Builder
public class ServiceRegistrationRequest {

    private String name;
    private String host;
    private int port;
    private String actuatorUrl;
    private String baseUrl;
}
