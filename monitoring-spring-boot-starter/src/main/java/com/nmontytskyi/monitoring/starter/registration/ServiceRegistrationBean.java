package com.nmontytskyi.monitoring.starter.registration;

import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * Spring lifecycle bean that registers and de-registers the host microservice with the
 * central monitoring server.
 *
 * <p>On {@link org.springframework.context.SmartLifecycle#start()} the bean sends a
 * {@code POST /api/services} request to the monitoring server containing the service name,
 * host, port, base URL, actuator URL, and initial SLA thresholds read from
 * {@link com.nmontytskyi.monitoring.starter.config.MonitoringProperties}.
 * The server-assigned service ID is stored for subsequent metric push requests.
 *
 * <p>On {@link org.springframework.context.SmartLifecycle#stop()} a
 * {@code DELETE /api/services/{id}} request is sent so the server can mark the service
 * as inactive rather than waiting for a polling timeout.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.starter.client.MonitoringServerClient
 * @see com.nmontytskyi.monitoring.starter.config.MonitoringProperties
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceRegistrationBean {

    private final MonitoringServerClient client;
    private final MonitoringProperties props;
    private final Environment environment;

    private Long serviceId;

    @PostConstruct
    public void register() {
        try {
            ServiceRegistrationRequest request = ServiceRegistrationRequest.builder()
                    .name(resolveServiceName())
                    .host(props.getServiceHost())
                    .port(props.getServicePort())
                    .actuatorUrl(resolveActuatorUrl())
                    .baseUrl(resolveBaseUrl())
                    .build();

            serviceId = client.registerService(request);

            if (serviceId != null) {
                log.info("Registered as service id={}", serviceId);
            } else {
                log.warn("Service registration failed — metrics will not be pushed to monitoring-server");
            }
        } catch (Exception e) {
            log.warn("Unexpected error during service registration: {}", e.getMessage());
        }
    }

    private String resolveServiceName() {
        String name = props.getServiceName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return environment.getProperty("spring.application.name", "unknown-service");
    }

    private String resolveActuatorUrl() {
        String configured = props.getActuatorUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "http://" + props.getServiceHost() + ":" + props.getServicePort() + "/actuator";
    }

    private String resolveBaseUrl() {
        String configured = props.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String actuatorUrl = resolveActuatorUrl();
        if (actuatorUrl.endsWith("/actuator")) {
            return actuatorUrl.substring(0, actuatorUrl.length() - "/actuator".length());
        }
        return "http://" + props.getServiceHost() + ":" + props.getServicePort();
    }

    public Long getServiceId() {
        return serviceId;
    }
}
