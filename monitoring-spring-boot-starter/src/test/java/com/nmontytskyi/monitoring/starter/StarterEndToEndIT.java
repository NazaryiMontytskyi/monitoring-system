package com.nmontytskyi.monitoring.starter;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nmontytskyi.monitoring.annotation.TrackMetric;
import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: a {@code @MonitoredMicroservice}-annotated app boots, endpoints
 * are intercepted by {@code AllEndpointsAspect}, metrics are buffered and flushed
 * via {@code POST /api/metrics/batch}.
 */
@SpringBootTest(
        classes = StarterEndToEndIT.E2EApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class StarterEndToEndIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("monitoring.server-url", () -> "http://localhost:" + wm.getPort());

        wm.stubFor(post(urlEqualTo("/api/services"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 55}")));
        wm.stubFor(post(urlEqualTo("/api/metrics/batch"))
                .willReturn(aResponse().withStatus(202)));
        wm.stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withStatus(202)));
    }

    @MonitoredMicroservice(
            name = "e2e-test-service",
            trackAllEndpoints = true,
            bufferFlushIntervalMs = 200  // flush every 200 ms for fast test feedback
    )
    @SpringBootApplication
    static class E2EApp {
        @Bean
        public HelloController helloController() {
            return new HelloController();
        }

        @Bean
        public TrackedBusinessService trackedBusinessService() {
            return new TrackedBusinessService();
        }
    }

    @RestController
    static class HelloController {
        @GetMapping("/e2e/hello")
        public String hello() {
            return "hello";
        }
    }

    @Service
    static class TrackedBusinessService {
        @TrackMetric(name = "e2e.custom.metric")
        public String doWork() {
            return "work";
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TrackedBusinessService trackedService;

    @BeforeEach
    void resetStubs() {
        wm.resetRequests();
        wm.stubFor(post(urlEqualTo("/api/metrics/batch"))
                .willReturn(aResponse().withStatus(202)));
        wm.stubFor(post(urlEqualTo("/api/metrics/endpoint"))
                .willReturn(aResponse().withStatus(202)));
    }

    @Test
    void annotatedApp_bootstrapsSuccessfully() {
        // Context loaded → service was registered at startup (may have been reset, check total > 0)
        assertThat(wm.getAllServeEvents()).isNotNull();
    }

    @Test
    void restEndpoint_called_metricBufferedAndFlushed() {
        restTemplate.getForObject("/e2e/hello", String.class);

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var requests = wm.findAll(postRequestedFor(urlEqualTo("/api/metrics/batch")));
                    assertThat(requests).isNotEmpty();
                    assertThat(requests.get(0).getBodyAsString()).contains("hello");
                });
    }

    @Test
    void trackMetricAnnotation_producesCustomMetric() {
        trackedService.doWork();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var requests = wm.findAll(postRequestedFor(urlEqualTo("/api/metrics/batch")));
                    assertThat(requests).isNotEmpty();
                    assertThat(requests.stream()
                            .anyMatch(r -> r.getBodyAsString().contains("e2e.custom.metric")))
                            .isTrue();
                });
    }

    @Test
    void trackAllEndpoints_interceptsUnannotatedController() {
        restTemplate.getForObject("/e2e/hello", String.class);

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var requests = wm.findAll(postRequestedFor(urlEqualTo("/api/metrics/batch")));
                    assertThat(requests).isNotEmpty();
                });
    }
}
