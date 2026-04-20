package com.nmontytskyi.monitoring.server.polling;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.PollingProperties;
import com.nmontytskyi.monitoring.server.polling.dto.ActuatorHealthResponse;
import com.nmontytskyi.monitoring.server.polling.dto.ActuatorMetricsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class ActuatorClient {

    private final RestClient restClient;

    public ActuatorClient(PollingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public Optional<HealthStatus> fetchHealth(String actuatorUrl) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            ActuatorHealthResponse response = restClient.get()
                    .uri(actuatorUrl + "/health")
                    .retrieve()
                    .body(ActuatorHealthResponse.class);
            if (response == null) {
                return Optional.of(HealthStatus.DOWN);
            }
            return Optional.of(response.toHealthStatus());
        } catch (Exception e) {
            log.warn("Failed to fetch health from {}: {}", actuatorUrl, e.getMessage());
            return Optional.of(HealthStatus.DOWN);
        }
    }

    public Optional<Double> fetchMetricValue(String actuatorUrl, String metricName) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            ActuatorMetricsResponse response = restClient.get()
                    .uri(actuatorUrl + "/metrics/" + metricName)
                    .retrieve()
                    .body(ActuatorMetricsResponse.class);
            if (response == null || response.getMeasurements() == null || response.getMeasurements().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.getValue());
        } catch (Exception e) {
            log.warn("Failed to fetch metric {} from {}: {}", metricName, actuatorUrl, e.getMessage());
            return Optional.empty();
        }
    }
}
