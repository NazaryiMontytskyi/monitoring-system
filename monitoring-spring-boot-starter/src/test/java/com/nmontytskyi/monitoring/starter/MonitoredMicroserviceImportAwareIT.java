package com.nmontytskyi.monitoring.starter;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nmontytskyi.monitoring.annotation.Sla;
import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import com.nmontytskyi.monitoring.starter.config.MonitoringProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code @MonitoredMicroservice} annotation attributes are correctly
 * read by {@code MonitoringAutoConfiguration.setImportMetadata()} and applied as
 * defaults to {@link MonitoringProperties}, with YAML overrides taking precedence.
 */
@SpringBootTest(
        classes = MonitoredMicroserviceImportAwareIT.AnnotatedApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class MonitoredMicroserviceImportAwareIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Override serverUrl via YAML — must win over annotation value
        registry.add("monitoring.server-url", () -> "http://localhost:" + wm.getPort());
        // Stub registration endpoint
        wm.stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 7}")));
    }

    @MonitoredMicroservice(
            name = "import-aware-service",
            serverUrl = "http://annotation-server:8080",
            trackAllEndpoints = true,
            bufferFlushIntervalMs = 30_000,
            bufferMaxSize = 50,
            sla = @Sla(uptimePercent = 99.5, maxResponseTimeMs = 500, maxErrorRatePercent = 2.0)
    )
    @SpringBootApplication
    static class AnnotatedApp {
    }

    @Autowired
    private MonitoringProperties props;

    @Test
    void annotationAttributes_readCorrectly() {
        assertThat(props.getServiceName()).isEqualTo("import-aware-service");
        assertThat(props.isTrackAllEndpoints()).isTrue();
        assertThat(props.getBufferMaxSize()).isEqualTo(50);
        assertThat(props.getBufferFlushIntervalMs()).isEqualTo(30_000L);
    }

    @Test
    void slaAttributes_readCorrectly() {
        assertThat(props.getSlaUptimePercent()).isEqualTo(99.5);
        assertThat(props.getSlaResponseTimeMs()).isEqualTo(500L);
        assertThat(props.getSlaErrorRatePercent()).isEqualTo(2.0);
    }

    @Test
    void yamlOverride_takesPrecedenceOverAnnotation() {
        // YAML sets monitoring.server-url; the annotation has a different value
        // YAML must win
        assertThat(props.getServerUrl()).startsWith("http://localhost:");
        assertThat(props.getServerUrl()).doesNotContain("annotation-server");
    }

    @Test
    void trackAllEndpoints_fromAnnotation() {
        assertThat(props.isTrackAllEndpoints()).isTrue();
    }
}
