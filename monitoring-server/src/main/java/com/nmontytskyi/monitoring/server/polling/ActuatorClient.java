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
            log.debug("Metric {} not available from {}: {}", metricName, actuatorUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a metric filtered by a URL tag dimension (e.g. {@code area:nonheap}).
     * Use this only for metrics where the tag is a real Micrometer tag key, not for
     * timer statistics such as TOTAL_TIME / COUNT / MAX — use
     * {@link #fetchTimerStatistic} for those.
     */
    public Optional<Double> fetchMetricValue(String actuatorUrl, String metricName, String tag) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            ActuatorMetricsResponse response = restClient.get()
                    .uri(actuatorUrl + "/metrics/" + metricName + "?tag=" + tag)
                    .retrieve()
                    .body(ActuatorMetricsResponse.class);
            if (response == null || response.getMeasurements() == null || response.getMeasurements().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.getValue());
        } catch (Exception e) {
            log.debug("Metric {} [tag={}] not available from {}: {}", metricName, tag, actuatorUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a Timer metric and returns the value of a specific {@code statistic}
     * (COUNT, TOTAL_TIME, MAX). The statistic is a field inside the {@code measurements}
     * array of the Actuator response — it is NOT a URL tag filter.
     *
     * <p>Returns {@link Optional#empty()} when the metric does not exist yet
     * (e.g. no GC pauses have occurred) or when the requested statistic is absent.
     */
    public Optional<Double> fetchTimerStatistic(String actuatorUrl, String metricName, String statistic) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            ActuatorMetricsResponse response = restClient.get()
                    .uri(actuatorUrl + "/metrics/" + metricName)
                    .retrieve()
                    .body(ActuatorMetricsResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return response.getStatisticValue(statistic);
        } catch (Exception e) {
            // 404 is normal when no GC pauses have occurred yet — log at DEBUG, not WARN
            log.debug("Timer metric {} [statistic={}] not available from {}: {}",
                    metricName, statistic, actuatorUrl, e.getMessage());
            return Optional.empty();
        }
    }
}
