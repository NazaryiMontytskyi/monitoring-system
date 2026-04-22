package com.nmontytskyi.monitoring.starter.client;

import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;

/**
 * HTTP client for communicating with the central monitoring server.
 *
 * <p>All calls are wrapped in try/catch so that monitoring failures
 * never propagate to the client service's business logic.
 */
@Slf4j
public class MonitoringServerClient {

    private final RestClient restClient;

    public MonitoringServerClient(String serverUrl) {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(serverUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(jacksonConverter);
                })
                .build();
    }

    /** Registers this service and returns the assigned service ID, or {@code null} on failure. */
    public Long registerService(ServiceRegistrationRequest request) {
        try {
            ServiceRegistrationResponse response = restClient.post()
                    .uri("/api/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ServiceRegistrationResponse.class);
            return response != null ? response.getId() : null;
        } catch (Exception e) {
            log.warn("Failed to register service with monitoring-server: {}", e.getMessage());
            return null;
        }
    }

    /** Pushes a single metric snapshot (used by {@code @MonitoredEndpoint} path). */
    public void pushMetric(MetricPushRequest request) {
        try {
            restClient.post()
                    .uri("/api/metrics/endpoint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to push metric to monitoring-server: {}", e.getMessage());
        }
    }

    /**
     * Pushes a batch of metric snapshots in a single HTTP call.
     * Throws on HTTP error so the caller ({@link com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer})
     * can apply its re-queue fail-safe strategy.
     */
    public void pushMetricBatch(List<MetricPushRequest> batch) {
        restClient.post()
                .uri("/api/metrics/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(batch)
                .retrieve()
                .toBodilessEntity();
    }
}
