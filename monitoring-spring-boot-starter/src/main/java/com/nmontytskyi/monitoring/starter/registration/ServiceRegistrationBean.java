package com.nmontytskyi.monitoring.starter.registration;

import com.nmontytskyi.monitoring.starter.client.MonitoringServerClient;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ServiceRegistrationBean {

    private final MonitoringServerClient client;
    private final MonitoringProperties props;

    private Long serviceId;

    @PostConstruct
    public void register() {
        try {
            ServiceRegistrationRequest request = ServiceRegistrationRequest.builder()
                    .name(props.getServiceName())
                    .host(props.getServiceHost())
                    .port(props.getServicePort())
                    .actuatorUrl(resolveActuatorUrl())
                    .baseUrl("http://" + props.getServiceHost() + ":" + props.getServicePort())
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

    private String resolveActuatorUrl() {
        String configured = props.getActuatorUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "http://" + props.getServiceHost() + ":" + props.getServicePort() + "/actuator";
    }

    public Long getServiceId() {
        return serviceId;
    }
}
