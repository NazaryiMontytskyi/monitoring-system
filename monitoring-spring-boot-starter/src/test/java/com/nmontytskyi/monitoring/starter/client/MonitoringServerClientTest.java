package com.nmontytskyi.monitoring.starter.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.starter.client.dto.MetricPushRequest;
import com.nmontytskyi.monitoring.starter.client.dto.ServiceRegistrationRequest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@WireMockTest
class MonitoringServerClientTest {

    @Test
    void registerService_success(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": 42, "name": "test-service", "status": "UNKNOWN"}
                                """)));

        MonitoringServerClient client = new MonitoringServerClient("http://localhost:" + wm.getHttpPort());
        Long serviceId = client.registerService(buildRegistrationRequest());

        assertThat(serviceId).isEqualTo(42L);
        verify(postRequestedFor(urlEqualTo("/api/services"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("test-service"))));
    }

    @Test
    void registerService_serverUnavailable(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        MonitoringServerClient client = new MonitoringServerClient("http://localhost:" + wm.getHttpPort());
        Long serviceId = client.registerService(buildRegistrationRequest());

        assertThat(serviceId).isNull();
    }

    @Test
    void registerService_conflict409(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withBody("{\"message\": \"already registered\"}")));

        MonitoringServerClient client = new MonitoringServerClient("http://localhost:" + wm.getHttpPort());
        Long serviceId = client.registerService(buildRegistrationRequest());

        assertThat(serviceId).isNull();
    }

    @Test
    void pushMetric_success(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withStatus(202)));

        MonitoringServerClient client = new MonitoringServerClient("http://localhost:" + wm.getHttpPort());

        assertThatNoException().isThrownBy(() -> client.pushMetric(buildMetricPushRequest()));
        verify(postRequestedFor(urlEqualTo("/api/metrics/endpoint"))
                .withRequestBody(matchingJsonPath("$.serviceId"))
                .withRequestBody(matchingJsonPath("$.endpoint")));
    }

    @Test
    void pushMetric_serverUnavailable(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        MonitoringServerClient client = new MonitoringServerClient("http://localhost:" + wm.getHttpPort());

        assertThatNoException().isThrownBy(() -> client.pushMetric(buildMetricPushRequest()));
    }

    private ServiceRegistrationRequest buildRegistrationRequest() {
        return ServiceRegistrationRequest.builder()
                .name("test-service")
                .host("localhost")
                .port(8081)
                .actuatorUrl("http://localhost:8081/actuator")
                .build();
    }

    private MetricPushRequest buildMetricPushRequest() {
        return MetricPushRequest.builder()
                .serviceId(1L)
                .endpoint("TestController.getAll")
                .responseTimeMs(50L)
                .status(HealthStatus.UP)
                .build();
    }
}
