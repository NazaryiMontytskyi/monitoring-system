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
}
