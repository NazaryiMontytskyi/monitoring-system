package com.nmontytskyi.monitoring.server.polling;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.PollingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER;
import static org.assertj.core.api.Assertions.assertThat;

class ActuatorClientTest {

    @RegisterExtension
    WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ActuatorClient actuatorClient;

    @BeforeEach
    void setUp() {
        PollingProperties props = new PollingProperties();
        props.setConnectTimeoutSeconds(2);
        props.setTimeoutSeconds(2);
        actuatorClient = new ActuatorClient(props);
    }

    @Test
    void fetchHealth_serviceUp_returnsUp() {
        wireMock.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(okJson("{\"status\":\"UP\"}")));

        Optional<HealthStatus> result = actuatorClient.fetchHealth(wireMock.baseUrl() + "/actuator");

        assertThat(result).contains(HealthStatus.UP);
    }

    @Test
    void fetchHealth_serviceDown_returnsDown() {
        wireMock.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(okJson("{\"status\":\"DOWN\"}")));

        Optional<HealthStatus> result = actuatorClient.fetchHealth(wireMock.baseUrl() + "/actuator");

        assertThat(result).contains(HealthStatus.DOWN);
    }

    @Test
    void fetchHealth_serviceUnavailable_returnsDown() {
        wireMock.stubFor(get(urlEqualTo("/actuator/health"))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));

        Optional<HealthStatus> result = actuatorClient.fetchHealth(wireMock.baseUrl() + "/actuator");

        assertThat(result).contains(HealthStatus.DOWN);
    }

    @Test
    void fetchHealth_nullActuatorUrl_returnsEmpty() {
        Optional<HealthStatus> result = actuatorClient.fetchHealth(null);

        assertThat(result).isEmpty();
    }

    @Test
    void fetchHealth_blankActuatorUrl_returnsEmpty() {
        Optional<HealthStatus> result = actuatorClient.fetchHealth("   ");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchMetricValue_success_returnsValue() {
        wireMock.stubFor(get(urlEqualTo("/actuator/metrics/jvm.memory.used"))
                .willReturn(okJson("""
                        {
                          "name": "jvm.memory.used",
                          "measurements": [{"statistic": "VALUE", "value": 52428800}]
                        }
                        """)));

        Optional<Double> result = actuatorClient.fetchMetricValue(
                wireMock.baseUrl() + "/actuator", "jvm.memory.used");

        assertThat(result).contains(52428800.0);
    }

    @Test
    void fetchMetricValue_metricNotFound_returnsEmpty() {
        wireMock.stubFor(get(urlEqualTo("/actuator/metrics/nonexistent.metric"))
                .willReturn(aResponse().withStatus(404)));

        Optional<Double> result = actuatorClient.fetchMetricValue(
                wireMock.baseUrl() + "/actuator", "nonexistent.metric");

        assertThat(result).isEmpty();
    }
}
